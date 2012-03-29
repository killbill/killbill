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

package com.ning.billing.entitlement.api.overdue;

import java.util.List;
import java.util.UUID;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;

public class DefaultEntitlementOverdueApi implements EntitlementOverdueApi {
    private EntitlementDao dao;

    @Inject
    public DefaultEntitlementOverdueApi(EntitlementDao dao) {
        this.dao = dao;
    }

    @Override
    public Subscription getBaseSubscription(UUID bundleId) {
        List<Subscription> subscriptions = dao.getSubscriptions(bundleId);
        for(Subscription subscription: subscriptions) {
            if(subscription.getCurrentPlan().getProduct().getCategory() == ProductCategory.BASE) {
                return subscription;
            }
        }
        return null;
    }
}
