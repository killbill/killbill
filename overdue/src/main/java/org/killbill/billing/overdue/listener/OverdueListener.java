/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.overdue.listener;

import java.util.List;
import java.util.UUID;

import javax.inject.Named;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.ControlTagCreationInternalEvent;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.events.InvoiceAdjustmentInternalEvent;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.InvoicePaymentErrorInternalEvent;
import org.killbill.billing.events.InvoicePaymentInfoInternalEvent;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.overdue.config.DefaultOverdueState;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotificationKey;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotificationKey.OverdueAsyncBusNotificationAction;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotifier;
import org.killbill.billing.overdue.notification.OverduePoster;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.bus.api.BusEvent;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class OverdueListener {

    private static final Logger log = LoggerFactory.getLogger(OverdueListener.class);

    private final InternalCallContextFactory internalCallContextFactory;
    private final CacheController<String, UUID> objectIdCacheController;
    private final Clock clock;
    private final OverduePoster asyncPoster;
    private final OverdueConfigCache overdueConfigCache;
    private final NonEntityDao nonEntityDao;
    private final AccountInternalApi accountApi;

    @Inject
    public OverdueListener(final NonEntityDao nonEntityDao,
                           final CacheControllerDispatcher cacheControllerDispatcher,
                           final Clock clock,
                           @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_ASYNC_BUS_NAMED)  final OverduePoster asyncPoster,
                           final OverdueConfigCache overdueConfigCache,
                           final InternalCallContextFactory internalCallContextFactory,
                           final AccountInternalApi accountApi) {
        this.nonEntityDao = nonEntityDao;
        this.clock = clock;
        this.asyncPoster = asyncPoster;
        this.overdueConfigCache = overdueConfigCache;
        this.objectIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID);
        this.internalCallContextFactory = internalCallContextFactory;
        this.accountApi = accountApi;
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleTagInsert(final ControlTagCreationInternalEvent event) {
        if (event.getTagDefinition().getName().equals(ControlTagType.OVERDUE_ENFORCEMENT_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            final InternalCallContext internalCallContext = createCallContext(event.getUserToken(), event.getSearchKey1(), event.getSearchKey2());
            insertBusEventIntoNotificationQueue(event.getObjectId(), OverdueAsyncBusNotificationAction.CLEAR, internalCallContext);
        } else if (event.getTagDefinition().getName().equals(ControlTagType.WRITTEN_OFF.toString()) && event.getObjectType() == ObjectType.INVOICE) {
            final UUID accountId = nonEntityDao.retrieveIdFromObject(event.getSearchKey1(), ObjectType.ACCOUNT, objectIdCacheController);
            insertBusEventIntoNotificationQueue(accountId, event);
        }
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleTagRemoval(final ControlTagDeletionInternalEvent event) {
        if (event.getTagDefinition().getName().equals(ControlTagType.OVERDUE_ENFORCEMENT_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            insertBusEventIntoNotificationQueue(event.getObjectId(), event);
        } else if (event.getTagDefinition().getName().equals(ControlTagType.WRITTEN_OFF.toString()) && event.getObjectType() == ObjectType.INVOICE) {
            final UUID accountId = nonEntityDao.retrieveIdFromObject(event.getSearchKey1(), ObjectType.ACCOUNT, objectIdCacheController);
            insertBusEventIntoNotificationQueue(accountId, event);
        }
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handlePaymentInfoEvent(final InvoicePaymentInfoInternalEvent event) {
        log.debug("Received InvoicePaymentInfo event {}", event);
        insertBusEventIntoNotificationQueue(event.getAccountId(), event);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handlePaymentErrorEvent(final InvoicePaymentErrorInternalEvent event) {
        log.debug("Received InvoicePaymentError event {}", event);
        insertBusEventIntoNotificationQueue(event.getAccountId(), event);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleInvoiceAdjustmentEvent(final InvoiceAdjustmentInternalEvent event) {
        log.debug("Received InvoiceAdjustment event {}", event);
        insertBusEventIntoNotificationQueue(event.getAccountId(), event);
    }

    @AllowConcurrentEvents
    @Subscribe
    public void handleInvoiceCreation(final InvoiceCreationInternalEvent event) {
        log.debug("Received InvoiceCreation event {}", event);
        insertBusEventIntoNotificationQueue(event.getAccountId(), event);
    }

    private void insertBusEventIntoNotificationQueue(final UUID accountId, final BusEvent event) {
        final InternalCallContext internalCallContext = createCallContext(event.getUserToken(), event.getSearchKey1(), event.getSearchKey2());
        insertBusEventIntoNotificationQueue(accountId, OverdueAsyncBusNotificationAction.REFRESH, internalCallContext);
    }

    private InternalCallContext createCallContext(final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        return internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "OverdueService", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
    }

    private void insertBusEventIntoNotificationQueue(final UUID accountId, final OverdueAsyncBusNotificationAction action, final InternalCallContext callContext) {
        final boolean shouldInsertNotification = shouldInsertNotification(callContext);

        if (!shouldInsertNotification) {
            log.debug("OverdueListener: shouldInsertNotification=false");
            return;
        }

        OverdueAsyncBusNotificationKey notificationKey = new OverdueAsyncBusNotificationKey(accountId, action);
        asyncPoster.insertOverdueNotification(accountId, callContext.getCreatedDate(), OverdueAsyncBusNotifier.OVERDUE_ASYNC_BUS_NOTIFIER_QUEUE, notificationKey, callContext);

        try {
            // Refresh parent
            final Account account = accountApi.getAccountById(accountId, callContext);
            if (account.getParentAccountId() != null && account.isPaymentDelegatedToParent()) {
                final InternalTenantContext parentAccountInternalTenantContext = internalCallContextFactory.createInternalTenantContext(account.getParentAccountId(), callContext);
                final InternalCallContext parentAccountContext = internalCallContextFactory.createInternalCallContext(parentAccountInternalTenantContext.getAccountRecordId(), callContext);
                notificationKey = new OverdueAsyncBusNotificationKey(account.getParentAccountId(), action);
                asyncPoster.insertOverdueNotification(account.getParentAccountId(), callContext.getCreatedDate(), OverdueAsyncBusNotifier.OVERDUE_ASYNC_BUS_NOTIFIER_QUEUE, notificationKey, parentAccountContext);
            }

            // Refresh children
            final List<Account> childrenAccounts = accountApi.getChildrenAccounts(accountId, callContext);
            if (childrenAccounts != null) {
                for (final Account childAccount : childrenAccounts) {
                    if (childAccount.isPaymentDelegatedToParent()) {
                        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(childAccount.getId(), callContext);
                        final InternalCallContext accountContext = internalCallContextFactory.createInternalCallContext(internalTenantContext.getAccountRecordId(), callContext);
                        notificationKey = new OverdueAsyncBusNotificationKey(childAccount.getId(), action);
                        asyncPoster.insertOverdueNotification(childAccount.getId(), callContext.getCreatedDate(), OverdueAsyncBusNotifier.OVERDUE_ASYNC_BUS_NOTIFIER_QUEUE, notificationKey, accountContext);
                    }
                }
            }
        } catch (final Exception e) {
            log.error("Error loading child accounts from accountId='{}'", accountId);
        }
    }

    // Optimization: don't bother running the Overdue machinery if it's disabled
    private boolean shouldInsertNotification(final InternalTenantContext internalTenantContext) {
        OverdueConfig overdueConfig;
        try {
            overdueConfig = overdueConfigCache.getOverdueConfig(internalTenantContext);
        } catch (final OverdueApiException e) {
            log.warn("Failed to extract overdue config for tenantRecordId='{}'", internalTenantContext.getTenantRecordId());
            overdueConfig = null;
        }
        if (overdueConfig == null || overdueConfig.getOverdueStatesAccount() == null || overdueConfig.getOverdueStatesAccount().getStates() == null) {
            return false;
        }

        for (final DefaultOverdueState state : ((DefaultOverdueConfig) overdueConfig).getOverdueStatesAccount().getStates()) {
            if (state.getConditionEvaluation() != null) {
                return true;
            }
        }
        return false;
    }
}
