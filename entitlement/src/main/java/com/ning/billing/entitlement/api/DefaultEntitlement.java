/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.entitlement.api;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountInternalApi;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.catalog.api.BillingActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.DefaultEntitlementService;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.engine.core.EntitlementNotificationKey;
import com.ning.billing.entitlement.engine.core.EntitlementNotificationKeyAction;
import com.ning.billing.entitlement.engine.core.EntitlementUtils;
import com.ning.billing.entity.EntityBase;
import com.ning.billing.junction.DefaultBlockingState;
import com.ning.billing.notificationq.api.NotificationEvent;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class DefaultEntitlement extends EntityBase implements Entitlement {

    protected final EntitlementDateHelper dateHelper;
    protected final InternalCallContextFactory internalCallContextFactory;
    protected final Clock clock;
    protected final BlockingChecker checker;
    protected final UUID accountId;
    protected final AccountInternalApi accountApi;
    protected final EntitlementApi entitlementApi;
    protected final SubscriptionBaseInternalApi subscriptionInternalApi;
    protected final BlockingStateDao blockingStateDao;
    protected final NotificationQueueService notificationQueueService;
    protected final EntitlementUtils entitlementUtils;

    // Refresh-able
    protected SubscriptionBase subscriptionBase;
    protected EntitlementState state;
    protected LocalDate effectiveEndDate;
    protected String externalKey;
    protected DateTimeZone accountTimeZone;

    public DefaultEntitlement(final EntitlementDateHelper dateHelper, final SubscriptionBase subscriptionBase, final UUID accountId,
                              final String externalKey, final EntitlementState state, final LocalDate effectiveEndDate, final DateTimeZone accountTimeZone,
                              final AccountInternalApi accountApi, final EntitlementApi entitlementApi, final SubscriptionBaseInternalApi subscriptionInternalApi, final InternalCallContextFactory internalCallContextFactory,
                              final BlockingStateDao blockingStateDao, final Clock clock, final BlockingChecker checker, final NotificationQueueService notificationQueueService,
                              final EntitlementUtils entitlementUtils) {
        super(subscriptionBase.getId(), subscriptionBase.getCreatedDate(), subscriptionBase.getUpdatedDate());
        this.dateHelper = dateHelper;
        this.subscriptionBase = subscriptionBase;
        this.accountId = accountId;
        this.externalKey = externalKey;
        this.state = state;
        this.effectiveEndDate = effectiveEndDate;
        this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.accountTimeZone = accountTimeZone;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.notificationQueueService = notificationQueueService;
        this.entitlementUtils = entitlementUtils;
    }

    public DefaultEntitlement(final DefaultEntitlement in) {
        this(in.getDateHelper(),
             in.getSubscriptionBase(),
             in.getAccountId(),
             in.getExternalKey(),
             in.getState(),
             in.getEffectiveEndDate(),
             in.getAccountTimeZone(),
             in.getAccountApi(),
             in.getEntitlementApi(),
             in.getSubscriptionInternalApi(),
             in.getInternalCallContextFactory(),
             in.getBlockingStateDao(),
             in.getClock(),
             in.getChecker(),
             in.getNotificationQueueService(),
             in.getEntitlementUtils());
    }

    public SubscriptionBase getSubscriptionBase() {
        return subscriptionBase;
    }

    public EntitlementDateHelper getDateHelper() {
        return dateHelper;
    }

    public InternalCallContextFactory getInternalCallContextFactory() {
        return internalCallContextFactory;
    }

    public AccountInternalApi getAccountApi() {
        return accountApi;
    }

    public EntitlementApi getEntitlementApi() {
        return entitlementApi;
    }

    public SubscriptionBaseInternalApi getSubscriptionInternalApi() {
        return subscriptionInternalApi;
    }

    public Clock getClock() {
        return clock;
    }

    public BlockingChecker getChecker() {
        return checker;
    }

    public DateTimeZone getAccountTimeZone() {
        return accountTimeZone;
    }

    public BlockingStateDao getBlockingStateDao() {
        return blockingStateDao;
    }

    public NotificationQueueService getNotificationQueueService() {
        return notificationQueueService;
    }

    public EntitlementUtils getEntitlementUtils() {
        return entitlementUtils;
    }

    @Override
    public UUID getBaseEntitlementId() {
        return subscriptionBase.getId();
    }

    @Override
    public UUID getBundleId() {
        return subscriptionBase.getBundleId();
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public EntitlementState getState() {
        return state;
    }

    @Override
    public EntitlementSourceType getSourceType() {
        return subscriptionBase.getSourceType();
    }

    @Override
    public LocalDate getEffectiveStartDate() {
        return new LocalDate(subscriptionBase.getStartDate(), accountTimeZone);
    }

    @Override
    public LocalDate getEffectiveEndDate() {
        return effectiveEndDate;
    }

    @Override
    public Product getLastActiveProduct() {
        return subscriptionBase.getLastActiveProduct();
    }

    @Override
    public Plan getLastActivePlan() {
        return subscriptionBase.getLastActivePlan();
    }

    @Override
    public PlanPhase getLastActivePhase() {
        return subscriptionBase.getLastActivePhase();
    }

    @Override
    public PriceList getLastActivePriceList() {
        return subscriptionBase.getLastActivePriceList();
    }

    @Override
    public ProductCategory getLastActiveProductCategory() {
        return subscriptionBase.getLastActiveCategory();
    }


    @Override
    public Entitlement cancelEntitlementWithPolicy(final EntitlementActionPolicy entitlementPolicy, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);

        // Get the latest state from disk - required to have the latest CTD
        refresh(callContext, contextWithValidAccountRecordId);

        final LocalDate cancellationDate = getLocalDateFromEntitlementPolicy(entitlementPolicy);
        return cancelEntitlementWithDate(cancellationDate, false, callContext);
    }

    @Override
    public Entitlement cancelEntitlementWithDate(final LocalDate localCancelDate, final boolean overrideBillingEffectiveDate, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);

        // Get the latest state from disk
        refresh(callContext, contextWithValidAccountRecordId);

        if (state == EntitlementState.CANCELLED) {
            throw new EntitlementApiException(ErrorCode.SUB_CANCEL_BAD_STATE, getId(), EntitlementState.CANCELLED);
        }

        final DateTime effectiveCancelDate = dateHelper.fromLocalDateAndReferenceTime(localCancelDate, subscriptionBase.getStartDate(), contextWithValidAccountRecordId);
        try {
            if (overrideBillingEffectiveDate) {
                subscriptionBase.cancelWithDate(effectiveCancelDate, callContext);
            } else {
                subscriptionBase.cancel(callContext);
            }
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        final BlockingState newBlockingState = new DefaultBlockingState(getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, effectiveCancelDate);
        entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(newBlockingState, contextWithValidAccountRecordId);

        blockAddOnsIfRequired(effectiveCancelDate, callContext, contextWithValidAccountRecordId);

        return entitlementApi.getEntitlementForId(getId(), callContext);
    }

    @Override
    public Entitlement cancelEntitlementWithPolicyOverrideBillingPolicy(final EntitlementActionPolicy entitlementPolicy, final BillingActionPolicy billingPolicy, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);

        // Get the latest state from disk - required to have the latest CTD
        refresh(callContext, contextWithValidAccountRecordId);

        final LocalDate cancellationDate = getLocalDateFromEntitlementPolicy(entitlementPolicy);
        return cancelEntitlementWithDateOverrideBillingPolicy(cancellationDate, billingPolicy, callContext);
    }

    @Override
    public void uncancelEntitlement(final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);

        // Get the latest state from disk
        refresh(callContext, contextWithValidAccountRecordId);

        if (state == EntitlementState.CANCELLED || subscriptionBase.getState() == EntitlementState.CANCELLED) {
            throw new EntitlementApiException(ErrorCode.SUB_CANCEL_BAD_STATE, getId(), EntitlementState.CANCELLED);
        }
        final List<BlockingState> blockingStatesForAccount = blockingStateDao.getBlockingAllForAccountRecordId(contextWithValidAccountRecordId);
        final Collection<BlockingState> futureEntitlementCancellationEvents = Collections2.filter(blockingStatesForAccount, new Predicate<BlockingState>() {
            @Override
            public boolean apply(final BlockingState input) {
                // Delete all future cancellation events...
                return EntitlementService.ENTITLEMENT_SERVICE_NAME.equals(input.getService()) &&
                       DefaultEntitlementApi.ENT_STATE_CANCELLED.equals(input.getStateName()) &&
                       input.getEffectiveDate().isAfter(clock.getUTCNow()) &&
                       (
                               // ... for that subscription
                               BlockingStateType.SUBSCRIPTION.equals(input.getType()) && input.getBlockedId().equals(getId()) ||
                               // ... for the associated base subscription (to make sure an add-on isn't associated with a cancelled base entitlement)
                               BlockingStateType.SUBSCRIPTION.equals(input.getType()) && input.getBlockedId().equals(getBaseEntitlementId()) ||
                               // ... for that bundle (to make sure the subscription isn't associated with a cancelled bundle - not yet implemented)
                               BlockingStateType.SUBSCRIPTION_BUNDLE.equals(input.getType()) && input.getBlockedId().equals(getBundleId()) ||
                               // ... for that bundle (to make sure the subscription isn't associated with a cancelled account - not yet implemented)
                               BlockingStateType.ACCOUNT.equals(input.getType()) && input.getBlockedId().equals(getAccountId())
                       );
            }
        });

        // Reactivate entitlement
        // We should only have one future event in theory - but cleanup the data if it's not the case
        // See https://github.com/killbill/killbill/issues/111
        for (final BlockingState futureCancellation : futureEntitlementCancellationEvents) {
            blockingStateDao.unactiveBlockingState(futureCancellation.getId(), contextWithValidAccountRecordId);
        }

        // If billing was previously cancelled, reactivate
        if (subscriptionBase.getFutureEndDate() != null) {
            try {
                subscriptionBase.uncancel(callContext);
            } catch (SubscriptionBaseApiException e) {
                throw new EntitlementApiException(e);
            }
        }
    }

    @Override
    public Entitlement cancelEntitlementWithDateOverrideBillingPolicy(final LocalDate localCancelDate, final BillingActionPolicy billingPolicy, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);

        // Get the latest state from disk
        refresh(callContext, contextWithValidAccountRecordId);

        if (state == EntitlementState.CANCELLED) {
            throw new EntitlementApiException(ErrorCode.SUB_CANCEL_BAD_STATE, getId(), EntitlementState.CANCELLED);
        }

        try {
            subscriptionBase.cancelWithPolicy(billingPolicy, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        final LocalDate effectiveLocalDate = new LocalDate(localCancelDate, accountTimeZone);
        final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(effectiveLocalDate, subscriptionBase.getStartDate(), contextWithValidAccountRecordId);
        final BlockingState newBlockingState = new DefaultBlockingState(getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, effectiveDate);
        entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(newBlockingState, contextWithValidAccountRecordId);

        blockAddOnsIfRequired(effectiveDate, callContext, contextWithValidAccountRecordId);

        return entitlementApi.getEntitlementForId(getId(), callContext);
    }

    private LocalDate getLocalDateFromEntitlementPolicy(final EntitlementActionPolicy entitlementPolicy) {
        final LocalDate cancellationDate;
        switch (entitlementPolicy) {
            case IMMEDIATE:
                cancellationDate = new LocalDate(clock.getUTCNow(), accountTimeZone);
                break;
            case END_OF_TERM:
                cancellationDate = subscriptionBase.getChargedThroughDate() != null ? new LocalDate(subscriptionBase.getChargedThroughDate(), accountTimeZone) : new LocalDate(clock.getUTCNow(), accountTimeZone);
                break;
            default:
                throw new RuntimeException("Unsupported policy " + entitlementPolicy);
        }
        return cancellationDate;
    }

    @Override
    public Entitlement changePlan(final String productName, final BillingPeriod billingPeriod, final String priceList, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, callContext);

        // Get the latest state from disk
        refresh(callContext, context);

        if (state != EntitlementState.ACTIVE) {
            throw new EntitlementApiException(ErrorCode.SUB_CHANGE_NON_ACTIVE, getId(), state);
        }

        try {
            checker.checkBlockedChange(subscriptionBase, context);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        }

        final DateTime effectiveChangeDate;
        try {
            effectiveChangeDate = subscriptionBase.changePlan(productName, billingPeriod, priceList, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        blockAddOnsIfRequired(effectiveChangeDate, callContext, context);

        return entitlementApi.getEntitlementForId(getId(), callContext);
    }

    @Override
    public Entitlement changePlanWithDate(final String productName, final BillingPeriod billingPeriod, final String priceList, final LocalDate localDate, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, callContext);

        // Get the latest state from disk
        refresh(callContext, context);

        if (state != EntitlementState.ACTIVE) {
            throw new EntitlementApiException(ErrorCode.SUB_CHANGE_NON_ACTIVE, getId(), state);
        }

        try {
            checker.checkBlockedChange(subscriptionBase, context);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        }

        final DateTime effectiveChangeDate = dateHelper.fromLocalDateAndReferenceTime(localDate, subscriptionBase.getStartDate(), context);
        try {
            subscriptionBase.changePlanWithDate(productName, billingPeriod, priceList, effectiveChangeDate, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        blockAddOnsIfRequired(effectiveChangeDate, callContext, context);

        return entitlementApi.getEntitlementForId(getId(), callContext);
    }

    @Override
    public Entitlement changePlanOverrideBillingPolicy(final String productName, final BillingPeriod billingPeriod, final String priceList, final LocalDate localDateX, final BillingActionPolicy actionPolicy, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, callContext);

        // Get the latest state from disk
        refresh(callContext, context);

        if (state != EntitlementState.ACTIVE) {
            throw new EntitlementApiException(ErrorCode.SUB_CHANGE_NON_ACTIVE, getId(), state);
        }

        try {
            checker.checkBlockedChange(subscriptionBase, context);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        }

        final DateTime effectiveChangeDate;
        try {
            effectiveChangeDate = subscriptionBase.changePlanWithPolicy(productName, billingPeriod, priceList, actionPolicy, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        blockAddOnsIfRequired(effectiveChangeDate, callContext, context);

        return entitlementApi.getEntitlementForId(getId(), callContext);
    }

    private void refresh(final TenantContext context, final InternalCallContext internalCallContext) throws EntitlementApiException {
        try {
            final Account account = accountApi.getAccountById(accountId, internalCallContext);
            // Not really required today as we don't allow timezone changes, but do it nonetheless
            // in case we change our mind later (and so we don't get surprises...).
            accountTimeZone = account.getTimeZone();
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        final Entitlement refreshedEntitlement = entitlementApi.getEntitlementForId(getId(), context);
        state = refreshedEntitlement.getState();
        effectiveEndDate = refreshedEntitlement.getEffectiveEndDate();
        externalKey = refreshedEntitlement.getExternalKey();
        if (refreshedEntitlement instanceof DefaultEntitlement) {
            subscriptionBase = ((DefaultEntitlement) refreshedEntitlement).getSubscriptionBase();
        } else {
            throw new IllegalStateException("We only support DefaultEntitlement implementations");
        }
    }

    public void blockAddOnsIfRequired(@Nullable final DateTime effectiveDateOrNull, final TenantContext context, final InternalCallContext internalCallContext) throws EntitlementApiException {
        // Optimization - bail early
        if (!ProductCategory.BASE.equals(subscriptionBase.getCategory())) {
            // Only base subscriptions have add-ons
            return;
        }

        // Get the latest state from disk (we just got cancelled or changed plan)
        refresh(context, internalCallContext);

        final DateTime now = clock.getUTCNow();

        // null means immediate
        final DateTime effectiveDate = effectiveDateOrNull == null ? now : effectiveDateOrNull;

        final boolean isBaseEntitlementCancelled = EntitlementState.CANCELLED.equals(state);

        // If cancellation/change occurs in the future, do nothing for now but add a notification entry.
        // This is to distinguish whether a future cancellation was requested by the user, or was a side effect
        // (e.g. base plan cancellation): future entitlement cancellations for add-ons on disk always reflect
        // an explicit cancellation. This trick lets us determine what to do when un-cancelling.
        // This mirror the behavior in subscription base (see DefaultSubscriptionBaseApiService).
        if (effectiveDate.compareTo(now) > 0) {
            // Note that usually we record the notification from the DAO. We cannot do it here because not all calls
            // go through the DAO (e.g. change)
            final NotificationEvent notificationEvent = new EntitlementNotificationKey(getId(), isBaseEntitlementCancelled ? EntitlementNotificationKeyAction.CANCEL : EntitlementNotificationKeyAction.CHANGE, effectiveDate);
            recordFutureNotification(effectiveDate, notificationEvent, internalCallContext);
            return;
        }

        final Collection<BlockingState> addOnsBlockingStates = entitlementUtils.computeBlockingStatesForAssociatedAddons(subscriptionBase, effectiveDate, internalCallContext);
        for (final BlockingState addOnBlockingState : addOnsBlockingStates) {
            entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(addOnBlockingState, internalCallContext);
        }
    }

    private void recordFutureNotification(final DateTime effectiveDate,
                                          final NotificationEvent notificationEvent,
                                          final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                                                           DefaultEntitlementService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotification(effectiveDate, notificationEvent, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
