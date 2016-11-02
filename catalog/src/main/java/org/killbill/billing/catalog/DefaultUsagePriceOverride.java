package org.killbill.billing.catalog;

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.api.UsageType;

import java.util.List;

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
