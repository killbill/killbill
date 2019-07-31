/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

    private final Iterable<EntitlementSpecifier> entitlementSpecifier;
    private final LocalDate entitlementEffectiveDate;
    private final LocalDate billingEffectiveDate;
    private final boolean isMigrated;

    // Maybe populated after create or transfer
    private UUID bundleId;
    private String bundleExternalKey;

    public DefaultBaseEntitlementWithAddOnsSpecifier(final BaseEntitlementWithAddOnsSpecifier input) {
        this(input.getBundleId(),
             input.getBundleExternalKey(),
             input.getEntitlementSpecifier(),
             input.getEntitlementEffectiveDate(),
             input.getBillingEffectiveDate(),
             input.isMigrated());
    }

    public DefaultBaseEntitlementWithAddOnsSpecifier(final UUID bundleId,
                                                     final String bundleExternalKey,
                                                     final Iterable<EntitlementSpecifier> entitlementSpecifier,
                                                     final LocalDate entitlementEffectiveDate,
                                                     final LocalDate billingEffectiveDate,
                                                     final boolean isMigrated) {
        this.bundleId = bundleId;
        this.bundleExternalKey = bundleExternalKey;
        this.entitlementSpecifier = entitlementSpecifier;
        this.entitlementEffectiveDate = entitlementEffectiveDate;
        this.billingEffectiveDate = billingEffectiveDate;
        this.isMigrated = isMigrated;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    public void setBundleId(final UUID bundleId) {
        this.bundleId = bundleId;
    }

    @Override
    public String getBundleExternalKey() {
        return bundleExternalKey;
    }

    public void setBundleExternalKey(final String bundleExternalKey) {
        this.bundleExternalKey = bundleExternalKey;
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultBaseEntitlementWithAddOnsSpecifier{");
        sb.append("entitlementSpecifier=").append(entitlementSpecifier);
        sb.append(", entitlementEffectiveDate=").append(entitlementEffectiveDate);
        sb.append(", billingEffectiveDate=").append(billingEffectiveDate);
        sb.append(", isMigrated=").append(isMigrated);
        sb.append(", bundleId=").append(bundleId);
        sb.append(", bundleExternalKey='").append(bundleExternalKey).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultBaseEntitlementWithAddOnsSpecifier that = (DefaultBaseEntitlementWithAddOnsSpecifier) o;

        if (isMigrated != that.isMigrated) {
            return false;
        }
        if (entitlementSpecifier != null ? !entitlementSpecifier.equals(that.entitlementSpecifier) : that.entitlementSpecifier != null) {
            return false;
        }
        if (entitlementEffectiveDate != null ? entitlementEffectiveDate.compareTo(that.entitlementEffectiveDate) != 0 : that.entitlementEffectiveDate != null) {
            return false;
        }
        if (billingEffectiveDate != null ? billingEffectiveDate.compareTo(that.billingEffectiveDate) != 0 : that.billingEffectiveDate != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        return bundleExternalKey != null ? bundleExternalKey.equals(that.bundleExternalKey) : that.bundleExternalKey == null;
    }

    @Override
    public int hashCode() {
        int result = entitlementSpecifier != null ? entitlementSpecifier.hashCode() : 0;
        result = 31 * result + (entitlementEffectiveDate != null ? entitlementEffectiveDate.hashCode() : 0);
        result = 31 * result + (billingEffectiveDate != null ? billingEffectiveDate.hashCode() : 0);
        result = 31 * result + (isMigrated ? 1 : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (bundleExternalKey != null ? bundleExternalKey.hashCode() : 0);
        return result;
    }
}
