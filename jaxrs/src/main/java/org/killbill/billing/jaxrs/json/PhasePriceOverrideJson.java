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

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.DefaultTierPriceOverride;
import org.killbill.billing.catalog.DefaultTieredBlockPriceOverride;
import org.killbill.billing.catalog.DefaultUsagePriceOverride;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsagePriceOverride;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class PhasePriceOverrideJson {

    private final String phaseName;
    private final String phaseType;
    private final BigDecimal fixedPrice;
    private final BigDecimal recurringPrice;
    private final List<UsagePriceOverrideJson> usagePriceOverrides;

    @JsonCreator
    public PhasePriceOverrideJson(@JsonProperty("phaseName") final String phaseName,
                                  @JsonProperty("phaseType") final String phaseType,
                                  @Nullable @JsonProperty("fixedPrice") final BigDecimal fixedPrice,
                                  @Nullable @JsonProperty("recurringPrice") final BigDecimal recurringPrice,
                                  @Nullable @JsonProperty("usageOverrides") final List<UsagePriceOverrideJson> usagePriceOverrides) {
        this.phaseName = phaseName;
        this.phaseType = phaseType;
        this.fixedPrice = fixedPrice;
        this.recurringPrice = recurringPrice;
        this.usagePriceOverrides = usagePriceOverrides;
    }

    public PhasePriceOverrideJson(final String phaseName,
                                  final String phaseType,
                                  final BigDecimal fixedPrice,
                                  final BigDecimal recurringPrice,
                                  final Usage[] usagePriceOverrides,
                                  final Currency currency) throws CatalogApiException {
        this.phaseName = phaseName;
        this.phaseType = phaseType;
        this.fixedPrice = fixedPrice;
        this.recurringPrice = recurringPrice;
        this.usagePriceOverrides = new LinkedList<UsagePriceOverrideJson>();

        for (final Usage usage : usagePriceOverrides) {
            List <TierPriceOverrideJson> tierPriceOverridesJson = new LinkedList<TierPriceOverrideJson>();
             for(final Tier tier :usage.getTiers())
             {
                 List <BlockPriceOverrideJson> blockPriceOverridesJson = new LinkedList<BlockPriceOverrideJson>();

                 for(final TieredBlock block : tier.getTieredBlocks())
                 {
                     BlockPriceOverrideJson blockPriceOverrideJson = new BlockPriceOverrideJson(block.getUnit().getName(), block.getSize(), block.getPrice().getPrice(currency), block.getMax());
                     blockPriceOverridesJson.add(blockPriceOverrideJson);
                 }
                     TierPriceOverrideJson tierPriceOverrideJson  = new TierPriceOverrideJson(blockPriceOverridesJson);
                     tierPriceOverridesJson.add(tierPriceOverrideJson);
             }
            final UsagePriceOverrideJson usagePriceOverrideJson = new UsagePriceOverrideJson(usage.getName(), usage.getUsageType(),usage.getBillingMode(), tierPriceOverridesJson);
            this.usagePriceOverrides.add(usagePriceOverrideJson);
        }

    }

    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    public BigDecimal getRecurringPrice() {
        return recurringPrice;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public String getPhaseType() {
        return phaseType;
    }

    public List<UsagePriceOverrideJson> getUsagePriceOverrides() {
        return usagePriceOverrides;
    }


    @Override
    public String toString() {
        return "PhasePriceOverrideJson{" +
               "phaseName='" + phaseName + '\'' +
               "phaseType='" + phaseType + '\'' +
               ", fixedPrice=" + fixedPrice +
               ", recurringPrice=" + recurringPrice +
                ", usageOverrides=" + usagePriceOverrides +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PhasePriceOverrideJson)) {
            return false;
        }

        final PhasePriceOverrideJson that = (PhasePriceOverrideJson) o;


        if (fixedPrice != null ? fixedPrice.compareTo(that.fixedPrice) != 0 : that.fixedPrice != null) {
            return false;
        }
        if (phaseName != null ? !phaseName.equals(that.phaseName) : that.phaseName != null) {
            return false;
        }
        if (phaseType != null ? !phaseType.equals(that.phaseType) : that.phaseType != null) {
            return false;
        }
        if (recurringPrice != null ? recurringPrice.compareTo(that.recurringPrice) != 0 : that.recurringPrice != null) {
            return false;
        }
        if (usagePriceOverrides != null ? usagePriceOverrides.equals(that.usagePriceOverrides)  : that.usagePriceOverrides != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = phaseName != null ? phaseName.hashCode() : 0;
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
        result = 31 * result + (phaseType != null ? phaseType.hashCode() : 0);
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
        result = 31 * result + (usagePriceOverrides != null ? usagePriceOverrides.hashCode() : 0);
        return result;
    }

    public static List<PlanPhasePriceOverride> toPlanPhasePriceOverrides(final List<PhasePriceOverrideJson> priceOverrides, final PlanSpecifier spec, final Currency currency) {
        if (priceOverrides == null || priceOverrides.isEmpty()) {
            return ImmutableList.<PlanPhasePriceOverride>of();
        }
        return ImmutableList.copyOf(Iterables.transform(priceOverrides, new Function<PhasePriceOverrideJson, PlanPhasePriceOverride>() {
            @Nullable
            @Override
            public PlanPhasePriceOverride apply(@Nullable final PhasePriceOverrideJson input) {

                List <UsagePriceOverride> usagePriceOverrides = new LinkedList<UsagePriceOverride>();
                Preconditions.checkNotNull(input);
                if(input.getUsagePriceOverrides()!= null) {
                    for (final UsagePriceOverrideJson usageOverrideJson : input.getUsagePriceOverrides()) {
                        List<TierPriceOverride> tierPriceOverrides = new LinkedList<TierPriceOverride>();
                        for (final TierPriceOverrideJson tierPriceOverrideJson : usageOverrideJson.getTierPriceOverrides()) {
                            List<TieredBlockPriceOverride> blockPriceOverrides = new LinkedList<TieredBlockPriceOverride>();
                            for (final BlockPriceOverrideJson block : tierPriceOverrideJson.getBlockPriceOverrides()) {
                                DefaultTieredBlockPriceOverride tieredBlockPriceOverride = new DefaultTieredBlockPriceOverride( block.getUnitName(), block.getSize(), block.getPrice(), block.getMax());
                                blockPriceOverrides.add(tieredBlockPriceOverride);
                            }
                            DefaultTierPriceOverride tierPriceOverride = new DefaultTierPriceOverride(blockPriceOverrides);
                            tierPriceOverrides.add(tierPriceOverride);
                        }
                        final DefaultUsagePriceOverride usageOverride = new DefaultUsagePriceOverride(usageOverrideJson.getUsageName(), usageOverrideJson.getUsageType(), tierPriceOverrides);
                        usagePriceOverrides.add(usageOverride);
                    }
                }

                if (input.getPhaseName() != null) {
                    return new DefaultPlanPhasePriceOverride(input.getPhaseName(), currency, input.getFixedPrice(), input.getRecurringPrice(), usagePriceOverrides);
                } else {
                    final PhaseType phaseType = input.getPhaseType() != null ? PhaseType.valueOf(input.getPhaseType()) : null;

                    final PlanPhaseSpecifier planPhaseSpecifier = spec.getPlanName() != null ?
                                                                  new PlanPhaseSpecifier(spec.getPlanName(), phaseType) :
                                                                  new PlanPhaseSpecifier(spec.getProductName(), spec.getBillingPeriod(), spec.getPriceListName(), phaseType);
                    return new DefaultPlanPhasePriceOverride(planPhaseSpecifier, currency, input.getFixedPrice(), input.getRecurringPrice(), usagePriceOverrides);

                }
            }
        }));

    }
}
