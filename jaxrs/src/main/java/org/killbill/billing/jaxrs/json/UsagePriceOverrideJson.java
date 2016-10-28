package org.killbill.billing.jaxrs.json;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.UsageType;

import javax.annotation.Nullable;
import java.util.List;

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
