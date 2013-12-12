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
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.ErrorCode;
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
import com.ning.billing.entitlement.EventsStream;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.engine.core.EntitlementNotificationKey;
import com.ning.billing.entitlement.engine.core.EntitlementNotificationKeyAction;
import com.ning.billing.entitlement.engine.core.EntitlementUtils;
import com.ning.billing.entitlement.engine.core.EventsStreamBuilder;
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

public class DefaultEntitlement extends EntityBase implements Entitlement {

    protected final EventsStreamBuilder eventsStreamBuilder;
    protected final EntitlementDateHelper dateHelper;
    protected final InternalCallContextFactory internalCallContextFactory;
    protected final Clock clock;
    protected final BlockingChecker checker;
    protected final EntitlementApi entitlementApi;
    protected final SubscriptionBaseInternalApi subscriptionInternalApi;
    protected final BlockingStateDao blockingStateDao;
    protected final NotificationQueueService notificationQueueService;
    protected final EntitlementUtils entitlementUtils;

    // Refresh-able
    protected EventsStream eventsStream;

    public DefaultEntitlement(final UUID entitlementId, final EventsStreamBuilder eventsStreamBuilder,
                              final EntitlementApi entitlementApi, final BlockingStateDao blockingStateDao,
                              final SubscriptionBaseInternalApi subscriptionInternalApi, final BlockingChecker checker,
                              final NotificationQueueService notificationQueueService, final EntitlementUtils entitlementUtils,
                              final EntitlementDateHelper dateHelper, final Clock clock,
                              final InternalCallContextFactory internalCallContextFactory, final TenantContext tenantContext) throws EntitlementApiException {
        this(eventsStreamBuilder.buildForEntitlement(entitlementId, tenantContext), eventsStreamBuilder,
             entitlementApi, blockingStateDao, subscriptionInternalApi, checker, notificationQueueService,
             entitlementUtils, dateHelper, clock, internalCallContextFactory);
    }

    public DefaultEntitlement(final EventsStream eventsStream, final EventsStreamBuilder eventsStreamBuilder,
                              final EntitlementApi entitlementApi, final BlockingStateDao blockingStateDao,
                              final SubscriptionBaseInternalApi subscriptionInternalApi, final BlockingChecker checker,
                              final NotificationQueueService notificationQueueService, final EntitlementUtils entitlementUtils,
                              final EntitlementDateHelper dateHelper, final Clock clock, final InternalCallContextFactory internalCallContextFactory) {
        super(eventsStream.getEntitlementId(), eventsStream.getSubscriptionBase().getCreatedDate(), eventsStream.getSubscriptionBase().getUpdatedDate());
        this.eventsStreamBuilder = eventsStreamBuilder;
        this.eventsStream = eventsStream;
        this.dateHelper = dateHelper;
        this.entitlementApi = entitlementApi;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.notificationQueueService = notificationQueueService;
        this.entitlementUtils = entitlementUtils;
    }

    public DefaultEntitlement(final DefaultEntitlement in) {
        this(in.getEventsStream(),
             in.getEventsStreamBuilder(),
             in.getEntitlementApi(),
             in.getBlockingStateDao(),
             in.getSubscriptionInternalApi(),
             in.getChecker(),
             in.getNotificationQueueService(),
             in.getEntitlementUtils(),
             in.getDateHelper(),
             in.getClock(),
             in.getInternalCallContextFactory());
    }

    public EventsStream getEventsStream() {
        return eventsStream;
    }

    public DateTimeZone getAccountTimeZone() {
        return eventsStream.getAccountTimeZone();
    }

    // Subscription associated with this entitlement (equals to baseSubscription for base subscriptions)
    public SubscriptionBase getSubscriptionBase() {
        return eventsStream.getSubscriptionBase();
    }

    // Base subscription for the bundle if it exists, null otherwise
    public SubscriptionBase getBasePlanSubscriptionBase() {
        return eventsStream.getBasePlanSubscriptionBase();
    }

    public EventsStreamBuilder getEventsStreamBuilder() {
        return eventsStreamBuilder;
    }

