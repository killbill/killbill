/*
 * Copyright 2010-2011 Ning, Inc.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Listing;

public class PlanDetailJason {

    final String productName;
    final  String planName;
    final  BillingPeriod billingPeriod;
    final  String priceListName;
    final  InternationalPrice finalPhasePrice;
    public PlanDetailJason(
            @JsonProperty("product") String productName,
            @JsonProperty("plan") String planName,
            @JsonProperty("final_phase_billing_period") BillingPeriod billingPeriod,
            @JsonProperty("priceList") String priceListName,
            @JsonProperty("final_phase_recurring_price") InternationalPrice finalPhasePrice
                ) {
        this.productName = productName;
        this.planName = planName;
        this.billingPeriod = billingPeriod;
        this.priceListName = priceListName;
        this.finalPhasePrice = finalPhasePrice;
    }
    
    public PlanDetailJason(Listing listing) {
        this.productName = listing.getPlan().getProduct().getName();
        this.planName = listing.getPlan().getName();
        this.billingPeriod = listing.getPlan().getBillingPeriod();
        this.priceListName = listing.getPriceList().getName();
        this.finalPhasePrice = listing.getPlan().getFinalPhase().getRecurringPrice();
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
    
 
}
