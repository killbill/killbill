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

package com.ning.billing.catalog.api;

// TODO should be interface ?

/**
 * The class {@code PlanPhaseSpecifier} specifies the attributes of a {@code PlanPhase}
 */
public class PlanPhaseSpecifier {

    private final PhaseType phaseType;
    private final String productName;
    private final ProductCategory productCategory;
    private final BillingPeriod billingPeriod;
    private final String priceListName;

    public PlanPhaseSpecifier(final String productName, final ProductCategory productCategory, final BillingPeriod billingPeriod,
                              final String priceListName, final PhaseType phaseType) {
        this.phaseType = phaseType;
        this.productName = productName;
        this.productCategory = productCategory;
        this.billingPeriod = billingPeriod;
        this.priceListName = priceListName;
    }

    /**
     *
     * @return the {@code Product} name
     */
    public String getProductName() {
        return productName;
    }

    /**
     *
     * @return the {@code ProductCategory}
     */
    public ProductCategory getProductCategory() {
        return productCategory;
    }

    /**
     *
     * @return the {@code BillingPeriod}
     */
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    /**
     *
     * @return the name of the {@code PriceList}
     */
    public String getPriceListName() {
        return priceListName;
    }

    /**
     *
     * @return the {@code PhaseType}
     */
    public PhaseType getPhaseType() {
        return phaseType;
    }

    /**
     *
     * @return the {@code PlanSpecifier}
     */
    public PlanSpecifier toPlanSpecifier() {
        return new PlanSpecifier(productName, productCategory, billingPeriod, priceListName);
    }
}
