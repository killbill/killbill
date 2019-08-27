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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PlanChangeResult;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.rules.CaseBillingAlignment;
import org.killbill.billing.catalog.api.rules.CaseCancelPolicy;
import org.killbill.billing.catalog.api.rules.CaseChangePlanAlignment;
import org.killbill.billing.catalog.api.rules.CaseChangePlanPolicy;
import org.killbill.billing.catalog.api.rules.CaseCreateAlignment;
import org.killbill.billing.catalog.api.rules.CasePriceList;
import org.killbill.billing.catalog.api.rules.PlanRules;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class TestModelPlanRules implements PlanRules {

    private final Iterable<Product> products;
    private final Iterable<Plan> plans;
    private final Iterable<PriceList> priceLists;

    private final List<CaseChangePlanPolicy> caseChangePlanPolicy;
    private final List<CaseChangePlanAlignment> caseChangePlanAlignment;
    private final List<CaseCancelPolicy> caseCancelPolicy;
    private final List<CaseCreateAlignment> caseCreateAlignment;
    private final List<CaseBillingAlignment> caseBillingAlignments;
    private final List<CasePriceList> casePriceList;

    public TestModelPlanRules(final Iterable<Product> products, Iterable<Plan> plans, Iterable<PriceList> priceLists) {
        this.products = products;
        this.plans = plans;
        this.priceLists = priceLists;
        this.caseChangePlanPolicy = new ArrayList<CaseChangePlanPolicy>();
        this.caseChangePlanAlignment = new ArrayList<CaseChangePlanAlignment>();
        this.caseCancelPolicy = new ArrayList<CaseCancelPolicy>();
        this.caseCreateAlignment = new ArrayList<CaseCreateAlignment>();
        this.caseBillingAlignments = new ArrayList<CaseBillingAlignment>();
        this.casePriceList = new ArrayList<CasePriceList>();
    }

    public void addCaseBillingAlignmentRule(final CaseBillingAlignment input) {
        caseBillingAlignments.add(input);
    }

    public void addCaseBillingAlignmentRule(final Product product,
                                            final ProductCategory productCategory,
                                            final BillingPeriod billingPeriod,
                                            final PriceList priceList,
                                            final PhaseType phaseType,
                                            final BillingAlignment billingAlignment) {
        caseBillingAlignments.add(new TestModelCaseBillingAlignment(product, productCategory, billingPeriod, priceList, phaseType, billingAlignment));
    }

    public void addCaseCancelRule(final CaseCancelPolicy input) {
        caseCancelPolicy.add(input);
    }

    public void addCaseCancelRule(final Product product,
                                  final ProductCategory productCategory,
                                  final BillingPeriod billingPeriod,
                                  final PriceList priceList,
                                  final PhaseType phaseType,
                                  final BillingActionPolicy billingActionPolicy) {
        caseCancelPolicy.add(new TestModelCaseCancelPolicy(product, productCategory, billingPeriod, priceList, phaseType, billingActionPolicy));

    }

    public void addCaseChangeAlignmentRule(final CaseChangePlanAlignment input) {
        caseChangePlanAlignment.add(input);
    }

    public void addCaseChangeAlignmentRule(final PhaseType phaseType,
                                           final String fromProduct,
                                           final ProductCategory fromProductCategory,
                                           final BillingPeriod fromBillingPeriod,
                                           final String fromPriceList,
                                           final String toProduct,
                                           final ProductCategory toProductCategory,
                                           final BillingPeriod toBillingPeriod,
                                           final String toPriceList,
                                           final PlanAlignmentChange planAlignmentChange) {
        caseChangePlanAlignment.add(new TestModelCaseChangePlanAlignment(phaseType, findProduct(fromProduct), fromProductCategory, fromBillingPeriod, findPriceList(fromPriceList), findProduct(toProduct), toProductCategory, toBillingPeriod, findPriceList(toPriceList), planAlignmentChange));
    }

    public void addCaseChangePlanPolicyRule(final CaseChangePlanPolicy input) {
        caseChangePlanPolicy.add(input);
    }

    public void addCaseChangePlanPolicyRule(final PhaseType phaseType,
                                            final String fromProduct,
                                            final ProductCategory fromProductCategory,
                                            final BillingPeriod fromBillingPeriod,
                                            final String fromPriceList,
                                            final String toProduct,
                                            final ProductCategory toProductCategory,
                                            final BillingPeriod toBillingPeriod,
                                            final String toPriceList,
                                            final BillingActionPolicy policy) {
        caseChangePlanPolicy.add(new TestModelCaseChangePlanPolicy(phaseType, findProduct(fromProduct), fromProductCategory, fromBillingPeriod, findPriceList(fromPriceList), findProduct(toProduct), toProductCategory, toBillingPeriod, findPriceList(toPriceList), policy));
    }

    public void addCaseCreateAlignmentRule(final CaseCreateAlignment input) {
        caseCreateAlignment.add(input);
    }

    public void addCaseCreateAlignmentRule(final Product product,
                                           final ProductCategory productCategory,
                                           final BillingPeriod billingPeriod,
                                           final PriceList priceList,
                                           final PlanAlignmentCreate planAlignmentCreate) {
        caseCreateAlignment.add(new TestModelCaseCreateAlignment(product, productCategory, billingPeriod, priceList, planAlignmentCreate));
    }

    public void addPriceListRule(final CasePriceList input) {
        casePriceList.add(input);
    }

    public void addPriceListRule(final Product product,
                                 final ProductCategory productCategory,
                                 final BillingPeriod billingPeriod,
                                 final PriceList priceList,
                                 final PriceList destPriceList) {
        casePriceList.add(new TestModelCasePriceList(product, productCategory, billingPeriod, priceList, destPriceList));
    }

    @Override
    public StaticCatalog getCatalog() {
        return null;
    }

    @Override
    public Iterable<CaseChangePlanPolicy> getCaseChangePlanPolicy() {
        return caseChangePlanPolicy;
    }

    @Override
    public Iterable<CaseChangePlanAlignment> getCaseChangePlanAlignment() {
        return caseChangePlanAlignment;
    }

    @Override
    public Iterable<CaseCancelPolicy> getCaseCancelPolicy() {
        return caseCancelPolicy;
    }

    @Override
    public Iterable<CaseCreateAlignment> getCaseCreateAlignment() {
        return caseCreateAlignment;
    }

    @Override
    public Iterable<CaseBillingAlignment> getCaseBillingAlignment() {
        return caseBillingAlignments;
    }

    @Override
    public Iterable<CasePriceList> getCasePriceList() {
        return casePriceList;
    }

    @Override
    public PlanAlignmentCreate getPlanCreateAlignment(final PlanSpecifier planSpecifier) throws CatalogApiException {
        return null;
    }

    @Override
    public BillingActionPolicy getPlanCancelPolicy(final PlanPhaseSpecifier planPhaseSpecifier) throws CatalogApiException {
        return null;
    }

    @Override
    public BillingAlignment getBillingAlignment(final PlanPhaseSpecifier planPhaseSpecifier) throws CatalogApiException {
        return null;
    }

    @Override
    public PlanChangeResult getPlanChangeResult(final PlanPhaseSpecifier planPhaseSpecifier, final PlanSpecifier planSpecifier) throws CatalogApiException {
        return null;
    }

    private Product findProduct(@Nullable final String productName) {
        return find(products, productName, "products", new Predicate<Product>() {
            @Override
            public boolean apply(final Product input) {
                return input.getName().equals(productName);
            }
        });
    }

    private Plan findPlan(@Nullable final String planName) {
        return find(plans, planName, "plans", new Predicate<Plan>() {
            @Override
            public boolean apply(final Plan input) {
                return input.getName().equals(planName);
            }
        });
    }

    private PriceList findPriceList(@Nullable final String priceListName) {
        return find(priceLists, priceListName, "pricelists", new Predicate<PriceList>() {
            @Override
            public boolean apply(final PriceList input) {
                return input.getName().equals(priceListName);
            }
        });
    }

    private <T> T find(final Iterable<T> all, @Nullable final String name, final String what, final Predicate<T> predicate) {
        if (name == null) {
            return null;
        }
        final T result = Iterables.tryFind(all, predicate).orNull();
        if (result == null) {
            throw new IllegalStateException(String.format("%s : cannot find entry %s", what, name));
        }
        return result;
    }
}