    public EntitlementDateHelper getDateHelper() {
        return dateHelper;
    }

    public InternalCallContextFactory getInternalCallContextFactory() {
        return internalCallContextFactory;
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
        return eventsStream.getEntitlementId();
    }

    @Override
    public UUID getBundleId() {
        return eventsStream.getBundleId();
    }

    @Override
    public UUID getAccountId() {
        return eventsStream.getAccountId();
    }

    @Override
    public String getExternalKey() {
        return eventsStream.getBundleExternalKey();
    }

    @Override
    public EntitlementState getState() {
        return eventsStream.getEntitlementState();
    }

    @Override
    public EntitlementSourceType getSourceType() {
        return getSubscriptionBase().getSourceType();
    }

    @Override
    public LocalDate getEffectiveStartDate() {
        return new LocalDate(getSubscriptionBase().getStartDate(), eventsStream.getAccountTimeZone());
    }

    @Override
    public LocalDate getEffectiveEndDate() {
        return eventsStream.getEntitlementEffectiveEndDate();
    }

    @Override
    public Product getLastActiveProduct() {
        return getSubscriptionBase().getLastActiveProduct();
    }

    @Override
    public Plan getLastActivePlan() {
        return getSubscriptionBase().getLastActivePlan();
    }

    @Override
    public PlanPhase getLastActivePhase() {
        return getSubscriptionBase().getLastActivePhase();
    }

    @Override
    public PriceList getLastActivePriceList() {
        return getSubscriptionBase().getLastActivePriceList();
    }

    @Override
    public ProductCategory getLastActiveProductCategory() {
        return getSubscriptionBase().getLastActiveCategory();
    }

    @Override
    public Entitlement cancelEntitlementWithPolicy(final EntitlementActionPolicy entitlementPolicy, final CallContext callContext) throws EntitlementApiException {
        // Get the latest state from disk - required to have the latest CTD
        refresh(callContext);

        final LocalDate cancellationDate = getLocalDateFromEntitlementPolicy(entitlementPolicy);
        return cancelEntitlementWithDate(cancellationDate, false, callContext);
    }

