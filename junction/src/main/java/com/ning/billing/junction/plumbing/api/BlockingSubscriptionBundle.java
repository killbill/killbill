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

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.overdue.OverdueState;

public class BlockingSubscriptionBundle implements SubscriptionBundle {
    private final SubscriptionBundle subscriptionBundle;
    private final BlockingApi blockingApi; 
    
    private BlockingState blockingState = null;

    public BlockingSubscriptionBundle(SubscriptionBundle subscriptionBundle, BlockingApi blockingApi) {
        this.subscriptionBundle = subscriptionBundle;
        this.blockingApi = blockingApi;
    }

    public UUID getAccountId() {
        return subscriptionBundle.getAccountId();
    }

    public UUID getId() {
        return subscriptionBundle.getId();
    }

    public DateTime getStartDate() {
        return subscriptionBundle.getStartDate();
    }

    public String getKey() {
        return subscriptionBundle.getKey();
    }

    public OverdueState<SubscriptionBundle> getOverdueState() {
        return subscriptionBundle.getOverdueState();
    }

    @Override
    public BlockingState getBlockingState() {
        if(blockingState == null) {
            blockingState = blockingApi.getBlockingStateFor(this);
        }
        return blockingState;
    }

}
