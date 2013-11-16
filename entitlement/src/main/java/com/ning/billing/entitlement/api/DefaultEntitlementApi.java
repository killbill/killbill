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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountInternalApi;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBus.EventBusException;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.catalog.api.BillingActionPolicy;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.EntitlementTransitionType;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.entitlement.engine.core.EntitlementUtils;
import com.ning.billing.entitlement.engine.core.EventsStream;
import com.ning.billing.entitlement.engine.core.EventsStreamBuilder;
import com.ning.billing.junction.DefaultBlockingState;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import com.ning.billing.subscription.api.transfer.SubscriptionBaseTransferApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;

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

    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final SubscriptionBaseTransferApi subscriptionTransferApi;
    private final AccountInternalApi accountApi;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;
    private final BlockingChecker checker;
    private final BlockingStateDao blockingStateDao;
    private final EntitlementDateHelper dateHelper;
    private final PersistentBus eventBus;
    private final EventsStreamBuilder eventsStreamBuilder;
    private final EntitlementUtils entitlementUtils;
    protected final NotificationQueueService notificationQueueService;

    @Inject
    public DefaultEntitlementApi(final PersistentBus eventBus, final InternalCallContextFactory internalCallContextFactory,
                                 final SubscriptionBaseTransferApi subscriptionTransferApi, final SubscriptionBaseInternalApi subscriptionInternalApi,
                                 final AccountInternalApi accountApi, final BlockingStateDao blockingStateDao, final Clock clock,
                                 final BlockingChecker checker, final NotificationQueueService notificationQueueService,
                                 final EventsStreamBuilder eventsStreamBuilder, final EntitlementUtils entitlementUtils) {
        this.eventBus = eventBus;
        this.internalCallContextFactory = internalCallContextFactory;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.subscriptionTransferApi = subscriptionTransferApi;
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
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.createBundleForAccount(accountId, externalKey, contextWithValidAccountRecordId);

            final DateTime referenceTime = clock.getUTCNow();
            final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, referenceTime, contextWithValidAccountRecordId);
            final SubscriptionBase subscription = subscriptionInternalApi.createSubscription(bundle.getId(), planPhaseSpecifier, requestedDate, contextWithValidAccountRecordId);

            return new DefaultEntitlement(subscription.getId(), eventsStreamBuilder, this,
                                          blockingStateDao, subscriptionInternalApi, checker, notificationQueueService,
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
        if (eventsStreamForBaseSubscription.getCurrentBlockingAggregator().isBlockChange()) {
            throw new EntitlementApiException(new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, BlockingChecker.ACTION_CHANGE, BlockingChecker.TYPE_SUBSCRIPTION, eventsStreamForBaseSubscription.getSubscription().getId().toString()));
        }

        final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, eventsStreamForBaseSubscription.getSubscription().getStartDate(), eventsStreamForBaseSubscription.getInternalTenantContext());

        try {
            final InternalCallContext context = internalCallContextFactory.createInternalCallContext(callContext);
            final SubscriptionBase subscription = subscriptionInternalApi.createSubscription(bundleId, planPhaseSpecifier, requestedDate, context);

            return new DefaultEntitlement(subscription.getId(), eventsStreamBuilder, this,
                                          blockingStateDao, subscriptionInternalApi, checker, notificationQueueService,
                                          entitlementUtils, dateHelper, clock, internalCallContextFactory, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public List<EntitlementAOStatusDryRun> getDryRunStatusForChange(final UUID bundleId, final String targetProductName, final LocalDate effectiveDate, final TenantContext context) throws EntitlementApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        try {
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleFromId(bundleId, internalContext);
            final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, internalContext);

            final InternalTenantContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalTenantContext(bundle.getAccountId(), context);
            final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);
            return subscriptionInternalApi.getDryRunChangePlanStatus(baseSubscription.getId(), targetProductName, requestedDate, contextWithValidAccountRecordId);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public Entitlement getEntitlementForId(final UUID uuid, final TenantContext tenantContext) throws EntitlementApiException {
        final EventsStream eventsStream = eventsStreamBuilder.buildForEntitlement(uuid, tenantContext);
        return new DefaultEntitlement(eventsStream, eventsStreamBuilder, this,
                                      blockingStateDao, subscriptionInternalApi, checker, notificationQueueService,
                                      entitlementUtils, dateHelper, clock, internalCallContextFactory);
    }

    @Override
    public List<Entitlement> getAllEntitlementsForBundle(final UUID bundleId, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(tenantContext);
        return getAllEntitlementsForBundle(subscriptionInternalApi.getSubscriptionsForBundle(bundleId, context), tenantContext);
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
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(accountId, tenantContext);
        final Map<UUID, List<SubscriptionBase>> subscriptionsPerBundle = subscriptionInternalApi.getSubscriptionsForAccount(context);

        final List<Entitlement> result = new LinkedList<Entitlement>();
        for (final UUID bundleId : subscriptionsPerBundle.keySet()) {
            final List<Entitlement> entitlements = getAllEntitlementsForBundle(subscriptionsPerBundle.get(bundleId), tenantContext);
            result.addAll(entitlements);
        }
        return result;
    }

    private List<Entitlement> getAllEntitlementsForBundle(final List<SubscriptionBase> subscriptions, final TenantContext context) {
        return Lists.transform(subscriptions,
                               new Function<SubscriptionBase, Entitlement>() {
                                   @Override
                                   public Entitlement apply(final SubscriptionBase input) {
                                       try {
                                           return getEntitlementForId(input.getId(), context);
                                       } catch (EntitlementApiException e) {
                                           throw new RuntimeException("Failed to extract blocking state for subscription " + input.getId().toString());
                                       }
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

            final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleFromId(bundleId, contextWithValidAccountRecordId);
            final Account account = accountApi.getAccountById(bundle.getAccountId(), contextWithValidAccountRecordId);
            final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, contextWithValidAccountRecordId);
            final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(localEffectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);

            // STEPH TODO implement ability to pause in the future
            if (!dateHelper.isBeforeOrEqualsToday(effectiveDate, account.getTimeZone())) {
                throw new UnsupportedOperationException("Pausing with a future date has not been implemented yet");
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
            final BlockingState currentState = blockingStateDao.getBlockingStateForService(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, EntitlementService.ENTITLEMENT_SERVICE_NAME, contextWithValidAccountRecordId);
            if (currentState == null || currentState.getStateName().equals(ENT_STATE_CLEAR)) {
                // Nothing to do.
                return;
            }
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleFromId(bundleId, contextWithValidAccountRecordId);
            final Account account = accountApi.getAccountById(bundle.getAccountId(), contextWithValidAccountRecordId);
            final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, contextWithValidAccountRecordId);

            final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(localEffectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);

            // STEPH TODO implement ability to pause in the future
            if (!dateHelper.isBeforeOrEqualsToday(effectiveDate, account.getTimeZone())) {
                throw new UnsupportedOperationException("Resuming with a future date has not been implemented yet");
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
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.getActiveBundleForKey(externalKey, contextWithValidAccountRecordId);
            if (!bundle.getAccountId().equals(sourceAccountId)) {
                throw new EntitlementApiException(new SubscriptionBaseApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_KEY, externalKey));
            }
            final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundle.getId(), contextWithValidAccountRecordId);

            final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);
            final SubscriptionBaseBundle newBundle = subscriptionTransferApi.transferBundle(sourceAccountId, destAccountId, externalKey, requestedDate, true, cancelImm, context);

            final BlockingState newBlockingState = new DefaultBlockingState(bundle.getId(), BlockingStateType.SUBSCRIPTION_BUNDLE, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, requestedDate);
            entitlementUtils.setBlockingStateAndPostBlockingTransitionEvent(newBlockingState, contextWithValidAccountRecordId);

            return newBundle.getId();
        } catch (SubscriptionBaseTransferApiException e) {
            throw new EntitlementApiException(e);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }
}
