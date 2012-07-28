/*
 * Copyright 2010-2011 Ning, Inc.
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

import com.google.inject.Inject;

import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionStatusDryRun;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.junction.block.BlockingChecker;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.glue.RealImplementation;

public class BlockingEntitlementUserApi implements EntitlementUserApi {
    private final EntitlementUserApi entitlementUserApi;
    private final BlockingApi blockingApi;
    private final BlockingChecker checker;

    @Inject
    public BlockingEntitlementUserApi(@RealImplementation final EntitlementUserApi userApi, final BlockingApi blockingApi, final BlockingChecker checker) {
        this.entitlementUserApi = userApi;
        this.blockingApi = blockingApi;
        this.checker = checker;
    }

    @Override
    public SubscriptionBundle getBundleFromId(final UUID id) throws EntitlementUserApiException {
        final SubscriptionBundle bundle = entitlementUserApi.getBundleFromId(id);
        return new BlockingSubscriptionBundle(bundle, blockingApi);
    }

    @Override
    public Subscription getSubscriptionFromId(final UUID id) throws EntitlementUserApiException {
        final Subscription subscription = entitlementUserApi.getSubscriptionFromId(id);
        return new BlockingSubscription(subscription, blockingApi, checker);
    }

    @Override
    public SubscriptionBundle getBundleForAccountAndKey(final UUID accountId, final String bundleKey) throws EntitlementUserApiException {
        final SubscriptionBundle bundle = entitlementUserApi.getBundleForAccountAndKey(accountId, bundleKey);
        return new BlockingSubscriptionBundle(bundle, blockingApi);
    }

    @Override
    public List<SubscriptionBundle> getBundlesForAccount(final UUID accountId) {
        final List<SubscriptionBundle> result = new ArrayList<SubscriptionBundle>();
        final List<SubscriptionBundle> bundles = entitlementUserApi.getBundlesForAccount(accountId);
        for (final SubscriptionBundle bundle : bundles) {
            result.add(new BlockingSubscriptionBundle(bundle, blockingApi));
        }
        return result;
    }

    @Override
    public List<Subscription> getSubscriptionsForBundle(final UUID bundleId) {
        final List<Subscription> result = new ArrayList<Subscription>();
        final List<Subscription> subscriptions = entitlementUserApi.getSubscriptionsForBundle(bundleId);
        for (final Subscription subscription : subscriptions) {
            result.add(new BlockingSubscription(subscription, blockingApi, checker));
        }
        return result;
    }

    @Override
    public List<Subscription> getSubscriptionsForAccountAndKey(final UUID accountId, final String bundleKey) {
        final List<Subscription> result = new ArrayList<Subscription>();
        final List<Subscription> subscriptions = entitlementUserApi.getSubscriptionsForAccountAndKey(accountId, bundleKey);
        for (final Subscription subscription : subscriptions) {
            result.add(new BlockingSubscription(subscription, blockingApi, checker));
        }
        return result;
    }

    @Override
    public List<SubscriptionStatusDryRun> getDryRunChangePlanStatus(final UUID subscriptionId, @Nullable final String productName, final DateTime requestedDate)
            throws EntitlementUserApiException {
        return entitlementUserApi.getDryRunChangePlanStatus(subscriptionId, productName, requestedDate);
    }

    @Override
    public Subscription getBaseSubscription(final UUID bundleId) throws EntitlementUserApiException {
        return new BlockingSubscription(entitlementUserApi.getBaseSubscription(bundleId), blockingApi, checker);
    }

    @Override
    public SubscriptionBundle createBundleForAccount(final UUID accountId, final String bundleKey, final CallContext context)
            throws EntitlementUserApiException {
        try {
            checker.checkBlockedChange(accountId, Blockable.Type.ACCOUNT);
            return new BlockingSubscriptionBundle(entitlementUserApi.createBundleForAccount(accountId, bundleKey, context), blockingApi);
        } catch (BlockingApiException e) {
            throw new EntitlementUserApiException(e, e.getCode(), e.getMessage());
        }
    }

    @Override
    public Subscription createSubscription(final UUID bundleId, final PlanPhaseSpecifier spec, final DateTime requestedDate,
                                           final CallContext context) throws EntitlementUserApiException {
        try {
            checker.checkBlockedChange(bundleId, Blockable.Type.SUBSCRIPTION_BUNDLE);
            return new BlockingSubscription(entitlementUserApi.createSubscription(bundleId, spec, requestedDate, context), blockingApi, checker);
        } catch (BlockingApiException e) {
            throw new EntitlementUserApiException(e, e.getCode(), e.getMessage());
        }
    }

    @Override
    public DateTime getNextBillingDate(final UUID account) {
        return entitlementUserApi.getNextBillingDate(account);
    }
}
