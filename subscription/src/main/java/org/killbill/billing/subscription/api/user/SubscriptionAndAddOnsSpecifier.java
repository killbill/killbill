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

import org.joda.time.DateTime;

public class SubscriptionAndAddOnsSpecifier {

    private final SubscriptionBaseBundle bundle;
    private final DateTime effectiveDate;
    private List<SubscriptionSpecifier> subscriptionSpecifiers;

    public SubscriptionAndAddOnsSpecifier(final SubscriptionBaseBundle bundle,
                                          final DateTime effectiveDate,
                                          final List<SubscriptionSpecifier> subscriptionSpecifiers) {
        this.bundle = bundle;
        this.effectiveDate = effectiveDate;
        this.subscriptionSpecifiers = subscriptionSpecifiers;
    }

    public SubscriptionBaseBundle getBundle() {
        return bundle;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public List<SubscriptionSpecifier> getSubscriptionSpecifiers() {
        return subscriptionSpecifiers;
    }

    public void setSubscriptionSpecifiers(final List<SubscriptionSpecifier> subscriptionSpecifiers) {
        this.subscriptionSpecifiers = subscriptionSpecifiers;
    }
}
