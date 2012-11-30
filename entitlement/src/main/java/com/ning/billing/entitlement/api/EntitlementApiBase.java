/*
 * Copyright 2010-2012 Ning, Inc.
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

import java.util.ArrayList;
import java.util.List;

import com.ning.billing.entitlement.api.user.DefaultSubscriptionApiService;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.util.clock.Clock;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class EntitlementApiBase {

    protected final EntitlementDao dao;

    protected final SubscriptionApiService apiService;
    protected final Clock clock;

    public EntitlementApiBase(final EntitlementDao dao, final SubscriptionApiService apiService, final Clock clock) {
        this.dao = dao;
        this.apiService = apiService;
        this.clock = clock;
    }

    protected List<Subscription> createSubscriptionsForApiUse(final List<Subscription> internalSubscriptions) {
        return new ArrayList<Subscription>(Collections2.transform(internalSubscriptions, new Function<Subscription, Subscription>() {
            @Override
            public Subscription apply(final Subscription subscription) {
                return createSubscriptionForApiUse((SubscriptionData) subscription);
            }
        }));
    }

    protected Subscription createSubscriptionForApiUse(final Subscription internalSubscription) {
        return new SubscriptionData((SubscriptionData) internalSubscription, apiService, clock);
    }
}
