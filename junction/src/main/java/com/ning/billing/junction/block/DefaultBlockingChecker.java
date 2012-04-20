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

package com.ning.billing.junction.block;

import java.util.UUID;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.dao.BlockingStateDao;

public class DefaultBlockingChecker implements BlockingChecker {
    
    private static class BlockingAggregator {
        private boolean blockChange = false;
        private boolean blockEntitlement= false;
        private boolean blockBilling = false;

        public void or(BlockingState state) {
            if (state == null) { return; }
            blockChange = blockChange || state.isBlockChange();
            blockEntitlement = blockEntitlement || state.isBlockEntitlement();
            blockBilling = blockBilling || state.isBlockBilling();
        }
        
        public void or(BlockingAggregator state) {
            if (state == null) { return; }
            blockChange = blockChange || state.isBlockChange();
            blockEntitlement = blockEntitlement || state.isBlockEntitlement();
            blockBilling = blockBilling || state.isBlockBilling();
        }
        
        public boolean isBlockChange() {
            return blockChange;
        }
        public boolean isBlockEntitlement() {
            return blockEntitlement;
        }
        public boolean isBlockBilling() {
            return blockBilling;
        }
       
    }

    private static final Object TYPE_SUBSCRIPTION = "Subscription";
    private static final Object TYPE_BUNDLE = "Bundle";
    private static final Object TYPE_ACCOUNT = "Account";

    private static final Object ACTION_CHANGE = "Change";
    private static final Object ACTION_ENTITLEMENT = "Entitlement";
    private static final Object ACTION_BILLING = "Billing";

    private final EntitlementUserApi entitlementApi;
    private final BlockingStateDao dao;

    @Inject
    public DefaultBlockingChecker(EntitlementUserApi entitlementApi, BlockingStateDao dao) {
        this.entitlementApi = entitlementApi;
        this.dao = dao;
    }

    public BlockingAggregator getBlockedStateSubscriptionId(UUID subscriptionId)  {
       Subscription subscription = entitlementApi.getSubscriptionFromId(subscriptionId);
       return getBlockedStateSubscription(subscription);
    }
    
    public BlockingAggregator getBlockedStateSubscription(Subscription subscription)  {
        BlockingAggregator result = new BlockingAggregator();
        if(subscription != null) {
            BlockingState subscriptionState = dao.getBlockingStateFor(subscription);
            if(subscriptionState != null) {
                result.or(subscriptionState);
            }
            if(subscription.getBundleId() != null) {
                SubscriptionBundle bundle = entitlementApi.getBundleFromId(subscription.getBundleId());
                result.or(getBlockedStateBundleId(subscription.getBundleId()));
            } 
        }
        return result;
    }

    public BlockingAggregator getBlockedStateBundleId(UUID bundleId)  {
        SubscriptionBundle bundle = entitlementApi.getBundleFromId(bundleId);
        return getBlockedStateBundle(bundle);
     }
     
    public BlockingAggregator getBlockedStateBundle(SubscriptionBundle bundle)  {
        BlockingAggregator result = getBlockedStateAccount(bundle.getAccountId());
        BlockingState bundleState = dao.getBlockingStateFor(bundle);
        if(bundleState != null) {
            result.or(bundleState);
        }
        return result;
    }

    public BlockingAggregator getBlockedStateAccount(UUID accountId)  {
        BlockingAggregator result = new BlockingAggregator();
        if(accountId != null) {
            BlockingState accountState = dao.getBlockingStateFor(accountId, Blockable.Type.ACCOUNT);
            result.or(accountState);
        }
        return result;
    }

    public BlockingAggregator getBlockedStateAccount(Account account)  {
        if(account != null) {
            return getBlockedStateAccount(account.getId());
        }
        return new BlockingAggregator();
    }
    @Override
    public void checkBlockedChange(Subscription subscription) throws BlockingApiException {
        if(getBlockedStateSubscription(subscription).isBlockChange()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_CHANGE, TYPE_SUBSCRIPTION, subscription.getId().toString());
        }
    }

    @Override
    public void checkBlockedChange(SubscriptionBundle bundle) throws BlockingApiException {
        if(getBlockedStateBundle(bundle).isBlockChange()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_CHANGE, TYPE_BUNDLE, bundle.getId().toString());
        }
    }

    @Override
    public void checkBlockedChange(Account account) throws BlockingApiException {
        if(getBlockedStateAccount(account).isBlockChange()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_CHANGE, TYPE_ACCOUNT, account.getId().toString());
        }
    }

    @Override
    public void checkBlockedEntitlement(Subscription subscription) throws BlockingApiException {
        if(getBlockedStateSubscription(subscription).isBlockEntitlement()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_ENTITLEMENT, TYPE_SUBSCRIPTION, subscription.getId().toString());
        }
    }

    @Override
    public void checkBlockedEntitlement(SubscriptionBundle bundle) throws BlockingApiException {
        if(getBlockedStateBundle(bundle).isBlockEntitlement()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_ENTITLEMENT, TYPE_BUNDLE, bundle.getId().toString());
        }
    }

    @Override
    public void checkBlockedEntitlement(Account account) throws BlockingApiException {
        if(getBlockedStateAccount(account).isBlockEntitlement()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_ENTITLEMENT, TYPE_ACCOUNT, account.getId().toString());
        }
    }

    @Override
    public void checkBlockedBilling(Subscription subscription) throws BlockingApiException {
        if(getBlockedStateSubscription(subscription).isBlockBilling()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_BILLING, TYPE_SUBSCRIPTION, subscription.getId().toString());
        }
    }

    @Override
    public void checkBlockedBilling(SubscriptionBundle bundle) throws BlockingApiException {
        if(getBlockedStateBundle(bundle).isBlockBilling()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_BILLING, TYPE_BUNDLE, bundle.getId().toString());
        }
    }

    @Override
    public void checkBlockedBilling(Account account) throws BlockingApiException {
        if(getBlockedStateAccount(account).isBlockBilling()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_BILLING, TYPE_ACCOUNT, account.getId().toString());
        }
    }

    
}
