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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogEntity;
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
import org.killbill.billing.catalog.rules.DefaultCaseCancelPolicy;
import org.killbill.billing.catalog.rules.DefaultCaseChangePlanAlignment;
import org.killbill.billing.catalog.rules.DefaultCaseChangePlanPolicy;
import org.killbill.billing.catalog.rules.DefaultCaseCreateAlignment;
import org.killbill.billing.catalog.rules.DefaultPlanRules;

import com.google.common.collect.ImmutableList;

public class MockCatalog extends StandaloneCatalog implements Catalog {

    private static final String[] PRODUCT_NAMES = new String[]{"TestProduct1", "TestProduct2", "TestProduct3"};
    private boolean canCreatePlan;
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

    public void setRules(
            final DefaultCaseChangePlanPolicy[] caseChangePlanPolicy,
            final DefaultCaseChangePlanAlignment[] caseChangePlanAlignment,
            final DefaultCaseCancelPolicy[] caseCancelPolicy,
            final DefaultCaseCreateAlignment[] caseCreateAlignment
                        ) {

    }

    public void populatePriceLists() {
        final Collection<Plan> plans = getCurrentPlans();

        final DefaultPriceList[] priceList = new DefaultPriceList[plans.size() - 1];
        int i = 1;
        final Iterator<Plan> it = plans.iterator();
        final Plan initialPlan = it.next();
        while (it.hasNext()) {
            final Plan plan = it.next();
            priceList[i - 1] = new DefaultPriceList(new DefaultPlan[] { (DefaultPlan) plan}, plan.getName() + "-pl");
            i++;
        }

        final DefaultPriceListSet set = new DefaultPriceListSet(new PriceListDefault(new DefaultPlan[]{(DefaultPlan) initialPlan}), priceList);
        setPriceLists(set);
    }

    public String[] getProductNames() {
        return PRODUCT_NAMES;
    }

    @Override
    public Date getStandaloneCatalogEffectiveDate(final DateTime dateTime) throws CatalogApiException {
        return getEffectiveDate();
    }

    @Override
    public Currency[] getSupportedCurrencies(final DateTime requestedDate) throws CatalogApiException {
        return getCurrentSupportedCurrencies();
    }

    @Override
    public Collection<Product> getProducts(final DateTime requestedDate) throws CatalogApiException {
        return getCurrentProducts();
    }

    @Override
    public Collection<Plan> getPlans(final DateTime requestedDate) throws CatalogApiException {
        return getCurrentPlans();
    }

    @Override
    public PriceListSet getPriceLists(final DateTime dateTime) throws CatalogApiException {
        return getPriceLists();
    }

    @Override
    public Plan findPlan(final String name, final DateTime requestedDate) throws CatalogApiException {
        return findCurrentPlan(name);
    }

    @Override
    public Plan createOrFindPlan(final PlanSpecifier spec, PlanPhasePriceOverridesWithCallContext overrides, final DateTime requestedDate)
            throws CatalogApiException {
        return createOrFindCurrentPlan(spec, overrides);
    }

    @Override
    public Plan findPlan(final String name, final DateTime effectiveDate, final DateTime subscriptionStartDate)
            throws CatalogApiException {
        return findCurrentPlan(name);
    }

    @Override
    public Plan createOrFindPlan(final PlanSpecifier spec, PlanPhasePriceOverridesWithCallContext overrides, final DateTime requestedDate,
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
    public PriceList findPriceList(final String name, final DateTime requestedDate) throws CatalogApiException {
        return findCurrentPricelist(name);
    }

    @Override
    public PriceList findPriceListForPlan(final String name, final DateTime requestedDate, final DateTime subscriptionStartDate) throws CatalogApiException {
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


    public DefaultProduct getCurrentProduct(int idx) {
        return (DefaultProduct) getCurrentProducts().toArray()[idx];
    }

    private <T extends CatalogEntity> void convertCurrentEntries(final Collection<T> unordered, final T [] result) {
        // Tests are not so well written and make assumption on how such entries are ordered
        final LinkedList<T> list = new LinkedList<T>(unordered);
        Collections.sort(list, new Comparator<T>() {
            @Override
            public int compare(final T o1, final T o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        list.toArray(result);
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
