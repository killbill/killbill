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

package com.ning.billing.entitlement.block;

import java.util.List;
import java.util.UUID;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.entitlement.api.BlockingApiException;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.subscription.SubscriptionBaseInternalApi;

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
    private static final Object TYPE_ACCOUNT = "Account";

    private static final Object ACTION_CHANGE = "Change";
    private static final Object ACTION_ENTITLEMENT = "Entitlement";
    private static final Object ACTION_BILLING = "Billing";

    private final SubscriptionBaseInternalApi subscriptionApi;
    private final BlockingStateDao dao;

    @Inject
    public DefaultBlockingChecker(final SubscriptionBaseInternalApi subscriptionApi, final BlockingStateDao dao) {
        this.subscriptionApi = subscriptionApi;
        this.dao = dao;
    }

    private BlockingAggregator getBlockedStateSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBase subscription = subscriptionApi.getSubscriptionFromId(subscriptionId, context);
        return getBlockedStateSubscription(subscription, context);
    }

    private BlockingAggregator getBlockedStateSubscription(final SubscriptionBase subscription, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final BlockingAggregator result = new BlockingAggregator();
        if (subscription != null) {
            final BlockingAggregator subscriptionState = getBlockedStateForId(subscription.getId(), context);
            if (subscriptionState != null) {
                result.or(subscriptionState);
            }
            if (subscription.getBundleId() != null) {
                // Recursive call to also fetch account state
                result.or(getBlockedStateBundleId(subscription.getBundleId(), context));
            }
        }
        return result;
    }


    private BlockingAggregator getBlockedStateBundleId(final UUID bundleId, final InternalTenantContext context) throws SubscriptionBaseApiException {
        final SubscriptionBaseBundle bundle = subscriptionApi.getBundleFromId(bundleId, context);
        return getBlockedStateBundle(bundle, context);
    }


    private BlockingAggregator getBlockedStateBundle(final SubscriptionBaseBundle bundle, final InternalTenantContext context) {
        final BlockingAggregator result = getBlockedStateAccountId(bundle.getAccountId(), context);
        final BlockingAggregator bundleState = getBlockedStateForId(bundle.getId(), context);
        if (bundleState != null) {
            result.or(bundleState);
        }
        return result;
    }

    private BlockingAggregator getBlockedStateAccount(final Account account, final InternalTenantContext context) {
        if (account != null) {
            return getBlockedStateForId(account.getId(), context);
        }
        return new BlockingAggregator();
    }

    private BlockingAggregator getBlockedStateAccountId(final UUID accountId, final InternalTenantContext context) {
        return getBlockedStateForId(accountId, context);
    }

    private BlockingAggregator getBlockedStateForId(final UUID blockableId, final InternalTenantContext context) {
        final BlockingAggregator result = new BlockingAggregator();
        if (blockableId != null) {
            final List<BlockingState> blockableState = dao.getBlockingState(blockableId, context);
            for (BlockingState cur : blockableState) {
                result.or(cur);
            }
        }
        return result;
    }

    @Override
    public void checkBlockedChange(final Blockable blockable, final InternalTenantContext context) throws BlockingApiException {
        try {
            if (blockable instanceof SubscriptionBase && getBlockedStateSubscription((SubscriptionBase) blockable, context).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_SUBSCRIPTION, blockable.getId().toString());
            } else if (blockable instanceof SubscriptionBaseBundle && getBlockedStateBundle((SubscriptionBaseBundle) blockable, context).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_BUNDLE, blockable.getId().toString());
            } else if (blockable instanceof Account && getBlockedStateAccount((Account) blockable, context).isBlockChange()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_ACCOUNT, blockable.getId().toString());
            }
        } catch (SubscriptionBaseApiException e) {
            throw new BlockingApiException(e, ErrorCode.fromCode(e.getCode()));
        }
    }

    @Override
    public void checkBlockedEntitlement(final Blockable blockable, final InternalTenantContext context) throws BlockingApiException {
        try {
            // STEPH_ENT
            if (blockable instanceof SubscriptionBase && getBlockedStateSubscription((SubscriptionBase) blockable, context).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_SUBSCRIPTION, blockable.getId().toString());
            } else if (blockable instanceof SubscriptionBaseBundle && getBlockedStateBundle((SubscriptionBaseBundle) blockable, context).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_BUNDLE, blockable.getId().toString());
            } else if (blockable instanceof Account && getBlockedStateAccount((Account) blockable, context).isBlockEntitlement()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_ACCOUNT, blockable.getId().toString());
            }
        } catch (SubscriptionBaseApiException e) {
            throw new BlockingApiException(e, ErrorCode.fromCode(e.getCode()));
        }
    }

    @Override
    public void checkBlockedBilling(final Blockable blockable, final InternalTenantContext context) throws BlockingApiException {
        try {
            if (blockable instanceof SubscriptionBase && getBlockedStateSubscription((SubscriptionBase) blockable, context).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_SUBSCRIPTION, blockable.getId().toString());
            } else if (blockable instanceof SubscriptionBaseBundle && getBlockedStateBundle((SubscriptionBaseBundle) blockable, context).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_BUNDLE, blockable.getId().toString());
            } else if (blockable instanceof Account && getBlockedStateAccount((Account) blockable, context).isBlockBilling()) {
                throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_ACCOUNT, blockable.getId().toString());
            }
        } catch (SubscriptionBaseApiException e) {
            throw new BlockingApiException(e, ErrorCode.fromCode(e.getCode()));
        }
    }
}
