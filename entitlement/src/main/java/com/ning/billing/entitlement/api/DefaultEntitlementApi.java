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
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBus.EventBusException;
import com.ning.billing.catalog.api.BillingActionPolicy;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.EntitlementTransitionType;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import com.ning.billing.subscription.api.transfer.SubscriptionBaseTransferApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;
import com.ning.billing.util.svcapi.subscription.SubscriptionBaseInternalApi;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

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

    @Inject
    public DefaultEntitlementApi(final PersistentBus eventBus, final InternalCallContextFactory internalCallContextFactory, final SubscriptionBaseTransferApi subscriptionTransferApi, final SubscriptionBaseInternalApi subscriptionInternalApi, final AccountInternalApi accountApi, final BlockingStateDao blockingStateDao, final Clock clock, final BlockingChecker checker) {
        this.eventBus = eventBus;
        this.internalCallContextFactory = internalCallContextFactory;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.subscriptionTransferApi = subscriptionTransferApi;
        this.accountApi = accountApi;
        this.clock = clock;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.dateHelper = new EntitlementDateHelper(accountApi, clock);
    }


    @Override
    public Entitlement createBaseEntitlement(final UUID accountId, final PlanPhaseSpecifier planPhaseSpecifier, final String externalKey, final LocalDate effectiveDate, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        try {
            final Account account = accountApi.getAccountById(accountId, contextWithValidAccountRecordId);
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.createBundleForAccount(accountId, externalKey, contextWithValidAccountRecordId);

            final DateTime referenceTime = clock.getUTCNow();
            final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, referenceTime, contextWithValidAccountRecordId);
            final SubscriptionBase subscription = subscriptionInternalApi.createSubscription(bundle.getId(), planPhaseSpecifier, requestedDate, contextWithValidAccountRecordId);
            return new DefaultEntitlement(dateHelper, subscription, accountId, bundle.getExternalKey(), EntitlementState.ACTIVE, null, account.getTimeZone(), this,
                                          internalCallContextFactory, blockingStateDao, clock, checker);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public Entitlement addEntitlement(final UUID bundleId, final PlanPhaseSpecifier planPhaseSpecifier, final LocalDate effectiveDate, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(callContext);
        try {
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleFromId(bundleId, context);
            final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, context);


            final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundle.getAccountId(), callContext);

            final Account account = accountApi.getAccountById(bundle.getAccountId(), context);
            final LocalDate baseEntitlementEffectiveEndDate = getEffectiveEndDate(bundle.getAccountId(), baseSubscription, account.getTimeZone(), contextWithValidAccountRecordId);
            // Check if there is a BP and if it is active
            final EntitlementState baseEntitlementState = getStateForEntitlement(baseEntitlementEffectiveEndDate, baseSubscription, account.getTimeZone(), contextWithValidAccountRecordId);
            if (baseSubscription.getCategory() != ProductCategory.BASE ||
                baseEntitlementState != EntitlementState.ACTIVE) {
                throw new EntitlementApiException(ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION, baseSubscription.getBundleId());
            }

            // Check if BP is blockedChange
            final BlockingAggregator blocking = checker.getBlockedStatus(baseSubscription, contextWithValidAccountRecordId);
            if (blocking.isBlockChange()) {
                throw new EntitlementApiException(new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, BlockingChecker.ACTION_CHANGE, BlockingChecker.TYPE_SUBSCRIPTION, baseSubscription.getId().toString()));
            }


            final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);
            final SubscriptionBase subscription = subscriptionInternalApi.createSubscription(baseSubscription.getBundleId(), planPhaseSpecifier, requestedDate, context);

            return new DefaultEntitlement(dateHelper, subscription, bundle.getAccountId(), bundle.getExternalKey(), EntitlementState.ACTIVE, null, account.getTimeZone(),
                                          this, internalCallContextFactory, blockingStateDao, clock, checker);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e);
        } catch (AccountApiException e) {
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
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(tenantContext);
        try {
            final SubscriptionBase subscription = subscriptionInternalApi.getSubscriptionFromId(uuid, context);
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleFromId(subscription.getBundleId(), context);

            final Account account = accountApi.getAccountById(bundle.getAccountId(), context);

            final LocalDate entitlementEffectiveEndDate = getEffectiveEndDate(bundle.getAccountId(), subscription, account.getTimeZone(), context);
            final EntitlementState entitlementState = getStateForEntitlement(entitlementEffectiveEndDate, subscription, account.getTimeZone(), context);


            return new DefaultEntitlement(dateHelper, subscription, bundle.getAccountId(), bundle.getExternalKey(), entitlementState, entitlementEffectiveEndDate, account.getTimeZone(),
                                          this, internalCallContextFactory, blockingStateDao, clock, checker);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public List<Entitlement> getAllEntitlementsForBundle(final UUID bundleId, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(tenantContext);
        try {
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleFromId(bundleId, context);
            return getAllEntitlementsForBundleId(bundleId, bundle.getAccountId(), bundle.getExternalKey(), context);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public List<Entitlement> getAllEntitlementsForAccountIdAndExternalKey(final UUID accountId, final String externalKey, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(tenantContext);
        try {
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleForAccountAndKey(accountId, externalKey, context);
            return getAllEntitlementsForBundleId(bundle.getId(), bundle.getAccountId(), bundle.getExternalKey(), context);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public List<Entitlement> getAllEntitlementsForAccountId(final UUID accountId, final TenantContext tenantContext) throws EntitlementApiException {

        final List<Entitlement> result = new LinkedList<Entitlement>();
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(tenantContext);
        final List<SubscriptionBaseBundle> bundles = subscriptionInternalApi.getBundlesForAccount(accountId, context);
        for (final SubscriptionBaseBundle bundle : bundles) {
            final List<Entitlement> entitlements = getAllEntitlementsForBundleId(bundle.getId(), bundle.getAccountId(), bundle.getExternalKey(), context);
            result.addAll(entitlements);
        }
        return result;
    }

    private List<Entitlement> getAllEntitlementsForBundleId(final UUID bundleId, final UUID accountId, final String externalKey, final InternalTenantContext context) throws EntitlementApiException {

        try {
            final Account account = accountApi.getAccountById(accountId, context);
            final List<SubscriptionBase> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(bundleId, context);
            final EntitlementApi thisEntitlementApi = this;
            return ImmutableList.<Entitlement>copyOf(Collections2.transform(subscriptions, new Function<SubscriptionBase, Entitlement>() {
                @Nullable
                @Override
                public Entitlement apply(@Nullable final SubscriptionBase input) {

                    final LocalDate effectiveEndDate = getEffectiveEndDate(accountId, input, account.getTimeZone(), context);

                    EntitlementState entitlementState;
                    try {
                        entitlementState = getStateForEntitlement(effectiveEndDate, input, account.getTimeZone(), context);
                    } catch (EntitlementApiException e) {
                        log.warn("Failed to extract blocking state for subscription " + input.getId().toString());
                        entitlementState = EntitlementState.CANCELLED;
                    }

                    return new DefaultEntitlement(dateHelper, input, accountId, externalKey,
                                                  entitlementState,
                                                  effectiveEndDate,
                                                  account.getTimeZone(),
                                                  thisEntitlementApi,
                                                  internalCallContextFactory, blockingStateDao, clock, checker);
                }
            }));
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }


    private LocalDate getEffectiveEndDate(final UUID accountId, final SubscriptionBase subscriptionBase, final DateTimeZone accountTimeZone, final InternalTenantContext context) {

        LocalDate result = null;

        final BlockingState subEntitlementState = blockingStateDao.getBlockingStateForService(subscriptionBase.getId(), EntitlementService.ENTITLEMENT_SERVICE_NAME, context);
        if (subEntitlementState != null && ENT_STATE_CANCELLED.equals(subEntitlementState.getStateName())) {
            result = new LocalDate(subEntitlementState.getEffectiveDate(), accountTimeZone);
        }

        final BlockingState bundleEntitlementState = blockingStateDao.getBlockingStateForService(subscriptionBase.getBundleId(), EntitlementService.ENTITLEMENT_SERVICE_NAME, context);
        if (bundleEntitlementState != null && ENT_STATE_CANCELLED.equals(bundleEntitlementState.getStateName())) {
            final LocalDate localDate = new LocalDate(bundleEntitlementState.getEffectiveDate(), accountTimeZone);
            result = (result == null) || (result.compareTo(localDate) < 0) ? result : localDate;
        }

        final BlockingState accountEntitlementState = blockingStateDao.getBlockingStateForService(accountId, EntitlementService.ENTITLEMENT_SERVICE_NAME, context);
        if (accountEntitlementState != null && ENT_STATE_CANCELLED.equals(accountEntitlementState.getStateName())) {
            final LocalDate localDate = new LocalDate(accountEntitlementState.getEffectiveDate(), accountTimeZone);
            result = (result == null) || (result.compareTo(localDate) < 0) ? result : localDate;
        }
        return result;
    }

    private EntitlementState getStateForEntitlement(final LocalDate entitlementEndDate, final SubscriptionBase subscriptionBase, final DateTimeZone accountTimeZone, final InternalTenantContext context) throws EntitlementApiException {

        // Current state for the ENTITLEMENT_SERVICE_NAME is set to cancelled
        if (entitlementEndDate != null &&
            entitlementEndDate.compareTo(new LocalDate(clock.getUTCNow(), accountTimeZone)) <= 0) {
            return EntitlementState.CANCELLED;
        }

        try {
            // Gather states across all services and check if one of them is set to 'blockEntitlement'
            BlockingAggregator blocking = checker.getBlockedStatus(subscriptionBase, context);
            return blocking != null && blocking.isBlockEntitlement() ? EntitlementState.BLOCKED : EntitlementState.ACTIVE;
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e);
        }
    }


    @Override
    public void pause(final UUID bundleId, final LocalDate localEffectiveDate, final CallContext context) throws EntitlementApiException {
        try {
            final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, context);
            final BlockingState currentState = blockingStateDao.getBlockingStateForService(bundleId, EntitlementService.ENTITLEMENT_SERVICE_NAME, contextWithValidAccountRecordId);
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

            final DefaultBlockingState state = new DefaultBlockingState(bundleId, BlockingStateType.BUNDLE, ENT_STATE_BLOCKED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, true, effectiveDate);
            blockingStateDao.setBlockingState(state, clock, contextWithValidAccountRecordId);

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
            final BlockingState currentState = blockingStateDao.getBlockingStateForService(bundleId, EntitlementService.ENTITLEMENT_SERVICE_NAME, contextWithValidAccountRecordId);
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

            final DefaultBlockingState state = new DefaultBlockingState(bundleId, BlockingStateType.BUNDLE, ENT_STATE_CLEAR, EntitlementService.ENTITLEMENT_SERVICE_NAME, false, false, false, effectiveDate);
            blockingStateDao.setBlockingState(state, clock, contextWithValidAccountRecordId);

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
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleForAccountAndKey(sourceAccountId, externalKey, contextWithValidAccountRecordId);
            final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundle.getId(), contextWithValidAccountRecordId);

            final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(effectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);
            final SubscriptionBaseBundle newBundle = subscriptionTransferApi.transferBundle(sourceAccountId, destAccountId, externalKey, requestedDate, true, cancelImm, context);

            blockingStateDao.setBlockingState(new DefaultBlockingState(bundle.getId(), BlockingStateType.BUNDLE, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, requestedDate), clock, contextWithValidAccountRecordId);

            return newBundle.getId();
        } catch (SubscriptionBaseTransferApiException e) {
            throw new EntitlementApiException(e);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }
}
