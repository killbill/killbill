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

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;

public class BlockingSubscriptionBundle implements SubscriptionBundle {
    private final SubscriptionBundle subscriptionBundle;
    private final BlockingInternalApi blockingApi;
    private final InternalTenantContext context;

    private BlockingState blockingState = null;

    public BlockingSubscriptionBundle(final SubscriptionBundle subscriptionBundle, final BlockingInternalApi blockingApi, final InternalTenantContext context) {
        this.subscriptionBundle = subscriptionBundle;
        this.blockingApi = blockingApi;
        this.context = context;
    }

    @Override
    public UUID getAccountId() {
        return subscriptionBundle.getAccountId();
    }

    @Override
    public UUID getId() {
        return subscriptionBundle.getId();
    }

    @Override
    public DateTime getCreatedDate() {
        return subscriptionBundle.getCreatedDate();
    }

    @Override
    public DateTime getUpdatedDate() {
        return subscriptionBundle.getUpdatedDate();
    }

    @Override
    public String getExternalKey() {
        return subscriptionBundle.getExternalKey();
    }

    @Override
    public OverdueState<SubscriptionBundle> getOverdueState() {
        return subscriptionBundle.getOverdueState();
    }

    @Override
    public BlockingState getBlockingState() {
        if (blockingState == null) {
            blockingState = blockingApi.getBlockingStateFor(this, context);
        }
        return blockingState;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BlockingSubscriptionBundle");
        sb.append("{blockingApi=").append(blockingApi);
        sb.append(", subscriptionBundle=").append(subscriptionBundle);
        sb.append(", blockingState=").append(blockingState);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BlockingSubscriptionBundle that = (BlockingSubscriptionBundle) o;

        if (blockingApi != null ? !blockingApi.equals(that.blockingApi) : that.blockingApi != null) {
            return false;
        }
        if (blockingState != null ? !blockingState.equals(that.blockingState) : that.blockingState != null) {
            return false;
        }
        if (subscriptionBundle != null ? !subscriptionBundle.equals(that.subscriptionBundle) : that.subscriptionBundle != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = subscriptionBundle != null ? subscriptionBundle.hashCode() : 0;
        result = 31 * result + (blockingApi != null ? blockingApi.hashCode() : 0);
        result = 31 * result + (blockingState != null ? blockingState.hashCode() : 0);
        return result;
    }
}
