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

package org.killbill.billing.entitlement.api;

import java.util.UUID;

import org.joda.time.LocalDate;

public class DefaultBaseEntitlementWithAddOnsSpecifier implements BaseEntitlementWithAddOnsSpecifier {

    private final UUID bundleId;
    private final String externalKey;
    private final Iterable<EntitlementSpecifier> entitlementSpecifier;
    private final LocalDate entitlementEffectiveDate;
    private final LocalDate billingEffectiveDate;
    private final boolean isMigrated;

    public DefaultBaseEntitlementWithAddOnsSpecifier(final UUID bundleId,
                                                     final String externalKey,
                                                     final Iterable<EntitlementSpecifier> entitlementSpecifier,
                                                     final LocalDate entitlementEffectiveDate,
                                                     final LocalDate billingEffectiveDate,
                                                     final boolean isMigrated) {
        this.bundleId = bundleId;
        this.externalKey = externalKey;
        this.entitlementSpecifier = entitlementSpecifier;
        this.entitlementEffectiveDate = entitlementEffectiveDate;
        this.billingEffectiveDate = billingEffectiveDate;
        this.isMigrated = isMigrated;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public Iterable<EntitlementSpecifier> getEntitlementSpecifier() {
        return entitlementSpecifier;
    }

    @Override
    public LocalDate getEntitlementEffectiveDate() {
        return entitlementEffectiveDate;
    }

    @Override
    public LocalDate getBillingEffectiveDate() {
        return billingEffectiveDate;
    }

    @Override
    public boolean isMigrated() {
        return isMigrated;
    }
}
