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

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.subscription.api.user.Subscription;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.subscription.api.user.SubscriptionState;
import com.ning.billing.subscription.api.user.SubscriptionUserApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.subscription.SubscriptionInternalApi;
import com.ning.billing.util.timezone.DateAndTimeZoneContext;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class DefaultEntitlementApi implements EntitlementApi {

    private final BlockingStateDao dao;
    private final SubscriptionInternalApi subscriptionInternalApi;
    private final AccountInternalApi accountApi;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;
    private final BlockingChecker checker;

    @Inject
    public DefaultEntitlementApi(final BlockingStateDao dao, final InternalCallContextFactory internalCallContextFactory, final SubscriptionInternalApi subscriptionInternalApi, final AccountInternalApi accountApi, final Clock clock, final BlockingChecker checker) {
        this.dao = dao;
        this.internalCallContextFactory = internalCallContextFactory;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.accountApi = accountApi;
        this.clock = clock;
        this.checker = checker;
    }


    @Override
    public Entitlement createBaseEntitlement(final UUID accountId, final PlanPhaseSpecifier planPhaseSpecifier, final String externalKey, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(callContext);
        try {
            final SubscriptionBundle bundle = subscriptionInternalApi.createBundleForAccount(accountId, externalKey, context);
            final Subscription subscription = subscriptionInternalApi.createSubscription(bundle.getId(), planPhaseSpecifier, clock.getUTCNow(), context);
            return new DefaultEntitlement(accountApi, subscriptionInternalApi, subscription, accountId, internalCallContextFactory, clock, checker);
        } catch (SubscriptionUserApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public Entitlement addEntitlement(final UUID baseSubscriptionId, final PlanPhaseSpecifier planPhaseSpecifier, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(callContext);
        try {
            final Subscription baseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscriptionId, context);
            if (baseSubscription.getCategory() != ProductCategory.BASE ||
                baseSubscription.getState() != SubscriptionState.ACTIVE) {
                throw new EntitlementApiException(new SubscriptionUserApiException(ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION, baseSubscription.getBundleId()));
            }

            final SubscriptionBundle bundle = subscriptionInternalApi.getBundleFromId(baseSubscription.getBundleId(), context);
            final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(bundle.getAccountId(), callContext);

            final DateTime requestedDate = fromNowAndReferenceTime(baseSubscription.getStartDate(), contextWithValidAccountRecordId);
            final Subscription subscription = subscriptionInternalApi.createSubscription(baseSubscription.getBundleId(), planPhaseSpecifier, requestedDate, context);
            return new DefaultEntitlement(accountApi, subscriptionInternalApi, subscription, bundle.getAccountId(), internalCallContextFactory, clock, checker);
        } catch (SubscriptionUserApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public Entitlement getEntitlementFromId(final UUID uuid, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(tenantContext);
        try {
            final Subscription subscription = subscriptionInternalApi.getSubscriptionFromId(uuid, context);
            final SubscriptionBundle bundle = subscriptionInternalApi.getBundleFromId(subscription.getBundleId(), context);
            return new DefaultEntitlement(accountApi, subscriptionInternalApi, subscription, bundle.getAccountId(), internalCallContextFactory, clock, checker);
        } catch (SubscriptionUserApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public List<Entitlement> getAllEntitlementFromBaseId(final UUID baseSubscriptionId, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(tenantContext);
        try {
            final Subscription baseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscriptionId, context);
            final SubscriptionBundle bundle = subscriptionInternalApi.getBundleFromId(baseSubscription.getBundleId(), context);
            return getAllEntitlementFromBundleId(baseSubscription.getBundleId(), bundle.getAccountId(), context);
        } catch (SubscriptionUserApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public List<Entitlement> getAllEntitlementForAccountIdAndExternalKey(final UUID accountId, final String externalKey, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(tenantContext);

        try {
            final SubscriptionBundle bundle = subscriptionInternalApi.getBundleForAccountAndKey(accountId, externalKey, context);
            return getAllEntitlementFromBundleId(bundle.getId(), bundle.getAccountId(), context);
        } catch (SubscriptionUserApiException e) {
            throw new EntitlementApiException(e);
        }
    }


    private List<Entitlement> getAllEntitlementFromBundleId(final UUID bundleId, final UUID accountId, final InternalTenantContext context) throws EntitlementApiException {
        final List<Subscription> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(bundleId, context);
        return ImmutableList.<Entitlement>copyOf(Collections2.transform(subscriptions, new Function<Subscription, Entitlement>() {
            @Nullable
            @Override
            public Entitlement apply(@Nullable final Subscription input) {
                return new DefaultEntitlement(accountApi, subscriptionInternalApi, input, accountId, internalCallContextFactory, clock, checker);
            }
        }));
    }


    @Override
    public List<Entitlement> getAllEntitlementFromAccountId(final UUID accountId, final TenantContext tenantContext) throws EntitlementApiException {

        final List<Entitlement> result = new LinkedList<Entitlement>();
        final InternalTenantContext context = internalCallContextFactory.createInternalTenantContext(tenantContext);
        final List<SubscriptionBundle> bundles = subscriptionInternalApi.getBundlesForAccount(accountId, context);
        for (final SubscriptionBundle bundle : bundles) {
            final List<Entitlement> entitlements = getAllEntitlementFromBundleId(bundle.getId(), bundle.getAccountId(), context);
            result.addAll(entitlements);
        }
        return result;
    }

    @Override
    public List<BlockingState> getBlockingHistory(final UUID overdueableId, final TenantContext context) {
        return dao.getBlockingHistoryFor(overdueableId, internalCallContextFactory.createInternalTenantContext(context));
    }

    private DateTime fromNowAndReferenceTime(final DateTime subscriptionStartDate, final InternalCallContext callContext) throws EntitlementApiException {
        try {
            final Account account = accountApi.getAccountByRecordId(callContext.getAccountRecordId(), callContext);
            final DateAndTimeZoneContext timeZoneContext = new DateAndTimeZoneContext(subscriptionStartDate, account.getTimeZone(), clock);
            return timeZoneContext.computeUTCDateTimeFromNow();
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

}
