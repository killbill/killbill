/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.entitlement.block;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.api.Blockable;
import org.killbill.billing.entitlement.api.BlockingApiException;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultBlockingChecker implements BlockingChecker {

    public static class DefaultBlockingAggregator implements BlockingAggregator {

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

        public void or(final DefaultBlockingAggregator state) {
            if (state == null) {
                return;
            }
            blockChange = blockChange || state.isBlockChange();
            blockEntitlement = blockEntitlement || state.isBlockEntitlement();
            blockBilling = blockBilling || state.isBlockBilling();
        }

        @Override
        public boolean isBlockChange() {
            return blockChange;
        }

        @Override
        public boolean isBlockEntitlement() {
            return blockEntitlement;
        }

        @Override
        public boolean isBlockBilling() {
            return blockBilling;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("DefaultBlockingAggregator{");
            sb.append("blockChange=").append(blockChange);
            sb.append(", blockEntitlement=").append(blockEntitlement);
            sb.append(", blockBilling=").append(blockBilling);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DefaultBlockingAggregator)) {
                return false;
            }

            final DefaultBlockingAggregator that = (DefaultBlockingAggregator) o;

            if (blockBilling != that.blockBilling) {
                return false;
            }
            if (blockChange != that.blockChange) {
                return false;
            }
            if (blockEntitlement != that.blockEntitlement) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = (blockChange ? 1 : 0);
            result = 31 * result + (blockEntitlement ? 1 : 0);
            result = 31 * result + (blockBilling ? 1 : 0);
            return result;
        }
    }

    private final SubscriptionBaseInternalApi subscriptionApi;
    private final BlockingStateDao dao;

    private final StatelessBlockingChecker statelessBlockingChecker = new StatelessBlockingChecker();

    @Inject
    public DefaultBlockingChecker(final SubscriptionBaseInternalApi subscriptionApi, final BlockingStateDao dao) {
        this.subscriptionApi = subscriptionApi;
        this.dao = dao;
    }

    private DefaultBlockingAggregator getBlockedStateSubscriptionId(final UUID subscriptionId, final DateTime upToDate, final InternalTenantContext context) throws BlockingApiException {
        try {
            final UUID bundleId = subscriptionApi.getBundleIdFromSubscriptionId(subscriptionId, context);
            return getBlockedStateSubscription(bundleId, subscriptionId, upToDate, context);
        } catch (final SubscriptionBaseApiException e) {
            throw new BlockingApiException(e, ErrorCode.fromCode(e.getCode()));
        }
    }

    private DefaultBlockingAggregator getBlockedStateSubscription(@Nullable final UUID bundleId, @Nullable final UUID subscriptionId, final DateTime upToDate, final InternalTenantContext context) throws BlockingApiException {
        final DefaultBlockingAggregator result = new DefaultBlockingAggregator();
        if (subscriptionId != null) {
            final DefaultBlockingAggregator subscriptionState = getBlockedStateForId(subscriptionId, BlockingStateType.SUBSCRIPTION, upToDate, context);
            if (subscriptionState != null) {
                result.or(subscriptionState);
            }
            if (bundleId != null) {
                // Recursive call to also fetch account state
                result.or(getBlockedStateBundleId(bundleId, upToDate, context));
            }
        }
        return result;
    }

    private DefaultBlockingAggregator getBlockedStateBundleId(final UUID bundleId, final DateTime upToDate, final InternalTenantContext context) throws BlockingApiException {
        try {
            final UUID accountId = subscriptionApi.getAccountIdFromBundleId(bundleId, context);
            return getBlockedStateBundle(accountId, bundleId, upToDate, context);
        } catch (final SubscriptionBaseApiException e) {
            throw new BlockingApiException(e, ErrorCode.fromCode(e.getCode()));
        }
    }

    private DefaultBlockingAggregator getBlockedStateBundle(final UUID accountId, final UUID bundleId, final DateTime upToDate, final InternalTenantContext context) {
        final DefaultBlockingAggregator result = getBlockedStateAccountId(accountId, upToDate, context);
        final DefaultBlockingAggregator bundleState = getBlockedStateForId(bundleId, BlockingStateType.SUBSCRIPTION_BUNDLE, upToDate, context);
        if (bundleState != null) {
            result.or(bundleState);
        }
        return result;
    }

    private DefaultBlockingAggregator getBlockedStateAccount(final Account account, final DateTime upToDate, final InternalTenantContext context) {
        if (account != null) {
            return getBlockedStateForId(account.getId(), BlockingStateType.ACCOUNT, upToDate, context);
        }
        return new DefaultBlockingAggregator();
    }

    private DefaultBlockingAggregator getBlockedStateAccountId(final UUID accountId, final DateTime upToDate, final InternalTenantContext context) {
        return getBlockedStateForId(accountId, BlockingStateType.ACCOUNT, upToDate, context);
    }

    private DefaultBlockingAggregator getBlockedStateForId(@Nullable final UUID blockableId, final BlockingStateType blockingStateType, final DateTime upToDate, final InternalTenantContext context) {
        // Last states across services
        final List<BlockingState> blockableState;
        if (blockableId != null) {
            blockableState = dao.getBlockingState(blockableId, blockingStateType, upToDate, context);
        } else {
            blockableState = ImmutableList.<BlockingState>of();
        }
        return statelessBlockingChecker.getBlockedState(blockableState);
    }

    @Override
    public BlockingAggregator getBlockedStatus(final UUID blockableId, final BlockingStateType type, final DateTime upToDate, final InternalTenantContext context) throws BlockingApiException {
        if (type == BlockingStateType.SUBSCRIPTION) {
            return getBlockedStateSubscriptionId(blockableId, upToDate, context);
        } else if (type == BlockingStateType.SUBSCRIPTION_BUNDLE) {
            return getBlockedStateBundleId(blockableId, upToDate, context);
        } else { // BlockingStateType.ACCOUNT {
            return getBlockedStateAccountId(blockableId, upToDate, context);
        }
    }

    @Override
    public BlockingAggregator getBlockedStatus(final List<BlockingState> accountEntitlementStates, final List<BlockingState> bundleEntitlementStates, final List<BlockingState> subscriptionEntitlementStates, final InternalTenantContext internalTenantContext) {
        return statelessBlockingChecker.getBlockedState(accountEntitlementStates, bundleEntitlementStates, subscriptionEntitlementStates);
    }

    @Override
    public void checkBlockedChange(final Blockable blockable, final DateTime upToDate, final InternalTenantContext context) throws BlockingApiException {
        if (blockable instanceof SubscriptionBase && getBlockedStateSubscription(((SubscriptionBase) blockable).getBundleId(), blockable.getId(), upToDate, context).isBlockChange()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_SUBSCRIPTION, blockable.getId().toString());
        } else if (blockable instanceof SubscriptionBaseBundle && getBlockedStateBundle(((SubscriptionBaseBundle) blockable).getAccountId(), blockable.getId(), upToDate, context).isBlockChange()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_BUNDLE, blockable.getId().toString());
        } else if (blockable instanceof Account && getBlockedStateAccount((Account) blockable, upToDate, context).isBlockChange()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_CHANGE, TYPE_ACCOUNT, blockable.getId().toString());
        }
    }

    @Override
    public void checkBlockedEntitlement(final Blockable blockable, final DateTime upToDate, final InternalTenantContext context) throws BlockingApiException {
        if (blockable instanceof SubscriptionBase && getBlockedStateSubscription(((SubscriptionBase) blockable).getBundleId(), blockable.getId(), upToDate, context).isBlockEntitlement()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_SUBSCRIPTION, blockable.getId().toString());
        } else if (blockable instanceof SubscriptionBaseBundle && getBlockedStateBundle(((SubscriptionBaseBundle) blockable).getAccountId(), blockable.getId(), upToDate, context).isBlockEntitlement()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_BUNDLE, blockable.getId().toString());
        } else if (blockable instanceof Account && getBlockedStateAccount((Account) blockable, upToDate, context).isBlockEntitlement()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_ENTITLEMENT, TYPE_ACCOUNT, blockable.getId().toString());
        }
    }

    @Override
    public void checkBlockedBilling(final Blockable blockable, final DateTime upToDate, final InternalTenantContext context) throws BlockingApiException {
        if (blockable instanceof SubscriptionBase && getBlockedStateSubscription(((SubscriptionBase) blockable).getBundleId(), blockable.getId(), upToDate, context).isBlockBilling()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_SUBSCRIPTION, blockable.getId().toString());
        } else if (blockable instanceof SubscriptionBaseBundle && getBlockedStateBundle(((SubscriptionBaseBundle) blockable).getAccountId(), blockable.getId(), upToDate, context).isBlockBilling()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_BUNDLE, blockable.getId().toString());
        } else if (blockable instanceof Account && getBlockedStateAccount((Account) blockable, upToDate, context).isBlockBilling()) {
            throw new BlockingApiException(ErrorCode.BLOCK_BLOCKED_ACTION, ACTION_BILLING, TYPE_ACCOUNT, blockable.getId().toString());
        }
    }
}
