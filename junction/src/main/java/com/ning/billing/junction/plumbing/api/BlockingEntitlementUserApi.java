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

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
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
    public BlockingEntitlementUserApi(@RealImplementation EntitlementUserApi userApi, BlockingApi blockingApi, BlockingChecker checker) {
        this.entitlementUserApi = userApi;
        this.blockingApi = blockingApi;
        this.checker = checker;
    }

    public SubscriptionBundle getBundleFromId(UUID id) throws EntitlementUserApiException {
        SubscriptionBundle bundle = entitlementUserApi.getBundleFromId(id);
        return new BlockingSubscriptionBundle(bundle, blockingApi);
    }

    public Subscription getSubscriptionFromId(UUID id) throws EntitlementUserApiException {
        Subscription subscription = entitlementUserApi.getSubscriptionFromId(id);
        return new BlockingSubscription(subscription, blockingApi, checker);
    }

    
    public SubscriptionBundle getBundleForKey(String bundleKey) throws EntitlementUserApiException {
        SubscriptionBundle bundle = entitlementUserApi.getBundleForKey(bundleKey);
        return new BlockingSubscriptionBundle(bundle, blockingApi);
    }

    public List<SubscriptionBundle> getBundlesForAccount(UUID accountId) {
        List<SubscriptionBundle> result = new ArrayList<SubscriptionBundle>();
        List<SubscriptionBundle> bundles = entitlementUserApi.getBundlesForAccount(accountId);
        for(SubscriptionBundle bundle : bundles) {
            result.add(new BlockingSubscriptionBundle(bundle, blockingApi));
        }
        return result;
    }

    public List<Subscription> getSubscriptionsForBundle(UUID bundleId) {
        List<Subscription> result = new ArrayList<Subscription>();
        List<Subscription> subscriptions = entitlementUserApi.getSubscriptionsForBundle(bundleId);
        for(Subscription subscription : subscriptions) {
            result.add(new BlockingSubscription(subscription, blockingApi, checker));
        }
        return result;
    }

    public List<Subscription> getSubscriptionsForKey(String bundleKey) {
        List<Subscription> result = new ArrayList<Subscription>();
        List<Subscription> subscriptions = entitlementUserApi.getSubscriptionsForKey(bundleKey);
        for(Subscription subscription : subscriptions) {
            result.add(new BlockingSubscription(subscription, blockingApi, checker));
        }
        return result;
    }

    public Subscription getBaseSubscription(UUID bundleId) throws EntitlementUserApiException {
        return new BlockingSubscription(entitlementUserApi.getBaseSubscription(bundleId), blockingApi, checker);
    }

    public SubscriptionBundle createBundleForAccount(UUID accountId, String bundleKey, CallContext context)
            throws EntitlementUserApiException {
        try {
           checker.checkBlockedChange(accountId, Blockable.Type.ACCOUNT);
           return new BlockingSubscriptionBundle(entitlementUserApi.createBundleForAccount(accountId, bundleKey, context), blockingApi);
        }catch (BlockingApiException e) {
            throw new EntitlementUserApiException(e, e.getCode(), e.getMessage());
        }
   }

    public Subscription createSubscription(UUID bundleId, PlanPhaseSpecifier spec, DateTime requestedDate,
            CallContext context) throws EntitlementUserApiException {
        try {
            checker.checkBlockedChange(bundleId, Blockable.Type.SUBSCRIPTION_BUNDLE);
            return new BlockingSubscription(entitlementUserApi.createSubscription(bundleId, spec, requestedDate, context), blockingApi, checker);
        }catch (BlockingApiException e) {
            throw new EntitlementUserApiException(e, e.getCode(), e.getMessage());
        }
    }

    public DateTime getNextBillingDate(UUID account) {
        return entitlementUserApi.getNextBillingDate(account);
    }

}
