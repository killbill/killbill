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

package org.killbill.billing.catalog;

import java.util.List;

import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.api.UsageType;

public class DefaultUsagePriceOverride implements UsagePriceOverride {

    String name;
    UsageType usageType;
    List<TierPriceOverride> tierPriceOverrides;

    public DefaultUsagePriceOverride(String name, UsageType usageType, List<TierPriceOverride> tierPriceOverrides) {
        this.name = name;
        this.usageType = usageType;
        this.tierPriceOverrides = tierPriceOverrides;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public UsageType getUsageType() {
        return usageType;
    }

    @Override
    public List<TierPriceOverride> getTierPriceOverrides() {
        return tierPriceOverrides;
    }


}
