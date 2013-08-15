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
import com.ning.billing.catalog.api.BillingActionPolicy;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.subscription.api.SubscriptionBase;
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
    private final AccountInternalApi accountApi;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;
    private final BlockingChecker checker;
    private final BlockingStateDao blockingStateDao;
    private final EntitlementDateHelper dateHelper;

    @Inject
    public DefaultEntitlementApi(final InternalCallContextFactory internalCallContextFactory, final SubscriptionBaseInternalApi subscriptionInternalApi, final AccountInternalApi accountApi, final BlockingStateDao blockingStateDao, final Clock clock, final BlockingChecker checker) {
        this.internalCallContextFactory = internalCallContextFactory;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.accountApi = accountApi;
        this.clock = clock;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.dateHelper = new EntitlementDateHelper(accountApi, clock);
    }


    @Override
    public Entitlement createBaseEntitlement(final UUID accountId, final PlanPhaseSpecifier planPhaseSpecifier, final String externalKey, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        try {
            final Account account = accountApi.getAccountById(accountId, contextWithValidAccountRecordId);
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.createBundleForAccount(accountId, externalKey, contextWithValidAccountRecordId);
            final SubscriptionBase subscription = subscriptionInternalApi.createSubscription(bundle.getId(), planPhaseSpecifier, clock.getUTCNow(), contextWithValidAccountRecordId);
            return new DefaultEntitlement(dateHelper, subscription, accountId, bundle.getExternalKey(), EntitlementState.ACTIVE, null, account.getTimeZone(),
                                          internalCallContextFactory, blockingStateDao, clock, checker);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public Entitlement addEntitlement(final UUID bundleId, final PlanPhaseSpecifier planPhaseSpecifier, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(callContext);
        try {
            final SubscriptionBaseBundle bundle = subscriptionInternalApi.getBundleFromId(bundleId, context);
            final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, context);


            final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundle.getAccountId(), callContext);
            final BlockingState currentBaseState =  blockingStateDao.getBlockingStateForService(baseSubscription.getId(), EntitlementService.ENTITLEMENT_SERVICE_NAME, contextWithValidAccountRecordId);

            final Account account = accountApi.getAccountById(bundle.getAccountId(), context);

            // Check if there is a BP and if it is active
            final EntitlementState baseEntitlementState = getStateForEntitlement(baseSubscription, currentBaseState, account.getTimeZone(), contextWithValidAccountRecordId);
            if (baseSubscription.getCategory() != ProductCategory.BASE ||
                baseEntitlementState != EntitlementState.ACTIVE) {
                throw new EntitlementApiException(ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION, baseSubscription.getBundleId());
            }

            // Check if BP is blockedChange
            final BlockingAggregator blocking = checker.getBlockedStatus(baseSubscription, contextWithValidAccountRecordId);
            if (blocking.isBlockChange()) {
                throw new EntitlementApiException(new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, BlockingChecker.ACTION_CHANGE, BlockingChecker.TYPE_SUBSCRIPTION, baseSubscription.getId().toString()));
            }


            final DateTime requestedDate = dateHelper.fromNowAndReferenceTime(baseSubscription.getStartDate(), contextWithValidAccountRecordId);
            final SubscriptionBase subscription = subscriptionInternalApi.createSubscription(baseSubscription.getBundleId(), planPhaseSpecifier, requestedDate, context);
            return new DefaultEntitlement(dateHelper, subscription, bundle.getAccountId(), bundle.getExternalKey(), getStateForEntitlement(subscription, currentBaseState, account.getTimeZone(), context), null, account.getTimeZone(),
                                          internalCallContextFactory, blockingStateDao, clock, checker);
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
            final SubscriptionBase baseSubscription  = subscriptionInternalApi.getBaseSubscription(bundleId, internalContext);

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
            final BlockingState currentState =  blockingStateDao.getBlockingStateForService(subscription.getId(), EntitlementService.ENTITLEMENT_SERVICE_NAME, context);

            return new DefaultEntitlement(dateHelper, subscription, bundle.getAccountId(), bundle.getExternalKey(), getStateForEntitlement(subscription, currentState, account.getTimeZone(), context), currentState, account.getTimeZone(),
                                          internalCallContextFactory, blockingStateDao, clock, checker);
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
            return ImmutableList.<Entitlement>copyOf(Collections2.transform(subscriptions, new Function<SubscriptionBase, Entitlement>() {
                @Nullable
                @Override
                public Entitlement apply(@Nullable final SubscriptionBase input) {

                    final BlockingState currentState =  blockingStateDao.getBlockingStateForService(input.getId(), EntitlementService.ENTITLEMENT_SERVICE_NAME, context);

                    return new DefaultEntitlement(dateHelper, input, accountId, externalKey,
                                                  getStateForEntitlement(input, currentState, account.getTimeZone(), context),
                                                  currentState,
                                                  account.getTimeZone(),
                                                  internalCallContextFactory, blockingStateDao, clock, checker);
                }
            }));
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }


    private EntitlementState getStateForEntitlement(final SubscriptionBase subscriptionBase, final BlockingState currentState, final DateTimeZone accountTimeZone, final InternalTenantContext context) {

        // Current state for the ENTITLEMENT_SERVICE_NAME is set to cancelled
        if (currentState != null &&
            currentState.getStateName().equals(ENT_STATE_CANCELLED) &&
                dateHelper.isBeforeOrEqualsNow(currentState.getEffectiveDate(), accountTimeZone)) {
            return EntitlementState.CANCELLED;
        }

        try {
            // Gather states across all services and check if one of them is set to 'blockEntitlement'
            BlockingAggregator blocking = checker.getBlockedStatus(subscriptionBase, context);
            return blocking != null && blocking.isBlockEntitlement() ? EntitlementState.BLOCKED : EntitlementState.ACTIVE;
        } catch (BlockingApiException e) {
            log.warn("Failed to extract blocking state for subscription " + subscriptionBase.getId().toString());
           return null;
        }
    }


    @Override
    public void pause(final UUID bundleId, final LocalDate localEffectiveDate, final CallContext context) throws EntitlementApiException {
        try {
            final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, context);
            final BlockingState currentState =  blockingStateDao.getBlockingStateForService(bundleId, EntitlementService.ENTITLEMENT_SERVICE_NAME, contextWithValidAccountRecordId);
            if (currentState != null && currentState.getStateName().equals(ENT_STATE_BLOCKED)) {
                throw new EntitlementApiException(ErrorCode.ENT_ALREADY_BLOCKED, bundleId);
            }
            final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, contextWithValidAccountRecordId);
            final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(localEffectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);

            blockingStateDao.setBlockingState(new DefaultBlockingState(bundleId, BlockingStateType.BUNDLE, ENT_STATE_BLOCKED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, true, effectiveDate), clock, contextWithValidAccountRecordId);

        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public void resume(final UUID bundleId, final LocalDate localEffectiveDate, final CallContext context) throws EntitlementApiException {
        try {
            final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, context);
            final BlockingState currentState =  blockingStateDao.getBlockingStateForService(bundleId, EntitlementService.ENTITLEMENT_SERVICE_NAME, contextWithValidAccountRecordId);
            if (currentState == null || currentState.getStateName().equals(ENT_STATE_CLEAR)) {
                // Nothing to do.
                return;
            }
            final SubscriptionBase baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, contextWithValidAccountRecordId);

            final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(localEffectiveDate, baseSubscription.getStartDate(), contextWithValidAccountRecordId);

            blockingStateDao.setBlockingState(new DefaultBlockingState(bundleId, BlockingStateType.BUNDLE, ENT_STATE_CLEAR, EntitlementService.ENTITLEMENT_SERVICE_NAME, false, false, false, effectiveDate), clock, contextWithValidAccountRecordId);

        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public void block(final UUID bundleId, final String serviceName, final LocalDate effectiveDate, final boolean blockBilling, final boolean blockEntitlement, final boolean blockChange, final CallContext context) throws EntitlementApiException {
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, context);

    }

    @Override
    public void unblock(final UUID bundleId, final String serviceName, final LocalDate effectiveDate, final CallContext context) throws EntitlementApiException {
    }


    @Override
    public UUID transferEntitlements(final UUID sourceAccountId, final UUID destAccountId, final String externalKey, final LocalDate effectiveDate, final CallContext context) throws EntitlementApiException {
        return null;
    }

    @Override
    public UUID transferEntitlementsOverrideBillingPolicy(final UUID sourceAccountId, final UUID destAccountId, final String externalKey, final LocalDate effectiveDate, final BillingActionPolicy billingPolicy, final CallContext context) throws EntitlementApiException {
        return null;
    }



}
