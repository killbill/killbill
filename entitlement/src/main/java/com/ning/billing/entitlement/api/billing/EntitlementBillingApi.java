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

package com.ning.billing.entitlement.api.billing;

import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.entitlement.api.user.ISubscription;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.IEntitlementDao;
import com.ning.billing.util.clock.IClock;

public class EntitlementBillingApi implements IEntitlementBillingApi {

    private final IClock clock;
    private final IEntitlementDao dao;

    @Inject
    public EntitlementBillingApi(IClock clock, IEntitlementDao dao) {
        super();
        this.clock = clock;
        this.dao = dao;
    }

    @Override
    public List<IAccount> getActiveAccounts() {
        return null;
    }

    @Override
    public SortedSet<IBillingEvent> getBillingEventsForSubscription(
            UUID subscriptionId) {
        return null;
    }

    @Override
    public void setChargedThroughDate(UUID subscriptionId, DateTime ctd) {
        Subscription subscription = (Subscription) dao.getSubscriptionFromId(subscriptionId);
        if (subscription == null) {
            new EntitlementBillingApiException(String.format("Unknwon subscription %s", subscriptionId));
        }

        Subscription updatedSubscription = new SubscriptionBuilder()
            .setId(subscription.getId())
            .setBundleId(subscription.getBundleId())
            .setStartDate(subscription.getStartDate())
            .setBundleStartDate(subscription.getBundleStartDate())
            .setChargedThroughDate(ctd)
            .setPaidThroughDate(subscription.getPaidThroughDate())
            .setActiveVersion(subscription.getActiveVersion())
            .setCategory(subscription.getCategory())
            .build();

        dao.updateSubscription(updatedSubscription);
    }

}
