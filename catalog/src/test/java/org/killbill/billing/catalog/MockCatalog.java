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

package org.killbill.billing.catalog;

import java.util.Date;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
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
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.rules.DefaultCaseCancelPolicy;
import org.killbill.billing.catalog.rules.DefaultCaseChangePlanAlignment;
import org.killbill.billing.catalog.rules.DefaultCaseChangePlanPolicy;
import org.killbill.billing.catalog.rules.DefaultCaseCreateAlignment;
import org.killbill.billing.catalog.rules.DefaultPlanRules;

public class MockCatalog extends StandaloneCatalog implements Catalog {

    private static final String[] PRODUCT_NAMES = new String[]{"TestProduct1", "TestProduct2", "TestProduct3"};
    private boolean canCreatePlan;
    private PlanChangeResult planChange;
    private BillingAlignment billingAlignment;
    private PlanAlignmentCreate planCreateAlignment;

    public MockCatalog() {
        setEffectiveDate(new Date());
        setProducts(MockProduct.createAll());
        setPlans((DefaultPlan[]) MockPlan.createAll());
        populateRules();
        populatePriceLists();
    }

    public void populateRules() {
        setPlanRules(new DefaultPlanRules());
    }

    public void setRules(
            final DefaultCaseChangePlanPolicy[] caseChangePlanPolicy,
            final DefaultCaseChangePlanAlignment[] caseChangePlanAlignment,
            final DefaultCaseCancelPolicy[] caseCancelPolicy,
            final DefaultCaseCreateAlignment[] caseCreateAlignment
                        ) {

    }

    public void populatePriceLists() {
        final DefaultPlan[] plans = getCurrentPlans();

        final DefaultPriceList[] priceList = new DefaultPriceList[plans.length - 1];
        for (int i = 1; i < plans.length; i++) {
            priceList[i - 1] = new DefaultPriceList(new DefaultPlan[]{plans[i]}, plans[i].getName() + "-pl");
        }

        final DefaultPriceListSet set = new DefaultPriceListSet(new PriceListDefault(new DefaultPlan[]{plans[0]}), priceList);
        setPriceLists(set);
    }

    public String[] getProductNames() {
        return PRODUCT_NAMES;
    }

    @Override
    public Currency[] getSupportedCurrencies(final DateTime requestedDate) throws CatalogApiException {
        return getCurrentSupportedCurrencies();
    }

    @Override
    public Product[] getProducts(final DateTime requestedDate) throws CatalogApiException {
        return getCurrentProducts();
    }

    @Override
    public Plan[] getPlans(final DateTime requestedDate) throws CatalogApiException {
        return getCurrentPlans();
    }

    @Override
    public Plan findPlan(final String name, final DateTime requestedDate) throws CatalogApiException {
        return findCurrentPlan(name);
    }

    @Override
    public Plan createOrFindPlan(final String productName, final BillingPeriod term, final String priceListName, PlanPhasePriceOverridesWithCallContext overrides, final DateTime requestedDate)
            throws CatalogApiException {
        return createOrFindCurrentPlan(productName, term, priceListName, overrides);
    }

    @Override
    public Plan findPlan(final String name, final DateTime effectiveDate, final DateTime subscriptionStartDate)
            throws CatalogApiException {
        return findCurrentPlan(name);
    }

    @Override
    public Plan createOrFindPlan(final String productName, final BillingPeriod term, final String priceListName, PlanPhasePriceOverridesWithCallContext overrides, final DateTime requestedDate,
                         final DateTime subscriptionStartDate) throws CatalogApiException {
        return createOrFindCurrentPlan(productName, term, priceListName, overrides);
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
    public PriceList findPriceList(final String name, final DateTime requestedDate) throws CatalogApiException {
        return findCurrentPricelist(name);
    }

    @Override
    public BillingActionPolicy planChangePolicy(final PlanPhaseSpecifier from, final PlanSpecifier to, final DateTime requestedDate)
            throws CatalogApiException {
        return planChangePolicy(from, to);
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to, final DateTime requestedDate)
            throws CatalogApiException {
        return planChange(from, to);
    }

    @Override
    public BillingActionPolicy planCancelPolicy(final PlanPhaseSpecifier planPhase, final DateTime requestedDate)
            throws CatalogApiException {
        return planCancelPolicy(planPhase);
    }

    @Override
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier, final DateTime requestedDate)
            throws CatalogApiException {
        return planCreateAlignment(specifier);
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase, final DateTime requestedDate)
            throws CatalogApiException {
        return billingAlignment(planPhase);
    }

    @Override
    public PlanAlignmentChange planChangeAlignment(final PlanPhaseSpecifier from, final PlanSpecifier to, final DateTime requestedDate)
            throws CatalogApiException {
        return planChangeAlignment(from, to);
    }

    @Override
    public boolean canCreatePlan(final PlanSpecifier specifier, final DateTime requestedDate) throws CatalogApiException {
        return canCreatePlan(specifier);
    }

    @Override
    public BillingActionPolicy planChangePolicy(final PlanPhaseSpecifier from, final PlanSpecifier to) throws CatalogApiException {
        return super.planChangePolicy(from, to);
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
    public PlanAlignmentCreate planCreateAlignment(final PlanSpecifier specifier) throws CatalogApiException {
        return planCreateAlignment;
    }

    @Override
    public BillingAlignment billingAlignment(final PlanPhaseSpecifier planPhase) throws CatalogApiException {
        return billingAlignment;
    }

    @Override
    public PlanChangeResult planChange(final PlanPhaseSpecifier from, final PlanSpecifier to) throws CatalogApiException {
        return planChange;
    }

    @Override
    public boolean canCreatePlan(final PlanSpecifier specifier) throws CatalogApiException {
        return canCreatePlan;
    }

    public void setCanCreatePlan(final boolean canCreatePlan) {
        this.canCreatePlan = canCreatePlan;
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
