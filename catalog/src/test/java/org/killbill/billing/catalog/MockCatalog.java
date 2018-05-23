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

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.rules.DefaultPlanRules;

public class MockCatalog extends StandaloneCatalog implements Catalog {

    private PlanChangeResult planChange;
    private BillingAlignment billingAlignment;
    private PlanAlignmentCreate planCreateAlignment;

    public MockCatalog() {
        setEffectiveDate(new Date());
        setProducts(MockProduct.createAll());
        setPlans(MockPlan.createAll());
        populateRules();
        populatePriceLists();
    }

    public void populateRules() {
        setPlanRules(new DefaultPlanRules());
    }

    public void populatePriceLists() {
        final Collection<Plan> plans = getCurrentPlans();

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

    @Override
    public Date getStandaloneCatalogEffectiveDate(final DateTime dateTime) {
        return getEffectiveDate();
    }

    @Override
    public Currency[] getSupportedCurrencies(final DateTime requestedDate) {
        return getCurrentSupportedCurrencies();
    }

    @Override
    public Unit[] getUnits(final DateTime requestedDate) {
        return getCurrentUnits();
    }

    @Override
    public Collection<Product> getProducts(final DateTime requestedDate) {
        return getCurrentProducts();
    }

    @Override
    public Collection<Plan> getPlans(final DateTime requestedDate) {
        return getCurrentPlans();
    }

    @Override
    public PriceListSet getPriceLists(final DateTime dateTime) {
        return getPriceLists();
    }

    @Override
    public Plan findPlan(final String name, final DateTime requestedDate) throws CatalogApiException {
        return findCurrentPlan(name);
    }

    @Override
    public Plan createOrFindPlan(final PlanSpecifier spec, final PlanPhasePriceOverridesWithCallContext overrides, final DateTime requestedDate)
            throws CatalogApiException {
        return createOrFindCurrentPlan(spec, overrides);
    }

    @Override
    public Plan findPlan(final String name, final DateTime effectiveDate, final DateTime subscriptionStartDate)
            throws CatalogApiException {
        return findCurrentPlan(name);
    }

    @Override
    public Plan createOrFindPlan(final PlanSpecifier spec, final PlanPhasePriceOverridesWithCallContext overrides, final DateTime requestedDate,
                                 final DateTime subscriptionStartDate) throws CatalogApiException {
        return createOrFindCurrentPlan(spec, overrides);
    }

    @Override
    public Product findProduct(final String name, final DateTime requestedDate) throws CatalogApiException {
        return findCurrentProduct(name);
    }

    @Override
    public PlanPhase findPhase(final String name, final DateTime requestedDate, final DateTime subscriptionStartDate)
            throws CatalogApiException {
        return findCurrentPhase(name);
    }

    @Override
    public PriceList findPriceListForPlan(final String name, final DateTime requestedDate, final DateTime subscriptionStartDate) throws CatalogApiException {
        return findCurrentPricelist(findCurrentPlan(name).getPriceListName());
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to, final DateTime requestedDate, final DateTime subscriptionStartDate) {
        return planChange(from, to);
    }

    @Override
    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase, final DateTime requestedDate, final DateTime subscriptionStartDate)
            throws CatalogApiException {
        return planCancelPolicy(planPhase);
    }

    @Override
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier, final DateTime requestedDate, final DateTime subscriptionStartDate) {
        return planCreateAlignment(specifier);
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase, final DateTime requestedDate, final DateTime subscriptionStartDate) {
        return billingAlignment(planPhase);
    }

    @Override
    public PlanAlignmentChange planChangeAlignment(final PlanPhaseSpecifier from, final PlanSpecifier to, final DateTime requestedDate, final DateTime subscriptionStartDate)
            throws CatalogApiException {
        return planChangeAlignment(from, to);
    }

    @Override
    public PlanAlignmentChange planChangeAlignment(final PlanPhaseSpecifier from, final PlanSpecifier to)
            throws CatalogApiException {
        return super.planChangeAlignment(from, to);
    }

    @Override
    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase) throws CatalogApiException {
        return super.planCancelPolicy(planPhase);
    }

    @Override
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier) {
        return planCreateAlignment;
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase) {
        return billingAlignment;
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to) {
        return planChange;
    }

    public DefaultProduct getCurrentProduct(final int idx) {
        return (DefaultProduct) getCurrentProducts().toArray()[idx];
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
