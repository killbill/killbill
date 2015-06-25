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
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.rules.CaseChangePlanAlignment;

public class TestModelCaseChangePlanAlignment extends TestModelCaseChange implements CaseChangePlanAlignment {

    private final PlanAlignmentChange planAlignmentChange;

    public TestModelCaseChangePlanAlignment(final PhaseType phaseType,
                                            final Product fromProduct,
                                            final ProductCategory fromProductCategory,
                                            final BillingPeriod fromBillingPeriod,
                                            final PriceList fromPriceList,
                                            final Product toProduct,
                                            final ProductCategory toProductCategory,
                                            final BillingPeriod toBillingPeriod,
                                            final PriceList toPriceList,
                                            final PlanAlignmentChange planAlignmentChange) {
        super(phaseType, fromProduct, fromProductCategory, fromBillingPeriod, fromPriceList, toProduct, toProductCategory, toBillingPeriod, toPriceList);
        this.planAlignmentChange = planAlignmentChange;
    }

    @Override
    public PlanAlignmentChange getAlignment() {
        return planAlignmentChange;
    }
}
