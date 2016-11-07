/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.json;

import java.util.List;

import javax.annotation.Nullable;

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.UsageType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class UsagePriceOverrideJson {

    private final String usageName;

    private final UsageType usageType;

    private final BillingMode billingMode;

    private final List<TierPriceOverrideJson> tierPriceOverrides;

    public String getUsageName() {
        return usageName;
    }

    public UsageType getUsageType() {
        return usageType;
    }

    public BillingMode getBillingMode() {
        return billingMode;
    }

    public List<TierPriceOverrideJson> getTierPriceOverrides() {
        return tierPriceOverrides;
    }

    @JsonCreator
    public UsagePriceOverrideJson(@JsonProperty("usageName") final String usageName,
                             @Nullable @JsonProperty("usageType") final UsageType usageType,
                             @Nullable @JsonProperty("billingMode") final BillingMode billingMode,
                             @Nullable @JsonProperty("tierPriceOverrides") final List<TierPriceOverrideJson> tierPriceOverrides ) {
        this.usageName = usageName;
        this.usageType = usageType;
        this.billingMode = billingMode;
        this.tierPriceOverrides = tierPriceOverrides;
    }
}
