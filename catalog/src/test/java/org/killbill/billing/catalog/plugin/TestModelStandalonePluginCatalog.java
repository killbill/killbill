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

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.api.rules.PlanRules;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;

public class TestModelStandalonePluginCatalog implements StandalonePluginCatalog {

    private final DateTime effectiveDate;

    private final Iterable<Currency> currency;

    private final Iterable<Product> products;

    private final Iterable<Plan> plans;

    private final PriceList defaultPriceList;

    private final Iterable<PriceList> childrenPriceLists;

    private final PlanRules planRules;

    private final Iterable<Unit> units;

    public TestModelStandalonePluginCatalog(final DateTime effectiveDate,
                                            final Iterable<Currency> currency,
                                            final Iterable<Product> products,
                                            final Iterable<Plan> plans,
                                            final PriceList defaultPriceList,
                                            final Iterable<PriceList> childrenPriceLists,
                                            final PlanRules planRules,
                                            final Iterable<Unit> units) {
        this.effectiveDate = effectiveDate;
        this.currency = currency;
        this.products = products;
        this.plans = plans;
        this.defaultPriceList = defaultPriceList;
        this.childrenPriceLists = childrenPriceLists;
        this.planRules = planRules;
        this.units = units;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public Iterable<Currency> getCurrencies() {
        return currency;
    }

    @Override
    public Iterable<Unit> getUnits() {
        return units;
    }

    @Override
    public Iterable<Product> getProducts() {
        return products;
    }

    @Override
    public Iterable<Plan> getPlans() {
        return plans;
    }

    @Override
    public PriceList getDefaultPriceList() {
        return defaultPriceList;
    }

    @Override
    public Iterable<PriceList> getChildrenPriceList() {
        return childrenPriceLists;
    }

    @Override
    public PlanRules getPlanRules() {
        return planRules;
    }
}
