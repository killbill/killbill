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
import com.ning.billing.util.callcontext.InternalTenantContext;

import com.google.inject.Inject;

public class DefaultBlockingChecker implements BlockingChecker {

    private static class BlockingAggregator {
        private boolean blockChange = false;
        private boolean blockEntitlement = false;
        private boolean blockBilling = false;

        public void or(final BlockingState state) {
            if (state == null) {
                return;
            }
            blockChange = blockChange || state.isBlockChange();
            blockEntitlement = blockEntitlement || state.isBlockEntitlement();
            blockBilling = blockBilling || state.isBlockBilling();
        }

        public void or(final BlockingAggregator state) {
            if (state == null) {
                return;
            }
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
    public DefaultBlockingChecker(final EntitlementUserApi entitlementApi, final BlockingStateDao dao) {
        this.entitlementApi = entitlementApi;
        this.dao = dao;
    }

    public BlockingAggregator getBlockedStateSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) throws EntitlementUserApiException {
        final Subscription subscription = entitlementApi.getSubscriptionFromId(subscriptionId, context.toTenantContext());
        return getBlockedStateSubscription(subscription, context);
    }

    public BlockingAggregator getBlockedStateSubscription(final Subscription subscription, final InternalTenantContext context) throws EntitlementUserApiException {
        final BlockingAggregator result = new BlockingAggregator();
        if (subscription != null) {
            final BlockingState subscriptionState = subscription.getBlockingState();
            if (subscriptionState != null) {
                result.or(subscriptionState);
            }
            if (subscription.getBundleId() != null) {
                result.or(getBlockedStateBundleId(subscription.getBundleId(), context));
            }
        }
        return result;
    }

    public BlockingAggregator getBlockedStateBundleId(final UUID bundleId, final InternalTenantContext context) throws EntitlementUserApiException {
        final SubscriptionBundle bundle = entitlementApi.getBundleFromId(bundleId, context.toTenantContext());
        return getBlockedStateBundle(bundle, context);
    }

    public BlockingAggregator getBlockedStateBundle(final SubscriptionBundle bundle, final InternalTenantContext context) {
        final BlockingAggregator result = getBlockedStateAccountId(bundle.getAccountId(), context);
        final BlockingState bundleState = bundle.getBlockingState();
        if (bundleState != null) {
            result.or(bundleState);
        }
        return result;
    }

    public BlockingAggregator getBlockedStateAccountId(final UUID accountId, final InternalTenantContext context) {
        final BlockingAggregator result = new BlockingAggregator();
        if (accountId != null) {
            final BlockingState accountState = dao.getBlockingStateFor(accountId, context);
            result.or(accountState);
        }
        return result;
    }

    public BlockingAggregator getBlockedStateAccount(final Account account, final InternalTenantContext context) {
        if (account != null) {
            return getBlockedStateAccountId(account.getId(), context);
        }
        return new BlockingAggregator();
    }

    @Override
    public void checkBlockedChange(final Blockable blockable, final InternalTenantContext context) throws BlockingApiException {
        try {
            if (blockable instanceof Subscription && getBlockedStateSubscription((Subscription) blockable, context).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_SUBSCRIPTION, blockable.getId().toString());
            } else if (blockable instanceof SubscriptionBundle && getBlockedStateBundle((SubscriptionBundle) blockable, context).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_BUNDLE, blockable.getId().toString());
            } else if (blockable instanceof Account && getBlockedStateAccount((Account) blockable, context).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_ACCOUNT, blockable.getId().toString());
            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }

    @Override
    public void checkBlockedEntitlement(final Blockable blockable, final InternalTenantContext context) throws BlockingApiException {
        try {
            if (blockable instanceof Subscription && getBlockedStateSubscription((Subscription) blockable, context).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_SUBSCRIPTION, blockable.getId().toString());
            } else if (blockable instanceof SubscriptionBundle && getBlockedStateBundle((SubscriptionBundle) blockable, context).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_BUNDLE, blockable.getId().toString());
            } else if (blockable instanceof Account && getBlockedStateAccount((Account) blockable, context).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_ACCOUNT, blockable.getId().toString());
            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }

    @Override
    public void checkBlockedBilling(final Blockable blockable, final InternalTenantContext context) throws BlockingApiException {
        try {
            if (blockable instanceof Subscription && getBlockedStateSubscription((Subscription) blockable, context).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_SUBSCRIPTION, blockable.getId().toString());
            } else if (blockable instanceof SubscriptionBundle && getBlockedStateBundle((SubscriptionBundle) blockable, context).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_BUNDLE, blockable.getId().toString());
            } else if (blockable instanceof Account && getBlockedStateAccount((Account) blockable, context).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_ACCOUNT, blockable.getId().toString());
            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }


    @Override
    public void checkBlockedChange(final UUID blockableId, final Blockable.Type type, final InternalTenantContext context) throws BlockingApiException {
        try {
            if (type == Blockable.Type.SUBSCRIPTION && getBlockedStateSubscriptionId(blockableId, context).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_SUBSCRIPTION, blockableId.toString());
            } else if (type == Blockable.Type.SUBSCRIPTION_BUNDLE && getBlockedStateBundleId(blockableId, context).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_BUNDLE, blockableId.toString());
            } else if (type == Blockable.Type.ACCOUNT && getBlockedStateAccountId(blockableId, context).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_ACCOUNT, blockableId.toString());

            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }

    @Override
    public void checkBlockedEntitlement(final UUID blockableId, final Blockable.Type type, final InternalTenantContext context) throws BlockingApiException {
        try {
            if (type == Blockable.Type.SUBSCRIPTION && getBlockedStateSubscriptionId(blockableId, context).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_SUBSCRIPTION, blockableId.toString());
            } else if (type == Blockable.Type.SUBSCRIPTION_BUNDLE && getBlockedStateBundleId(blockableId, context).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_BUNDLE, blockableId.toString());
            } else if (type == Blockable.Type.ACCOUNT && getBlockedStateAccountId(blockableId, context).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_ACCOUNT, blockableId.toString());
            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }

    @Override
    public void checkBlockedBilling(final UUID blockableId, final Blockable.Type type, final InternalTenantContext context) throws BlockingApiException {
        try {
            if (type == Blockable.Type.SUBSCRIPTION && getBlockedStateSubscriptionId(blockableId, context).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_SUBSCRIPTION, blockableId.toString());
            } else if (type == Blockable.Type.SUBSCRIPTION_BUNDLE && getBlockedStateBundleId(blockableId, context).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_BUNDLE, blockableId.toString());
            } else if (type == Blockable.Type.ACCOUNT && getBlockedStateAccountId(blockableId, context).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_ACCOUNT, blockableId.toString());
            }
        } catch (EntitlementUserApiException e) {
            throw new BlockingApiException(e, ErrorCode.values()[e.getCode()]);
        }
    }


}
