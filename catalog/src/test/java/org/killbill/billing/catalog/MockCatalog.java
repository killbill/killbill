/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.catalog;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.rules.DefaultPlanRules;

public class MockCatalog extends StandaloneCatalog {

    private PlanChangeResult planChange;
    private BillingAlignment billingAlignment;
    private PlanAlignmentCreate planCreateAlignment;

    public MockCatalog() {
        setUnits(new DefaultUnit[0]);
        setEffectiveDate(new Date());
        setProducts(MockProduct.createAll());
        setPlans(MockPlan.createAll());
        populateRules();
        populatePriceLists();
        initialize(this);
    }

    public void populateRules() {
        setPlanRules(new DefaultPlanRules());
    }

    public void populatePriceLists() {
        final Collection<Plan> plans = getPlans();

        final DefaultPriceList[] priceList = new DefaultPriceList[plans.size() - 1];
        int i = 1;
        final Iterator<Plan> it = plans.iterator();
        final Plan initialPlan = it.next();
        while (it.hasNext()) {
            final Plan plan = it.next();
            priceList[i - 1] = new DefaultPriceList(new DefaultPlan[]{(DefaultPlan) plan}, plan.getName() + "-pl");
            i++;
        }

        final DefaultPriceListSet set = new DefaultPriceListSet(new PriceListDefault(new DefaultPlan[]{(DefaultPlan) initialPlan}), priceList);
        setPriceLists(set);
    }

    public DefaultProduct getCurrentProduct(final int idx) {
        return (DefaultProduct) getProducts().toArray()[idx];
    }

    public void setPlanChange(final PlanChangeResult planChange) {
        this.planChange = planChange;
    }

    public void setBillingAlignment(final BillingAlignment billingAlignment) {
        this.billingAlignment = billingAlignment;
    }

    public void setPlanCreateAlignment(final PlanAlignmentCreate planCreateAlignment) {
        this.planCreateAlignment = planCreateAlignment;
    }
}
