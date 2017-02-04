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
import java.util.List;

import javax.annotation.Nullable;

import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class PhasePriceOverrideJson {


    private final String planName;
    private final String phaseName;
    private final String phaseType;
    private final BigDecimal fixedPrice;
    private final BigDecimal recurringPrice;

    @JsonCreator
    public PhasePriceOverrideJson(@JsonProperty("planName") final String planName,
                                  @JsonProperty("phaseName") final String phaseName,
                                  @JsonProperty("phaseType") final String phaseType,
                                  @Nullable @JsonProperty("fixedPrice") final BigDecimal fixedPrice,
                                  @Nullable @JsonProperty("recurringPrice") final BigDecimal recurringPrice) {
        this.planName = planName;
        this.phaseName = phaseName;
        this.phaseType = phaseType;
        this.fixedPrice = fixedPrice;
        this.recurringPrice = recurringPrice;
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

    @Override
    public String toString() {
        return "PhasePriceOverrideJson{" +
               "planName='" + planName + '\'' +
               "phaseName='" + phaseName + '\'' +
               "phaseType='" + phaseType + '\'' +
               ", fixedPrice=" + fixedPrice +
               ", recurringPrice=" + recurringPrice +
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
        return true;
    }

    @Override
    public int hashCode() {
        int result = planName != null ? planName.hashCode() : 0;
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
        result = 31 * result + (phaseType != null ? phaseType.hashCode() : 0);
        result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
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
                if (input.getPhaseName() != null) {
                    return new DefaultPlanPhasePriceOverride(input.getPhaseName(), currency, input.getFixedPrice(), input.getRecurringPrice());
                } else {
                    final PhaseType phaseType = input.getPhaseType() != null ? PhaseType.valueOf(input.getPhaseType()) : null;
                    final PlanPhaseSpecifier planPhaseSpecifier = spec.getPlanName() != null ?
                                                                  new PlanPhaseSpecifier(spec.getPlanName(), phaseType) :
                                                                  new PlanPhaseSpecifier(spec.getProductName(), spec.getBillingPeriod(), spec.getPriceListName(), phaseType);
                    final Currency resolvedCurrency = input.getFixedPrice() != null || input.getRecurringPrice() != null ? currency : null;
                    return new DefaultPlanPhasePriceOverride(planPhaseSpecifier, resolvedCurrency, input.getFixedPrice(), input.getRecurringPrice());
                }
            }
        }));

    }
}
