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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

public class DefaultSubscription extends DefaultEntitlement implements Subscription {


    private final List<BlockingState> blockingStates;

    DefaultSubscription(final DefaultEntitlement entitlement, final List<BlockingState> blockingStates) {
        super(entitlement);
        this.blockingStates = blockingStates;
    }

    @Override
    public LocalDate getBillingStartDate() {
        return new LocalDate(subscriptionBase.getStartDate(), accountTimeZone);
    }

    @Override
    public LocalDate getBillingEndDate() {
        final DateTime futureOrCurrentEndDate = subscriptionBase.getEndDate() != null ? subscriptionBase.getEndDate() : subscriptionBase.getFutureEndDate();
        return futureOrCurrentEndDate != null ? new LocalDate(futureOrCurrentEndDate, accountTimeZone) : null;
    }

    @Override
    public LocalDate getChargedThroughDate() {
        return subscriptionBase.getChargedThroughDate() != null ? new LocalDate(subscriptionBase.getChargedThroughDate(), accountTimeZone) : null;
    }

    @Override
    public int getBCD() {
        // STEPH_ENT
        return 0;
    }

    @Override
    public Map<String, String> getCurrentStatesForService() {

        final Map<String, String> result = new HashMap<String, String>();
        if (blockingStates == null) {
            return result;
        }
        for (BlockingState cur : blockingStates) {
            result.put(cur.getService(), cur.getStateName());
        }
        return result;
    }
}