    @Override
    public Entitlement cancelEntitlementWithDate(final LocalDate localCancelDate, final boolean overrideBillingEffectiveDate, final CallContext callContext) throws EntitlementApiException {
        // Get the latest state from disk
        refresh(callContext);

        if (eventsStream.isEntitlementCancelled()) {
            throw new EntitlementApiException(ErrorCode.SUB_CANCEL_BAD_STATE, getId(), EntitlementState.CANCELLED);
        }

        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(getAccountId(), callContext);
        final DateTime effectiveCancelDate = dateHelper.fromLocalDateAndReferenceTime(localCancelDate, getSubscriptionBase().getStartDate(), contextWithValidAccountRecordId);
        try {
            if (overrideBillingEffectiveDate) {
                getSubscriptionBase().cancelWithDate(effectiveCancelDate, callContext);
            } else {
                getSubscriptionBase().cancel(callContext);
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
        // Get the latest state from disk - required to have the latest CTD
        refresh(callContext);

        final LocalDate cancellationDate = getLocalDateFromEntitlementPolicy(entitlementPolicy);
        return cancelEntitlementWithDateOverrideBillingPolicy(cancellationDate, billingPolicy, callContext);
    }

    @Override
    public void uncancelEntitlement(final CallContext callContext) throws EntitlementApiException {
        // Get the latest state from disk
        refresh(callContext);

        if (eventsStream.isSubscriptionCancelled()) {
            throw new EntitlementApiException(ErrorCode.SUB_CANCEL_BAD_STATE, getId(), EntitlementState.CANCELLED);
        }

        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(getAccountId(), callContext);
        final Collection<BlockingState> pendingEntitlementCancellationEvents = eventsStream.getPendingEntitlementCancellationEvents();
        if (eventsStream.isEntitlementCancelled()) {
            final BlockingState cancellationEvent = eventsStream.getEntitlementCancellationEvent();
            blockingStateDao.unactiveBlockingState(cancellationEvent.getId(), contextWithValidAccountRecordId);
        } else if (pendingEntitlementCancellationEvents.size() > 0) {
            // Reactivate entitlements
            // See also https://github.com/killbill/killbill/issues/111
            //
            // Today we only support cancellation at SUBSCRIPTION level (Not ACCOUNT or BUNDLE), so we should really have only
            // one future event in the list
            //
            for (final BlockingState futureCancellation : pendingEntitlementCancellationEvents) {
                blockingStateDao.unactiveBlockingState(futureCancellation.getId(), contextWithValidAccountRecordId);
            }
        } else {
            // Entitlement is NOT cancelled (or future cancelled), there is nothing to do
            throw new EntitlementApiException(ErrorCode.SUB_CANCEL_BAD_STATE, getId(), EntitlementState.CANCELLED);
        }

        // If billing was previously cancelled, reactivate
        if (getSubscriptionBase().getFutureEndDate() != null) {
            try {
                getSubscriptionBase().uncancel(callContext);
            } catch (SubscriptionBaseApiException e) {
                throw new EntitlementApiException(e);
            }
        }
    }

    @Override
    public Entitlement cancelEntitlementWithDateOverrideBillingPolicy(final LocalDate localCancelDate, final BillingActionPolicy billingPolicy, final CallContext callContext) throws EntitlementApiException {
        // Get the latest state from disk
        refresh(callContext);

        if (eventsStream.isEntitlementCancelled()) {
            throw new EntitlementApiException(ErrorCode.SUB_CANCEL_BAD_STATE, getId(), EntitlementState.CANCELLED);
        }

        // Make sure to compute the entitlement effective date first to avoid timing issues for IMM cancellations
        // (we don't want an entitlement cancel date one second or so after the subscription cancel date or add-ons cancellations
        // computations won't work).
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(getAccountId(), callContext);
        final LocalDate effectiveLocalDate = new LocalDate(localCancelDate, eventsStream.getAccountTimeZone());
        final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(effectiveLocalDate, getSubscriptionBase().getStartDate(), contextWithValidAccountRecordId);

        try {
            // Cancel subscription base first, to correctly compute the add-ons entitlements we need to cancel (see below)
            getSubscriptionBase().cancelWithPolicy(billingPolicy, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        final BlockingState newBlockingState = new DefaultBlockingState(getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, effectiveDate);
        entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(newBlockingState, contextWithValidAccountRecordId);

        blockAddOnsIfRequired(effectiveDate, callContext, contextWithValidAccountRecordId);

        return entitlementApi.getEntitlementForId(getId(), callContext);
    }

    private LocalDate getLocalDateFromEntitlementPolicy(final EntitlementActionPolicy entitlementPolicy) {
        final LocalDate cancellationDate;
        switch (entitlementPolicy) {
            case IMMEDIATE:
                cancellationDate = new LocalDate(clock.getUTCNow(), eventsStream.getAccountTimeZone());
                break;
            case END_OF_TERM:
                cancellationDate = getSubscriptionBase().getChargedThroughDate() != null ? new LocalDate(getSubscriptionBase().getChargedThroughDate(), eventsStream.getAccountTimeZone()) : new LocalDate(clock.getUTCNow(), eventsStream.getAccountTimeZone());
                break;
            default:
                throw new RuntimeException("Unsupported policy " + entitlementPolicy);
        }
        return cancellationDate;
    }

    @Override
    public Entitlement changePlan(final String productName, final BillingPeriod billingPeriod, final String priceList, final CallContext callContext) throws EntitlementApiException {
        // Get the latest state from disk
        refresh(callContext);

        if (!eventsStream.isEntitlementActive()) {
            throw new EntitlementApiException(ErrorCode.SUB_CHANGE_NON_ACTIVE, getId(), getState());
        }

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(getAccountId(), callContext);
        try {
            checker.checkBlockedChange(getSubscriptionBase(), context);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        }

        final DateTime effectiveChangeDate;
        try {
            effectiveChangeDate = getSubscriptionBase().changePlan(productName, billingPeriod, priceList, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        blockAddOnsIfRequired(effectiveChangeDate, callContext, context);

        return entitlementApi.getEntitlementForId(getId(), callContext);
    }

    @Override
    public Entitlement changePlanWithDate(final String productName, final BillingPeriod billingPeriod, final String priceList, final LocalDate localDate, final CallContext callContext) throws EntitlementApiException {
        // Get the latest state from disk
        refresh(callContext);

        if (!eventsStream.isEntitlementActive()) {
            throw new EntitlementApiException(ErrorCode.SUB_CHANGE_NON_ACTIVE, getId(), getState());
        }

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(getAccountId(), callContext);
        try {
            checker.checkBlockedChange(getSubscriptionBase(), context);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        }

        final DateTime effectiveChangeDate = dateHelper.fromLocalDateAndReferenceTime(localDate, getSubscriptionBase().getStartDate(), context);
        try {
            getSubscriptionBase().changePlanWithDate(productName, billingPeriod, priceList, effectiveChangeDate, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        blockAddOnsIfRequired(effectiveChangeDate, callContext, context);

        return entitlementApi.getEntitlementForId(getId(), callContext);
    }

    @Override
    public Entitlement changePlanOverrideBillingPolicy(final String productName, final BillingPeriod billingPeriod, final String priceList, final LocalDate localDateX, final BillingActionPolicy actionPolicy, final CallContext callContext) throws EntitlementApiException {
        // Get the latest state from disk
        refresh(callContext);

        if (!eventsStream.isEntitlementActive()) {
            throw new EntitlementApiException(ErrorCode.SUB_CHANGE_NON_ACTIVE, getId(), getState());
        }

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(getAccountId(), callContext);
        try {
            checker.checkBlockedChange(getSubscriptionBase(), context);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        }

        final DateTime effectiveChangeDate;
        try {
            effectiveChangeDate = getSubscriptionBase().changePlanWithPolicy(productName, billingPeriod, priceList, actionPolicy, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        blockAddOnsIfRequired(effectiveChangeDate, callContext, context);

        return entitlementApi.getEntitlementForId(getId(), callContext);
    }

    private void refresh(final TenantContext context) throws EntitlementApiException {
        eventsStream = eventsStreamBuilder.refresh(eventsStream, context);
    }

    public void blockAddOnsIfRequired(final DateTime effectiveDate, final TenantContext context, final InternalCallContext internalCallContext) throws EntitlementApiException {
        // Optimization - bail early
        if (!ProductCategory.BASE.equals(getSubscriptionBase().getCategory())) {
            // Only base subscriptions have add-ons
            return;
        }

        // Get the latest state from disk (we just got cancelled or changed plan)
        refresh(context);

        // If cancellation/change occurs in the future, do nothing for now but add a notification entry.
        // This is to distinguish whether a future cancellation was requested by the user, or was a side effect
        // (e.g. base plan cancellation): future entitlement cancellations for add-ons on disk always reflect
        // an explicit cancellation. This trick lets us determine what to do when un-cancelling.
        // This mirror the behavior in subscription base (see DefaultSubscriptionBaseApiService).
        final DateTime now = clock.getUTCNow();
        if (effectiveDate.compareTo(now) > 0) {
            // Note that usually we record the notification from the DAO. We cannot do it here because not all calls
            // go through the DAO (e.g. change)
            final boolean isBaseEntitlementCancelled = eventsStream.isEntitlementCancelled();
            final NotificationEvent notificationEvent = new EntitlementNotificationKey(getId(), getBundleId(), isBaseEntitlementCancelled ? EntitlementNotificationKeyAction.CANCEL : EntitlementNotificationKeyAction.CHANGE, effectiveDate);
            recordFutureNotification(effectiveDate, notificationEvent, internalCallContext);
            return;
        }

        final Collection<BlockingState> addOnsBlockingStates = eventsStream.computeAddonsBlockingStatesForNextSubscriptionBaseEvent(effectiveDate);
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
