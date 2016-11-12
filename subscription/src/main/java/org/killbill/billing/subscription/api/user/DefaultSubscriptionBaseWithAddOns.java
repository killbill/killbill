/*
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.user;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultSubscriptionBaseWithAddOns implements SubscriptionBaseWithAddOns {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionBaseWithAddOns.class);

    private final UUID bundleId;
    private final List<SubscriptionBase> subscriptionBaseList;
    private final DateTime effectiveDate;

    public DefaultSubscriptionBaseWithAddOns(final UUID bundleId, final List<SubscriptionBase> subscriptionBaseList, final DateTime effectiveDate) {
        this.bundleId = bundleId;
        this.subscriptionBaseList = subscriptionBaseList;
        this.effectiveDate = effectiveDate;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public List<SubscriptionBase> getSubscriptionBaseList() {
        return subscriptionBaseList;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }
}
