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

package org.killbill.billing.catalog.plugin;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.rules.CaseChange;

public class TestModelCaseChange implements CaseChange {

    private final PhaseType phaseType;

    private final Product fromProduct;

    private final ProductCategory fromProductCategory;

    private final BillingPeriod fromBillingPeriod;

    private final PriceList fromPriceList;

    private final Product toProduct;

    private final ProductCategory toProductCategory;

    private final BillingPeriod toBillingPeriod;

    private final PriceList toPriceList;

    public TestModelCaseChange(final PhaseType phaseType,
                               final Product fromProduct,
                               final ProductCategory fromProductCategory,
                               final BillingPeriod fromBillingPeriod,
                               final PriceList fromPriceList,
                               final Product toProduct,
                               final ProductCategory toProductCategory,
                               final BillingPeriod toBillingPeriod,
                               final PriceList toPriceList) {
        this.phaseType = phaseType;
        this.fromProduct = fromProduct;
        this.fromProductCategory = fromProductCategory;
        this.fromBillingPeriod = fromBillingPeriod;
        this.fromPriceList = fromPriceList;
        this.toProduct = toProduct;
        this.toProductCategory = toProductCategory;
        this.toBillingPeriod = toBillingPeriod;
        this.toPriceList = toPriceList;
    }

    @Override
    public PhaseType getPhaseType() {
        return phaseType;
    }

    @Override
    public Product getFromProduct() {
        return fromProduct;
    }

    @Override
    public ProductCategory getFromProductCategory() {
        return fromProductCategory;
    }

    @Override
    public BillingPeriod getFromBillingPeriod() {
        return fromBillingPeriod;
    }

    @Override
    public PriceList getFromPriceList() {
        return fromPriceList;
    }

    @Override
    public Product getToProduct() {
        return toProduct;
    }

    @Override
    public ProductCategory getToProductCategory() {
        return toProductCategory;
    }

    @Override
    public BillingPeriod getToBillingPeriod() {
        return toBillingPeriod;
    }

    @Override
    public PriceList getToPriceList() {
        return toPriceList;
    }
}
