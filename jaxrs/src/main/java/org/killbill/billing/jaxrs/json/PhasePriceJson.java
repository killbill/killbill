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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.Nullable;

import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.Usage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value = "PhasePrice")
public class PhasePriceJson {

    private final String planName;
    private final String phaseName;
    private final String phaseType;
    private final BigDecimal fixedPrice;
    private final BigDecimal recurringPrice;
    private final List<UsagePriceJson> usagePrices;

    @JsonCreator
    public PhasePriceJson(@JsonProperty("planName") final String planName,
                          @JsonProperty("phaseName") final String phaseName,
                          @JsonProperty("phaseType") final String phaseType,
                          @Nullable @JsonProperty("fixedPrice") final BigDecimal fixedPrice,
                          @Nullable @JsonProperty("recurringPrice") final BigDecimal recurringPrice,
                          @Nullable @JsonProperty("usagePrices") final List<UsagePriceJson> usagePrices) {
        this.planName = planName;
        this.phaseName = phaseName;
        this.phaseType = phaseType;
        this.fixedPrice = fixedPrice;
        this.recurringPrice = recurringPrice;
        this.usagePrices = usagePrices;
    }

    public PhasePriceJson(final String planName,
                          final String phaseName,
                          final String phaseType,
                          final BigDecimal fixedPrice,
                          final BigDecimal recurringPrice,
                          final Usage[] usagePrices,
                          final Currency currency) throws CatalogApiException {
        this.planName = planName;
        this.phaseName = phaseName;
        this.phaseType = phaseType;
        this.fixedPrice = fixedPrice;
        this.recurringPrice = recurringPrice;
        this.usagePrices = new LinkedList<UsagePriceJson>();

        for (final Usage usage : usagePrices) {
            List<TierPriceJson> usageTierPrices = new LinkedList<TierPriceJson>();
            for (final Tier tier : usage.getTiers()) {
                List<BlockPriceJson> blockPrices = new LinkedList<BlockPriceJson>();

                for (final TieredBlock block : tier.getTieredBlocks()) {
                    BlockPriceJson blockPriceJson = new BlockPriceJson(block.getUnit().getName(), block.getSize(), block.getPrice().getPrice(currency), block.getMax());
                    blockPrices.add(blockPriceJson);
                }
                TierPriceJson tierPriceJson = new TierPriceJson(blockPrices);
                usageTierPrices.add(tierPriceJson);
            }
            final UsagePriceJson usagePriceJson = new UsagePriceJson(usage.getName(), usage.getUsageType(), usage.getBillingMode(), usage.getTierBlockPolicy(), usageTierPrices);
            this.usagePrices.add(usagePriceJson);
        }

    }

    public String getPlanName() {
        return planName;
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

    public List<UsagePriceJson> getUsagePrices() {
        return usagePrices;
    }

    @Override
    public String toString() {
        return "PhasePriceJson{" +
               "planName='" + planName + '\'' +
               "phaseName='" + phaseName + '\'' +
               "phaseType='" + phaseType + '\'' +
               ", fixedPrice=" + fixedPrice +
               ", recurringPrice=" + recurringPrice +
               ", usageOverrides=" + usagePrices +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PhasePriceJson)) {
            return false;
        }

        final PhasePriceJson that = (PhasePriceJson) o;

        if (fixedPrice != null ? fixedPrice.compareTo(that.fixedPrice) != 0 : that.fixedPrice != null) {
            return false;
        }
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) {
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
        if (usagePrices != null ? !usagePrices.equals(that.usagePrices) : that.usagePrices != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = planName != null ? planName.hashCode() : 0;
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
        result = 31 * result + (phaseType != null ? phaseType.hashCode() : 0);
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
        result = 31 * result + (usagePrices != null ? usagePrices.hashCode() : 0);
        return result;
    }
}
