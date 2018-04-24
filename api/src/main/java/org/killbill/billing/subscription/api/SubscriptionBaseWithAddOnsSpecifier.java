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

package org.killbill.billing.subscription.api;

import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;

public class SubscriptionBaseWithAddOnsSpecifier {

    private final UUID bundleId;
    private final String bundleExternalKey;
    private final Iterable<EntitlementSpecifier> entitlementSpecifiers;
    private final LocalDate billingEffectiveDate;
    private final boolean isMigrated;

    public SubscriptionBaseWithAddOnsSpecifier(final UUID bundleId,
                                               final String bundleExternalKey,
                                               final Iterable<EntitlementSpecifier> entitlementSpecifiers,
                                               final LocalDate billingEffectiveDate,
                                               final boolean isMigrated) {
        this.bundleId = bundleId;
        this.bundleExternalKey = bundleExternalKey;
        this.entitlementSpecifiers = entitlementSpecifiers;
        this.billingEffectiveDate = billingEffectiveDate;
        this.isMigrated = isMigrated;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public String getBundleExternalKey() {
        return bundleExternalKey;
    }

    public Iterable<EntitlementSpecifier> getEntitlementSpecifiers() {
        return entitlementSpecifiers;
    }

    public LocalDate getBillingEffectiveDate() {
        return billingEffectiveDate;
    }

    public boolean isMigrated() {
        return isMigrated;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SubscriptionBaseWithAddOnsSpecifier{");
        sb.append("bundleId=").append(bundleId);
        sb.append(", bundleExternalKey='").append(bundleExternalKey).append('\'');
        sb.append(", entitlementSpecifiers=").append(entitlementSpecifiers);
        sb.append(", billingEffectiveDate=").append(billingEffectiveDate);
        sb.append(", isMigrated=").append(isMigrated);
        sb.append('}');
        return sb.toString();
    }
}
