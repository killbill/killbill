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

package org.killbill.billing.entitlement.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.clock.Clock;
import org.killbill.billing.entitlement.AccountEventsStreams;
import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.entitlement.EntitlementService;
import org.killbill.billing.entitlement.EntitlementTransitionType;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.block.BlockingChecker;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.entitlement.engine.core.EntitlementNotificationKey;
import org.killbill.billing.entitlement.engine.core.EntitlementNotificationKeyAction;
import org.killbill.billing.entitlement.engine.core.EntitlementUtils;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import org.killbill.billing.subscription.api.transfer.SubscriptionBaseTransferApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DefaultEntitlementApi implements EntitlementApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultEntitlementApi.class);

    public static final String ENT_STATE_BLOCKED = "ENT_BLOCKED";
    public static final String ENT_STATE_CLEAR = "ENT_CLEAR";
    public static final String ENT_STATE_CANCELLED = "ENT_CANCELLED";

    private final SubscriptionBaseInternalApi subscriptionBaseInternalApi;
    private final SubscriptionBaseTransferApi subscriptionBaseTransferApi;
    private final AccountInternalApi accountApi;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;
    private final BlockingChecker checker;
    private final BlockingStateDao blockingStateDao;
    private final EntitlementDateHelper dateHelper;
    private final PersistentBus eventBus;
    private final EventsStreamBuilder eventsStreamBuilder;
    private final EntitlementUtils entitlementUtils;
    private final NotificationQueueService notificationQueueService;

    @Inject
    public DefaultEntitlementApi(final PersistentBus eventBus, final InternalCallContextFactory internalCallContextFactory,
                                 final SubscriptionBaseTransferApi subscriptionTransferApi, final SubscriptionBaseInternalApi subscriptionInternalApi,
                                 final AccountInternalApi accountApi, final BlockingStateDao blockingStateDao, final Clock clock,
                                 final BlockingChecker checker, final NotificationQueueService notificationQueueService,
                                 final EventsStreamBuilder eventsStreamBuilder, final EntitlementUtils entitlementUtils) {
        this.eventBus = eventBus;
        this.internalCallContextFactory = internalCallContextFactory;
        this.subscriptionBaseInternalApi = subscriptionInternalApi;
        this.subscriptionBaseTransferApi = subscriptionTransferApi;
        this.accountApi = accountApi;
        this.clock = clock;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.notificationQueueService = notificationQueueService;
        this.eventsStreamBuilder = eventsStreamBuilder;
        this.entitlementUtils = entitlementUtils;
        this.dateHelper = new EntitlementDateHelper(accountApi, clock);
    }

    @Override
    public Entitlement createBaseEntitlement(final UUID accountId, final PlanPhaseSpecifier planPhaseSpecifier, final String externalKey, final LocalDate effectiveDate, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        try {

            if (entitlementUtils.getFirstActiveSubscriptionIdForKeyOrNull(externalKey, contextWithValidAccountRecordId) != null) {
                throw new EntitlementApiException(new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_ACTIVE_BUNDLE_KEY_EXISTS, externalKey));
            }

            final SubscriptionBaseBundle bundle = subscriptionBaseInternalApi.createBundleForAccount(accountId, externalKey, contextWithValidAccountRecordId);

            final DateTime referenceTime = clock.getUTCNow();
            final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, referenceTime, contextWithValidAccountRecordId);
            final SubscriptionBase subscription = subscriptionBaseInternalApi.createSubscription(bundle.getId(), planPhaseSpecifier, requestedDate, contextWithValidAccountRecordId);

            return new DefaultEntitlement(subscription.getId(), eventsStreamBuilder, this,
                                          blockingStateDao, subscriptionBaseInternalApi, checker, notificationQueueService,
                                          entitlementUtils, dateHelper, clock, internalCallContextFactory, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public Entitlement addEntitlement(final UUID bundleId, final PlanPhaseSpecifier planPhaseSpecifier, final LocalDate effectiveDate, final CallContext callContext) throws EntitlementApiException {
        final EventsStream eventsStreamForBaseSubscription = eventsStreamBuilder.buildForBaseSubscription(bundleId, callContext);

        // Check the base entitlement state is active
        if (!eventsStreamForBaseSubscription.isEntitlementActive()) {
            throw new EntitlementApiException(ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION, bundleId);
        }

        // Check the base entitlement state is not blocked
        if (eventsStreamForBaseSubscription.isBlockChange()) {
            throw new EntitlementApiException(new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, BlockingChecker.ACTION_CHANGE, BlockingChecker.TYPE_SUBSCRIPTION, eventsStreamForBaseSubscription.getEntitlementId().toString()));
        }

        final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, eventsStreamForBaseSubscription.getSubscriptionBase().getStartDate(), eventsStreamForBaseSubscription.getInternalTenantContext());

        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(callContext);
            final SubscriptionBase subscription = subscriptionBaseInternalApi.createSubscription(bundleId, planPhaseSpecifier, requestedDate, context);

            return new DefaultEntitlement(subscription.getId(), eventsStreamBuilder, this,
                                          blockingStateDao, subscriptionBaseInternalApi, checker, notificationQueueService,
                                          entitlementUtils, dateHelper, clock, internalCallContextFactory, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public List<EntitlementAOStatusDryRun> getDryRunStatusForChange(final UUID bundleId, final String targetProductName, final LocalDate effectiveDate, final TenantContext context) throws EntitlementApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        try {
            final SubscriptionBaseBundle bundle = subscriptionBaseInternalApi.getBundleFromId(bundleId, internalContext);
            final SubscriptionBase baseSubscription = subscriptionBaseInternalApi.getBaseSubscription(bundleId, internalContext);

            final InternalTenantContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalTenantContext(bundle.getAccountId(), context);
            final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);
            return subscriptionBaseInternalApi.getDryRunChangePlanStatus(baseSubscription.getId(), targetProductName, requestedDate, contextWithValidAccountRecordId);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public Entitlement getEntitlementForId(final UUID uuid, final TenantContext tenantContext) throws EntitlementApiException {
        final EventsStream eventsStream = eventsStreamBuilder.buildForEntitlement(uuid, tenantContext);
        return new DefaultEntitlement(eventsStream, eventsStreamBuilder, this,
                                      blockingStateDao, subscriptionBaseInternalApi, checker, notificationQueueService,
                                      entitlementUtils, dateHelper, clock, internalCallContextFactory);
    }

    @Override
    public List<Entitlement> getAllEntitlementsForBundle(final UUID bundleId, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(tenantContext);
        final UUID accountId;
        try {
            accountId = subscriptionBaseInternalApi.getBundleFromId(bundleId, internalContext).getAccountId();
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        return ImmutableList.<Entitlement>copyOf(Iterables.<Entitlement>filter(getAllEntitlementsForAccountId(accountId, tenantContext),
                                                                               new Predicate<Entitlement>() {
                                                                                   @Override
                                                                                   public boolean apply(final Entitlement input) {
                                                                                       return bundleId.equals(input.getBundleId());
                                                                                   }
                                                                               }));
    }

    @Override
    public List<Entitlement> getAllEntitlementsForAccountIdAndExternalKey(final UUID accountId, final String externalKey, final TenantContext tenantContext) throws EntitlementApiException {
        // getAllEntitlementsForAccountId should be fast (uses account_record_id)
        return ImmutableList.<Entitlement>copyOf(Iterables.<Entitlement>filter(getAllEntitlementsForAccountId(accountId, tenantContext),
                                                                               new Predicate<Entitlement>() {
                                                                                   @Override
                                                                                   public boolean apply(final Entitlement input) {
                                                                                       return externalKey.equals(input.getExternalKey());
                                                                                   }
                                                                               }));
    }

    @Override
    public List<Entitlement> getAllEntitlementsForAccountId(final UUID accountId, final TenantContext tenantContext) throws EntitlementApiException {
        final EntitlementApi entitlementApi = this;
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(accountId, tenantContext);

        final AccountEventsStreams accountEventsStreams = eventsStreamBuilder.buildForAccount(context);
        final List<EventsStream> eventsStreams = ImmutableList.<EventsStream>copyOf(Iterables.<EventsStream>concat(accountEventsStreams.getEventsStreams().values()));
        return Lists.<EventsStream, Entitlement>transform(eventsStreams,
                                                          new Function<EventsStream, Entitlement>() {
                                                              @Override
                                                              public Entitlement apply(final EventsStream eventsStream) {
                                                                  return new DefaultEntitlement(eventsStream, eventsStreamBuilder, entitlementApi,
                                                                                                blockingStateDao, subscriptionBaseInternalApi, checker, notificationQueueService,
                                                                                                entitlementUtils, dateHelper, clock, internalCallContextFactory);
                                                              }
                                                          });
    }

    @Override
    public void pause(final UUID bundleId, final LocalDate localEffectiveDate, final CallContext context) throws EntitlementApiException {
        try {
            final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, context);
            final BlockingState currentState = blockingStateDao.getBlockingStateForService(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, EntitlementService.ENTITLEMENT_SERVICE_NAME, contextWithValidAccountRecordId);
            if (currentState != null && currentState.getStateName().equals(ENT_STATE_BLOCKED)) {
                throw new EntitlementApiException(ErrorCode.ENT_ALREADY_BLOCKED, bundleId);
            }

            final SubscriptionBaseBundle bundle = subscriptionBaseInternalApi.getBundleFromId(bundleId, contextWithValidAccountRecordId);
            final Account account = accountApi.getAccountById(bundle.getAccountId(), contextWithValidAccountRecordId);
            final SubscriptionBase baseSubscription = subscriptionBaseInternalApi.getBaseSubscription(bundleId, contextWithValidAccountRecordId);
            final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(localEffectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);

            if (!dateHelper.isBeforeOrEqualsToday(effectiveDate, account.getTimeZone())) {
                recordPauseResumeNotificationEntry(baseSubscription.getId(), bundleId, effectiveDate, true, contextWithValidAccountRecordId);
                return;
            }

            final BlockingState state = new DefaultBlockingState(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, ENT_STATE_BLOCKED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, true, effectiveDate);
            entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(state, contextWithValidAccountRecordId);

            // Should we send one event per entitlement in the bundle?
            // Code below only sends one event for the bundle and use the base entitlementId
            final DefaultEffectiveEntitlementEvent event = new DefaultEffectiveEntitlementEvent(state.getId(), baseSubscription.getId(), bundleId, bundle.getAccountId(), EntitlementTransitionType.BLOCK_BUNDLE,
                                                                                                effectiveDate, clock.getUTCNow(),
                                                                                                contextWithValidAccountRecordId.getAccountRecordId(), contextWithValidAccountRecordId.getTenantRecordId(),
                                                                                                contextWithValidAccountRecordId.getUserToken());

            try {
                eventBus.post(event);
            } catch (EventBusException e) {
                log.warn("Failed to post bus event for pause operation on bundle " + bundleId);
            }

        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public void resume(final UUID bundleId, final LocalDate localEffectiveDate, final CallContext context) throws EntitlementApiException {
        try {
            final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, context);
            final SubscriptionBaseBundle bundle = subscriptionBaseInternalApi.getBundleFromId(bundleId, contextWithValidAccountRecordId);
            final Account account = accountApi.getAccountById(bundle.getAccountId(), contextWithValidAccountRecordId);
            final SubscriptionBase baseSubscription = subscriptionBaseInternalApi.getBaseSubscription(bundleId, contextWithValidAccountRecordId);

            final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(localEffectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);

            if (!dateHelper.isBeforeOrEqualsToday(effectiveDate, account.getTimeZone())) {
                recordPauseResumeNotificationEntry(baseSubscription.getId(), bundleId, effectiveDate, false, contextWithValidAccountRecordId);
                return;
            }

            final BlockingState currentState = blockingStateDao.getBlockingStateForService(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, EntitlementService.ENTITLEMENT_SERVICE_NAME, contextWithValidAccountRecordId);
            if (currentState == null || currentState.getStateName().equals(ENT_STATE_CLEAR)) {
                // Nothing to do.
                log.warn("Current state is {}, nothing to resume", currentState);
                return;
            }

            final BlockingState state = new DefaultBlockingState(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, ENT_STATE_CLEAR, EntitlementService.ENTITLEMENT_SERVICE_NAME, false, false, false, effectiveDate);
            entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(state, contextWithValidAccountRecordId);

            // Should we send one event per entitlement in the bundle?
            // Code below only sends one event for the bundle and use the base entitlementId
            final DefaultEffectiveEntitlementEvent event = new DefaultEffectiveEntitlementEvent(state.getId(), baseSubscription.getId(), bundleId, bundle.getAccountId(), EntitlementTransitionType.UNBLOCK_BUNDLE,
                                                                                                effectiveDate, clock.getUTCNow(),
                                                                                                contextWithValidAccountRecordId.getAccountRecordId(), contextWithValidAccountRecordId.getTenantRecordId(),
                                                                                                contextWithValidAccountRecordId.getUserToken());

            try {
                eventBus.post(event);
            } catch (EventBusException e) {
                log.warn("Failed to post bus event for resume operation on bundle " + bundleId);
            }

        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public UUID transferEntitlements(final UUID sourceAccountId, final UUID destAccountId, final String externalKey, final LocalDate effectiveDate, final CallContext context) throws EntitlementApiException {
        return transferEntitlementsOverrideBillingPolicy(sourceAccountId, destAccountId, externalKey, effectiveDate, BillingActionPolicy.IMMEDIATE, context);
    }

    @Override
    public UUID transferEntitlementsOverrideBillingPolicy(final UUID sourceAccountId, final UUID destAccountId, final String externalKey, final LocalDate effectiveDate, final BillingActionPolicy billingPolicy, final CallContext context) throws EntitlementApiException {
        final boolean cancelImm;
        switch (billingPolicy) {
            case IMMEDIATE:
                cancelImm = true;
                break;
            case END_OF_TERM:
                cancelImm = false;
                break;
            default:
                throw new RuntimeException("Unexpected billing policy " + billingPolicy);
        }

        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(sourceAccountId, context);
        try {

            final UUID activeSubscriptionIdForKey = entitlementUtils.getFirstActiveSubscriptionIdForKeyOrNull(externalKey, contextWithValidAccountRecordId);
            final SubscriptionBase baseSubscription = activeSubscriptionIdForKey != null ?
                                                      subscriptionBaseInternalApi.getSubscriptionFromId(activeSubscriptionIdForKey, contextWithValidAccountRecordId) : null;
            final SubscriptionBaseBundle baseBundle = baseSubscription != null ?
                                                      subscriptionBaseInternalApi.getBundleFromId(baseSubscription.getBundleId(), contextWithValidAccountRecordId) : null;

            if (baseBundle == null || ! baseBundle.getAccountId().equals(sourceAccountId)) {
                throw new EntitlementApiException(new SubscriptionBaseApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_KEY, externalKey));
            }

            final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);
            final SubscriptionBaseBundle newBundle = subscriptionBaseTransferApi.transferBundle(sourceAccountId, destAccountId, externalKey, requestedDate, true, cancelImm, context);

            // Block all associated subscriptions - TODO Do we want to block the bundle as well (this will add an extra STOP_ENTITLEMENT event in the bundle timeline stream)?
            // Note that there is no un-transfer at the moment, so we effectively add a blocking state on disk for all subscriptions
            for (final SubscriptionBase subscriptionBase : subscriptionBaseInternalApi.getSubscriptionsForBundle(baseBundle.getId(), null, contextWithValidAccountRecordId)) {
                final BlockingState blockingState = new DefaultBlockingState(subscriptionBase.getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, requestedDate);
                entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(blockingState, contextWithValidAccountRecordId);
            }

            return newBundle.getId();
        } catch (SubscriptionBaseTransferApiException e) {
            throw new EntitlementApiException(e);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    private void recordPauseResumeNotificationEntry(final UUID entitlementId, final UUID bundleId, final DateTime effectiveDate, final boolean isPause, final InternalCallContext contextWithValidAccountRecordId) throws EntitlementApiException {
        final NotificationEvent notificationEvent = new EntitlementNotificationKey(entitlementId,
                                                                                   bundleId,
                                                                                   isPause ? EntitlementNotificationKeyAction.PAUSE : EntitlementNotificationKeyAction.RESUME,
                                                                                   effectiveDate);

        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultEntitlementService.ENTITLEMENT_SERVICE_NAME,
                                                                                                           DefaultEntitlementService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotification(effectiveDate, notificationEvent, contextWithValidAccountRecordId.getUserToken(), contextWithValidAccountRecordId.getAccountRecordId(), contextWithValidAccountRecordId.getTenantRecordId());
        } catch (final NoSuchNotificationQueue e) {
            throw new EntitlementApiException(e, ErrorCode.__UNKNOWN_ERROR_CODE);
        } catch (final IOException e) {
            throw new EntitlementApiException(e, ErrorCode.__UNKNOWN_ERROR_CODE);
        }
    }

}
