/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CurrencyValueNull;
import org.killbill.billing.catalog.api.Listing;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.jaxrs.json.CatalogJson.PriceJson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.swagger.annotations.ApiModel;

@ApiModel(value="PlanDetail")
public class PlanDetailJson {

    final String product;
    final String plan;
    final BillingPeriod finalPhaseBillingPeriod;
    final String priceList;
    final List<PriceJson> finalPhaseRecurringPrice;

    @JsonCreator
    public PlanDetailJson(@JsonProperty("product") final String product,
                          @JsonProperty("plan") final String plan,
                          @JsonProperty("final_phase_billing_period") final BillingPeriod finalPhaseBillingPeriod,
                          @JsonProperty("priceList") final String priceList,
                          @JsonProperty("final_phase_recurring_price") final List<PriceJson> finalPhaseRecurringPrice) {
        this.product = product;
        this.plan = plan;
        this.finalPhaseBillingPeriod = finalPhaseBillingPeriod;
        this.priceList = priceList;
        this.finalPhaseRecurringPrice = finalPhaseRecurringPrice;
    }

    public PlanDetailJson(final Listing listing) {
        final Plan plan = listing.getPlan();
        if (plan == null) {
            this.product = null;
            this.plan = null;
            this.finalPhaseBillingPeriod = null;
            this.finalPhaseRecurringPrice = ImmutableList.<PriceJson>of();
        } else {
            this.product = plan.getProduct() == null ? null : plan.getProduct().getName();
            this.plan = plan.getName();
            this.finalPhaseBillingPeriod = plan.getRecurringBillingPeriod();
            if (plan.getFinalPhase() == null ||
                plan.getFinalPhase().getRecurring() == null ||
                plan.getFinalPhase().getRecurring().getRecurringPrice() == null ||
                plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices().length == 0) {
                this.finalPhaseRecurringPrice = ImmutableList.<PriceJson>of();
            } else {
                this.finalPhaseRecurringPrice = Lists.transform(ImmutableList.<Price>copyOf(plan.getFinalPhase().getRecurring().getRecurringPrice().getPrices()),
                                                                new Function<Price, PriceJson>() {
                                                                    @Override
                                                                    public PriceJson apply(final Price price) {
                                                                        try {
                                                                            return new PriceJson(price);
                                                                        } catch (final CurrencyValueNull e) {
                                                                            return new PriceJson(price.getCurrency(), BigDecimal.ZERO);
                                                                        }
                                                                    }
                                                                });
            }
        }
        this.priceList = listing.getPriceList() == null ? null : listing.getPriceList().getName();
    }

    public String getProduct() {
        return product;
    }

    public String getPlan() {
        return plan;
    }

    public BillingPeriod getFinalPhaseBillingPeriod() {
        return finalPhaseBillingPeriod;
    }

    public String getPriceList() {
        return priceList;
    }

    public List<PriceJson> getFinalPhaseRecurringPrice() {
        return finalPhaseRecurringPrice;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PlanDetailJson{");
        sb.append("product='").append(product).append('\'');
        sb.append(", plan='").append(plan).append('\'');
        sb.append(", finalPhaseBillingPeriod=").append(finalPhaseBillingPeriod);
        sb.append(", priceList='").append(priceList).append('\'');
        sb.append(", finalPhaseRecurringPrice=").append(finalPhaseRecurringPrice);
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

        final PlanDetailJson that = (PlanDetailJson) o;

        if (finalPhaseBillingPeriod != that.finalPhaseBillingPeriod) {
            return false;
        }
        if (finalPhaseRecurringPrice != null ? !finalPhaseRecurringPrice.equals(that.finalPhaseRecurringPrice) : that.finalPhaseRecurringPrice != null) {
            return false;
        }
        if (plan != null ? !plan.equals(that.plan) : that.plan != null) {
            return false;
        }
        if (priceList != null ? !priceList.equals(that.priceList) : that.priceList != null) {
            return false;
        }
        if (product != null ? !product.equals(that.product) : that.product != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = product != null ? product.hashCode() : 0;
        result = 31 * result + (plan != null ? plan.hashCode() : 0);
        result = 31 * result + (finalPhaseBillingPeriod != null ? finalPhaseBillingPeriod.hashCode() : 0);
        result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
        result = 31 * result + (finalPhaseRecurringPrice != null ? finalPhaseRecurringPrice.hashCode() : 0);
        return result;
    }
}
