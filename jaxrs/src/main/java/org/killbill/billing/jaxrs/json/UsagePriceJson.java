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
import org.killbill.billing.catalog.api.TierBlockPolicy;
import org.killbill.billing.catalog.api.UsageType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="UsagePrice")
public class UsagePriceJson {

    private final String usageName;

    private final UsageType usageType;

    private final BillingMode billingMode;

    private final TierBlockPolicy tierBlockPolicy;

    private final List<TierPriceJson> tierPrices;

    @JsonCreator
    public UsagePriceJson(@JsonProperty("usageName") final String usageName,
                          @Nullable @JsonProperty("usageType") final UsageType usageType,
                          @Nullable @JsonProperty("billingMode") final BillingMode billingMode,
                          @Nullable @JsonProperty("tierBlockPolicy") final TierBlockPolicy tierBlockPolicy,
                          @Nullable @JsonProperty("tierPrices") final List<TierPriceJson> tierPrices) {
        this.usageName = usageName;
        this.usageType = usageType;
        this.billingMode = billingMode;
        this.tierBlockPolicy = tierBlockPolicy;
        this.tierPrices = tierPrices;
    }

    public String getUsageName() {
        return usageName;
    }

    public UsageType getUsageType() {
        return usageType;
    }

    public BillingMode getBillingMode() {
        return billingMode;
    }

    public TierBlockPolicy getTierBlockPolicy() {
        return tierBlockPolicy;
    }

    public List<TierPriceJson> getTierPrices() {
        return tierPrices;
    }

    @Override
    public String toString() {
        return "UsagePriceJson{" +
               "usageName='" + usageName + '\'' +
               "usageType='" + usageType + '\'' +
               ", billingMode=" + billingMode +
               ", tierBlockPolicy=" + tierBlockPolicy +
               ", tierPrices=" + tierPrices +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UsagePriceJson)) {
            return false;
        }

        final UsagePriceJson that = (UsagePriceJson) o;

        if (usageName != null ? !usageName.equals(that.usageName) : that.usageName != null) {
            return false;
        }
        if (usageType != null ? !usageType.equals(that.usageType) : that.usageType != null) {
            return false;
        }
        if (billingMode != null ? !billingMode.equals(that.billingMode) : that.billingMode != null) {
            return false;
        }
        if (tierBlockPolicy != null ? !tierBlockPolicy.equals(that.tierBlockPolicy) : that.tierBlockPolicy != null) {
            return false;
        }
        if (tierPrices != null ? !tierPrices.equals(that.tierPrices) : that.tierPrices != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = usageName != null ? usageName.hashCode() : 0;
        result = 31 * result + (usageType != null ? usageType.hashCode() : 0);
        result = 31 * result + (billingMode != null ? billingMode.hashCode() : 0);
        result = 31 * result + (tierBlockPolicy != null ? tierBlockPolicy.hashCode() : 0);
        result = 31 * result + (tierPrices != null ? tierPrices.hashCode() : 0);
        return result;
    }

}
