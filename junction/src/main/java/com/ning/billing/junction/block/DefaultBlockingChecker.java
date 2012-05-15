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
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
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
    private static final Object TYPE_ACCOUNT = "ACCOUNT";

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

    public BlockingAggregator getBlockedStateSubscriptionId(UUID subscriptionId) throws EntitlementUserApiException  {
        Subscription subscription = entitlementApi.getSubscriptionFromId(subscriptionId);
        return getBlockedStateSubscription(subscription);
    }

    public BlockingAggregator getBlockedStateSubscription(Subscription subscription) throws EntitlementUserApiException  {
        BlockingAggregator result = new BlockingAggregator();
        if(subscription != null) {
            BlockingState subscriptionState = subscription.getBlockingState();
            if(subscriptionState != null) {
                result.or(subscriptionState);
            }
            if(subscription.getBundleId() != null) {
                result.or(getBlockedStateBundleId(subscription.getBundleId()));
            } 
        }
        return result;
    }

    public BlockingAggregator getBlockedStateBundleId(UUID bundleId) throws EntitlementUserApiException  {
        SubscriptionBundle bundle = entitlementApi.getBundleFromId(bundleId);
        return getBlockedStateBundle(bundle);
    }

    public BlockingAggregator getBlockedStateBundle(SubscriptionBundle bundle)  {
        BlockingAggregator result = getBlockedStateAccountId(bundle.getAccountId());
        BlockingState bundleState = bundle.getBlockingState();
        if(bundleState != null) {
            result.or(bundleState);
        }
        return result;
    }

    public BlockingAggregator getBlockedStateAccountId(UUID accountId)  {
        BlockingAggregator result = new BlockingAggregator();
        if(accountId != null) {
            BlockingState accountState = dao.getBlockingStateFor(accountId);
            result.or(accountState);
        }
        return result;
    }

    public BlockingAggregator getBlockedStateAccount(Account account)  {
        if(account != null) {
            return getBlockedStateAccountId(account.getId());
        }
        return new BlockingAggregator();
    }
    @Override
    public void checkBlockedChange(Blockable blockable) throws BlockingApiException  {
        try {
            if(blockable instanceof Subscription && getBlockedStateSubscription((Subscription) blockable).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_CHANGE, TYPE_SUBSCRIPTION, blockable.getId().toString());
            } else if(blockable instanceof SubscriptionBundle &&  getBlockedStateBundle((SubscriptionBundle) blockable).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_CHANGE, TYPE_BUNDLE, blockable.getId().toString());
            } else if(blockable instanceof Account && getBlockedStateAccount((Account) blockable).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_CHANGE, TYPE_ACCOUNT, blockable.getId().toString());
            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }

    @Override
    public void checkBlockedEntitlement(Blockable blockable) throws BlockingApiException  {
        try {
            if(blockable instanceof Subscription && getBlockedStateSubscription((Subscription) blockable).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_ENTITLEMENT, TYPE_SUBSCRIPTION, blockable.getId().toString());
            } else if(blockable instanceof SubscriptionBundle &&  getBlockedStateBundle((SubscriptionBundle) blockable).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_ENTITLEMENT, TYPE_BUNDLE, blockable.getId().toString());
            } else if(blockable instanceof Account && getBlockedStateAccount((Account) blockable).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_ENTITLEMENT, TYPE_ACCOUNT, blockable.getId().toString());
            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }

    @Override
    public void checkBlockedBilling(Blockable blockable) throws BlockingApiException  {
        try {
            if(blockable instanceof Subscription && getBlockedStateSubscription((Subscription) blockable).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_BILLING, TYPE_SUBSCRIPTION, blockable.getId().toString());
            } else if(blockable instanceof SubscriptionBundle &&  getBlockedStateBundle((SubscriptionBundle) blockable).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_BILLING, TYPE_BUNDLE, blockable.getId().toString());
            } else if(blockable instanceof Account && getBlockedStateAccount((Account) blockable).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_BILLING, TYPE_ACCOUNT, blockable.getId().toString());
            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }


    @Override
    public void checkBlockedChange(UUID blockableId, Blockable.Type type) throws BlockingApiException  {
        try {
            if(type == Blockable.Type.SUBSCRIPTION && getBlockedStateSubscriptionId(blockableId).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_CHANGE, TYPE_SUBSCRIPTION, blockableId.toString());
            } else if(type == Blockable.Type.SUBSCRIPTION_BUNDLE  &&  getBlockedStateBundleId(blockableId).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_CHANGE, TYPE_BUNDLE, blockableId.toString());
            } else if(type == Blockable.Type.ACCOUNT  && getBlockedStateAccountId(blockableId).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_CHANGE, TYPE_ACCOUNT, blockableId.toString());

            } 
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }

    @Override
    public void checkBlockedEntitlement(UUID blockableId, Blockable.Type type) throws BlockingApiException  {
        try {
            if(type == Blockable.Type.SUBSCRIPTION && getBlockedStateSubscriptionId(blockableId).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_ENTITLEMENT, TYPE_SUBSCRIPTION, blockableId.toString());
            } else if(type == Blockable.Type.SUBSCRIPTION_BUNDLE  &&  getBlockedStateBundleId(blockableId).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_ENTITLEMENT, TYPE_BUNDLE, blockableId.toString());
            } else if(type == Blockable.Type.ACCOUNT  && getBlockedStateAccountId(blockableId).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_ENTITLEMENT, TYPE_ACCOUNT, blockableId.toString());
            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }

    @Override
    public void checkBlockedBilling(UUID blockableId, Blockable.Type type) throws BlockingApiException  {
        try {
            if(type == Blockable.Type.SUBSCRIPTION && getBlockedStateSubscriptionId(blockableId).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_BILLING, TYPE_SUBSCRIPTION, blockableId.toString());
            } else if(type == Blockable.Type.SUBSCRIPTION_BUNDLE  &&  getBlockedStateBundleId(blockableId).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_BILLING, TYPE_BUNDLE, blockableId.toString());
            } else if(type == Blockable.Type.ACCOUNT  && getBlockedStateAccountId(blockableId).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION,ACTION_BILLING, TYPE_ACCOUNT, blockableId.toString());
            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }




}
