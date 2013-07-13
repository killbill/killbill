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

package com.ning.billing.junction.plumbing.api;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.subscription.api.user.SubscriptionUserApiException;
import com.ning.billing.subscription.api.user.Subscription;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.subscription.api.user.SubscriptionStatusDryRun;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.junction.api.Type;
import com.ning.billing.junction.block.BlockingChecker;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;

import com.google.inject.Inject;

public class BlockingSubscriptionUserApi implements SubscriptionUserApi {

    private final SubscriptionUserApi subscriptionUserApi;
    private final BlockingInternalApi blockingApi;
    private final BlockingChecker checker;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public BlockingSubscriptionUserApi(@RealImplementation final SubscriptionUserApi userApi, final BlockingInternalApi blockingApi,
                                       final BlockingChecker checker, final InternalCallContextFactory internalCallContextFactory) {
        this.subscriptionUserApi = userApi;
        this.blockingApi = blockingApi;
        this.checker = checker;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public SubscriptionBundle getBundleFromId(final UUID id, final TenantContext context) throws SubscriptionUserApiException {
        final SubscriptionBundle bundle = subscriptionUserApi.getBundleFromId(id, context);
        return new BlockingSubscriptionBundle(bundle, blockingApi, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public Subscription getSubscriptionFromId(final UUID id, final TenantContext context) throws SubscriptionUserApiException {
        final Subscription subscription = subscriptionUserApi.getSubscriptionFromId(id, context);
        return new BlockingSubscription(subscription, blockingApi, checker, internalCallContextFactory.createInternalTenantContext(context), internalCallContextFactory);
    }

    @Override
    public SubscriptionBundle getBundleForAccountAndKey(final UUID accountId, final String bundleKey, final TenantContext context) throws SubscriptionUserApiException {
        final SubscriptionBundle bundle = subscriptionUserApi.getBundleForAccountAndKey(accountId, bundleKey, context);
        return new BlockingSubscriptionBundle(bundle, blockingApi, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<SubscriptionBundle> getBundlesForAccount(final UUID accountId, final TenantContext context) {
        final List<SubscriptionBundle> result = new ArrayList<SubscriptionBundle>();
        final List<SubscriptionBundle> bundles = subscriptionUserApi.getBundlesForAccount(accountId, context);
        for (final SubscriptionBundle bundle : bundles) {
            result.add(new BlockingSubscriptionBundle(bundle, blockingApi, internalCallContextFactory.createInternalTenantContext(context)));
        }
        return result;
    }

    @Override
    public List<SubscriptionBundle> getBundlesForKey(final String bundleKey, final TenantContext context)
            throws SubscriptionUserApiException {
        final List<SubscriptionBundle> result = new ArrayList<SubscriptionBundle>();
        final List<SubscriptionBundle> bundles = subscriptionUserApi.getBundlesForKey(bundleKey, context);
        for (final SubscriptionBundle bundle : bundles) {
            result.add(new BlockingSubscriptionBundle(bundle, blockingApi, internalCallContextFactory.createInternalTenantContext(context)));
        }
        return result;
    }

    @Override
    public List<Subscription> getSubscriptionsForBundle(final UUID bundleId, final TenantContext context) {
        final List<Subscription> result = new ArrayList<Subscription>();
        final List<Subscription> subscriptions = subscriptionUserApi.getSubscriptionsForBundle(bundleId, context);
        for (final Subscription subscription : subscriptions) {
            result.add(new BlockingSubscription(subscription, blockingApi, checker, internalCallContextFactory.createInternalTenantContext(context), internalCallContextFactory));
        }
        return result;
    }

    @Override
    public List<Subscription> getSubscriptionsForAccountAndKey(final UUID accountId, final String bundleKey, final TenantContext context) {
        final List<Subscription> result = new ArrayList<Subscription>();
        final List<Subscription> subscriptions = subscriptionUserApi.getSubscriptionsForAccountAndKey(accountId, bundleKey, context);
        for (final Subscription subscription : subscriptions) {
            result.add(new BlockingSubscription(subscription, blockingApi, checker, internalCallContextFactory.createInternalTenantContext(context), internalCallContextFactory));
        }
        return result;
    }

    @Override
    public List<SubscriptionStatusDryRun> getDryRunChangePlanStatus(final UUID subscriptionId, @Nullable final String productName,
                                                                    final DateTime requestedDate, final TenantContext context) throws SubscriptionUserApiException {
        return subscriptionUserApi.getDryRunChangePlanStatus(subscriptionId, productName, requestedDate, context);
    }

    @Override
    public Subscription getBaseSubscription(final UUID bundleId, final TenantContext context) throws SubscriptionUserApiException {
        return new BlockingSubscription(subscriptionUserApi.getBaseSubscription(bundleId, context), blockingApi, checker, internalCallContextFactory.createInternalTenantContext(context), internalCallContextFactory);
    }

    @Override
    public SubscriptionBundle createBundleForAccount(final UUID accountId, final String bundleKey, final CallContext context)
            throws SubscriptionUserApiException {
        try {
            final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
            checker.checkBlockedChange(accountId, Type.ACCOUNT, internalContext);
            return new BlockingSubscriptionBundle(subscriptionUserApi.createBundleForAccount(accountId, bundleKey, context), blockingApi, internalContext);
        } catch (BlockingApiException e) {
            throw new SubscriptionUserApiException(e, e.getCode(), e.getMessage());
        }
    }

    @Override
    public Subscription createSubscription(final UUID bundleId, final PlanPhaseSpecifier spec, final DateTime requestedDate,
                                           final CallContext context) throws SubscriptionUserApiException {
        try {
            final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
            checker.checkBlockedChange(bundleId, Type.SUBSCRIPTION_BUNDLE, internalContext);
            return new BlockingSubscription(subscriptionUserApi.createSubscription(bundleId, spec, requestedDate, context), blockingApi, checker,  internalContext, internalCallContextFactory);
        } catch (BlockingApiException e) {
            throw new SubscriptionUserApiException(e, e.getCode(), e.getMessage());
        }
    }

    @Override
    public DateTime getNextBillingDate(final UUID account, final TenantContext context) {
        return subscriptionUserApi.getNextBillingDate(account, context);
    }
}
