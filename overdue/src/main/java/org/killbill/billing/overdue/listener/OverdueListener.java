/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.ControlTagCreationInternalEvent;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.events.InvoiceAdjustmentInternalEvent;
import org.killbill.billing.events.InvoicePaymentErrorInternalEvent;
import org.killbill.billing.events.InvoicePaymentInfoInternalEvent;
import org.killbill.billing.overdue.OverdueInternalApi;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.bus.api.BusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class OverdueListener {

    private static final Logger log = LoggerFactory.getLogger(OverdueListener.class);

    private final OverdueInternalApi overdueInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;
    private final CacheControllerDispatcher cacheControllerDispatcher;

    private final NonEntityDao nonEntityDao;

    @Inject
    public OverdueListener(final OverdueInternalApi overdueInternalApi,
                           final NonEntityDao nonEntityDao,
                           final CacheControllerDispatcher cacheControllerDispatcher,
                           final InternalCallContextFactory internalCallContextFactory) {
        this.overdueInternalApi = overdueInternalApi;
        this.nonEntityDao = nonEntityDao;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
        this.internalCallContextFactory = internalCallContextFactory;
    }




    @AllowConcurrentEvents
    @Subscribe
    public void handleTagInsert(final ControlTagCreationInternalEvent event) {
        if (event.getTagDefinition().getName().equals(ControlTagType.OVERDUE_ENFORCEMENT_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            final InternalCallContext callContext = createCallContext(event.getUserToken(), event.getSearchKey1(), event.getSearchKey2());
            overdueInternalApi.scheduleOverdueClear(event.getObjectId(), callContext);
        } else if (event.getTagDefinition().getName().equals(ControlTagType.WRITTEN_OFF.toString()) && event.getObjectType() == ObjectType.INVOICE) {
            final UUID accountId = nonEntityDao.retrieveIdFromObject(event.getSearchKey1(), ObjectType.ACCOUNT, cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID));
            insertBusEventIntoNotificationQueue(accountId, event);
        }
    }


    @AllowConcurrentEvents
    @Subscribe
    public void handleTagRemoval(final ControlTagDeletionInternalEvent event) {
        if (event.getTagDefinition().getName().equals(ControlTagType.OVERDUE_ENFORCEMENT_OFF.toString()) && event.getObjectType() == ObjectType.ACCOUNT) {
            insertBusEventIntoNotificationQueue(event.getObjectId(), event);
        } else if (event.getTagDefinition().getName().equals(ControlTagType.WRITTEN_OFF.toString()) && event.getObjectType() == ObjectType.INVOICE) {
            final UUID accountId = nonEntityDao.retrieveIdFromObject(event.getSearchKey1(), ObjectType.ACCOUNT, cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID));
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

    private void insertBusEventIntoNotificationQueue(final UUID accountId, final BusEvent event) {
        final InternalCallContext callContext = createCallContext(event.getUserToken(), event.getSearchKey1(), event.getSearchKey2());
        overdueInternalApi.scheduleOverdueRefresh(accountId, callContext);
    }

    private InternalCallContext createCallContext(final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        return internalCallContextFactory.createInternalCallContext(tenantRecordId, accountRecordId, "OverdueService", CallOrigin.INTERNAL, UserType.SYSTEM, userToken);
    }
}
