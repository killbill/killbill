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

package com.ning.billing.entitlement.engine.core;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountInternalApi;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.api.BlockingApiException;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.EntitlementApiException;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.block.BlockingChecker.BlockingAggregator;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

@Singleton
public class EventsStreamBuilder {

    private final AccountInternalApi accountInternalApi;
    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final BlockingChecker checker;
    private final BlockingStateDao blockingStateDao;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public EventsStreamBuilder(final AccountInternalApi accountInternalApi, final SubscriptionBaseInternalApi subscriptionInternalApi,
                               final BlockingChecker checker, final BlockingStateDao blockingStateDao,
                               final Clock clock, final InternalCallContextFactory internalCallContextFactory) {
        this.accountInternalApi = accountInternalApi;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public EventsStream refresh(final EventsStream eventsStream, final TenantContext tenantContext) throws EntitlementApiException {
        return buildForEntitlement(eventsStream.getSubscription().getId(), tenantContext);
    }

    public EventsStream buildForBaseSubscription(final UUID bundleId, final TenantContext tenantContext) throws EntitlementApiException {
        final SubscriptionBase baseSubscription;
        try {
            final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(tenantContext);
            baseSubscription = subscriptionInternalApi.getBaseSubscription(bundleId, internalTenantContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        return buildForEntitlement(baseSubscription.getId(), tenantContext);
    }

    public EventsStream buildForEntitlement(final UUID entitlementId, final TenantContext tenantContext) throws EntitlementApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(entitlementId, ObjectType.SUBSCRIPTION, tenantContext);
        return buildForEntitlement(entitlementId, internalTenantContext);
    }

    public EventsStream buildForEntitlement(final UUID entitlementId, final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final SubscriptionBaseBundle bundle;
        final SubscriptionBase subscription;
        final List<SubscriptionBase> allSubscriptionsForBundle;
        final SubscriptionBase baseSubscription;
        try {
            subscription = subscriptionInternalApi.getSubscriptionFromId(entitlementId, internalTenantContext);
            bundle = subscriptionInternalApi.getBundleFromId(subscription.getBundleId(), internalTenantContext);
            allSubscriptionsForBundle = subscriptionInternalApi.getSubscriptionsForBundle(subscription.getBundleId(), internalTenantContext);
            baseSubscription = Iterables.<SubscriptionBase>tryFind(allSubscriptionsForBundle,
                                                                   new Predicate<SubscriptionBase>() {
                                                                       @Override
                                                                       public boolean apply(final SubscriptionBase input) {
                                                                           return ProductCategory.BASE.equals(input.getLastActiveProduct().getCategory());
                                                                       }
                                                                   }).orNull(); // null for standalone subscriptions
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }

        return buildForEntitlement(bundle, baseSubscription, subscription, allSubscriptionsForBundle, internalTenantContext);
    }

    private EventsStream buildForEntitlement(final SubscriptionBaseBundle bundle,
                                             final SubscriptionBase baseSubscription,
                                             final SubscriptionBase subscription,
                                             final List<SubscriptionBase> allSubscriptionsForBundle,
                                             final InternalTenantContext internalTenantContext) throws EntitlementApiException {
        final Account account;
        try {
            account = accountInternalApi.getAccountById(bundle.getAccountId(), internalTenantContext);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }

        final List<BlockingState> subscriptionEntitlementStates = blockingStateDao.getBlockingHistoryForService(subscription.getId(), EntitlementService.ENTITLEMENT_SERVICE_NAME, internalTenantContext);
        final List<BlockingState> bundleEntitlementStates = blockingStateDao.getBlockingHistoryForService(bundle.getId(), EntitlementService.ENTITLEMENT_SERVICE_NAME, internalTenantContext);
        final List<BlockingState> accountEntitlementStates = blockingStateDao.getBlockingHistoryForService(account.getId(), EntitlementService.ENTITLEMENT_SERVICE_NAME, internalTenantContext);

        final BlockingAggregator blockingAggregator;
        try {
            blockingAggregator = checker.getBlockedStatus(subscription, internalTenantContext);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e);
        }

        return new EventsStream(account,
                                bundle,
                                subscriptionEntitlementStates,
                                bundleEntitlementStates,
                                accountEntitlementStates,
                                blockingAggregator,
                                baseSubscription,
                                subscription,
                                allSubscriptionsForBundle,
                                internalTenantContext,
                                clock.getUTCNow());
    }
}
