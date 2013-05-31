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

package com.ning.billing.jaxrs.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Listing;

public class PlanDetailJson {
    final String productName;
    final String planName;
    final BillingPeriod billingPeriod;
    final String priceListName;
    final InternationalPrice finalPhasePrice;

    @JsonCreator
    public PlanDetailJson(@JsonProperty("product") final String productName,
                          @JsonProperty("plan") final String planName,
                          @JsonProperty("final_phase_billing_period") final BillingPeriod billingPeriod,
                          @JsonProperty("priceList") final String priceListName,
                          @JsonProperty("final_phase_recurring_price") final InternationalPrice finalPhasePrice) {
        this.productName = productName;
        this.planName = planName;
        this.billingPeriod = billingPeriod;
        this.priceListName = priceListName;
        this.finalPhasePrice = finalPhasePrice;
    }

    public PlanDetailJson(final Listing listing) {
        this(listing.getPlan().getProduct().getName(), listing.getPlan().getName(), listing.getPlan().getBillingPeriod(),
             listing.getPriceList().getName(), listing.getPlan().getFinalPhase().getRecurringPrice());
    }

    public String getProductName() {
        return productName;
    }

    public String getPlanName() {
        return planName;
    }

    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    public String getPriceListName() {
        return priceListName;
    }

    public InternationalPrice getFinalPhasePrice() {
        return finalPhasePrice;
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

        if (billingPeriod != that.billingPeriod) {
            return false;
        }
        if (finalPhasePrice != null ? !finalPhasePrice.equals(that.finalPhasePrice) : that.finalPhasePrice != null) {
            return false;
        }
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) {
            return false;
        }
        if (priceListName != null ? !priceListName.equals(that.priceListName) : that.priceListName != null) {
            return false;
        }
        if (productName != null ? !productName.equals(that.productName) : that.productName != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = productName != null ? productName.hashCode() : 0;
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (priceListName != null ? priceListName.hashCode() : 0);
        result = 31 * result + (finalPhasePrice != null ? finalPhasePrice.hashCode() : 0);
        return result;
    }
}
