/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;

public class DefaultSubscriptionBaseWithAddOns implements SubscriptionBaseWithAddOns {

    private final SubscriptionBaseBundle bundle;
    private final List<SubscriptionBase> subscriptionBaseList;

    public DefaultSubscriptionBaseWithAddOns(final SubscriptionBaseBundle bundle, final List<SubscriptionBase> subscriptionBaseList) {
        this.bundle = bundle;
        this.subscriptionBaseList = subscriptionBaseList;
    }

    @Override
    public SubscriptionBaseBundle getBundle() {
        return bundle;
    }

    @Override
    public List<SubscriptionBase> getSubscriptionBaseList() {
        return subscriptionBaseList;
    }
}
