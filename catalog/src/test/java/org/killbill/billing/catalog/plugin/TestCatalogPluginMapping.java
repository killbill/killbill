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

import org.joda.time.DateTime;
import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanAlignmentChange;
import org.killbill.billing.catalog.api.PlanAlignmentCreate;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.api.rules.Case;
import org.killbill.billing.catalog.api.rules.CaseBillingAlignment;
import org.killbill.billing.catalog.api.rules.CaseCancelPolicy;
import org.killbill.billing.catalog.api.rules.CaseChange;
import org.killbill.billing.catalog.api.rules.CaseChangePlanAlignment;
import org.killbill.billing.catalog.api.rules.CaseChangePlanPolicy;
import org.killbill.billing.catalog.api.rules.CaseCreateAlignment;
import org.killbill.billing.catalog.api.rules.CasePhase;
import org.killbill.billing.catalog.api.rules.CasePriceList;
import org.killbill.billing.catalog.api.rules.PlanRules;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;

public class TestCatalogPluginMapping extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testMappingFromExistingCatalog() throws Exception {
        final StandaloneCatalog inputCatalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final StandalonePluginCatalog pluginCatalog = buildStandalonePluginCatalog(inputCatalog);

        final StandaloneCatalogMapper mapper = new StandaloneCatalogMapper(inputCatalog.getCatalogName(), inputCatalog.getRecurringBillingMode());

        final StandaloneCatalog output = mapper.toStandaloneCatalog(pluginCatalog, inputCatalog.getCatalogURI());
        Assert.assertEquals(output, inputCatalog);

    }

    private StandalonePluginCatalog buildStandalonePluginCatalog(final StandaloneCatalog inputCatalog) throws Exception {

        final TestPlanRules rules = new TestPlanRules(ImmutableList.<Product>copyOf(inputCatalog.getCurrentProducts()),
                                                      ImmutableList.<Plan>copyOf(inputCatalog.getCurrentPlans()),
                                                      inputCatalog.getPriceLists().getAllPriceLists());

        if (inputCatalog.getPlanRules().getCaseChangePlanPolicy() != null) {
            for (final CaseChangePlanPolicy cur : inputCatalog.getPlanRules().getCaseChangePlanPolicy()) {
                rules.addCaseChangePlanPolicyRule(cur);
            }
        }
        if (inputCatalog.getPlanRules().getCaseChangePlanAlignment() != null) {
            for (final CaseChangePlanAlignment cur : inputCatalog.getPlanRules().getCaseChangePlanAlignment()) {
                rules.addCaseChangeAlignmentRule(cur);
            }
        }
        if (inputCatalog.getPlanRules().getCaseCancelPolicy() != null) {
            for (final CaseCancelPolicy cur : inputCatalog.getPlanRules().getCaseCancelPolicy()) {
                rules.addCaseCancelRule(cur);
            }
        }
        if (inputCatalog.getPlanRules().getCaseCreateAlignment() != null) {
            for (final CaseCreateAlignment cur : inputCatalog.getPlanRules().getCaseCreateAlignment()) {
                rules.addCaseCreateAlignmentRule(cur);
            }
        }
        if (inputCatalog.getPlanRules().getCaseBillingAlignment() != null) {
            for (final CaseBillingAlignment cur : inputCatalog.getPlanRules().getCaseBillingAlignment()) {
                rules.addCaseBillingAlignmentRule(cur);
            }
        }
        if (inputCatalog.getPlanRules().getCasePriceList() != null) {
            for (final CasePriceList cur : inputCatalog.getPlanRules().getCasePriceList()) {
                rules.addPriceListRule(cur);
            }
        }
        final TestStandalonePluginCatalog result = new TestStandalonePluginCatalog(new DateTime(inputCatalog.getEffectiveDate()),
                                                                                   ImmutableList.<Currency>copyOf(inputCatalog.getCurrentSupportedCurrencies()),
                                                                                   ImmutableList.<Product>copyOf(inputCatalog.getCurrentProducts()),
                                                                                   ImmutableList.<Plan>copyOf(inputCatalog.getCurrentPlans()),
                                                                                   inputCatalog.getPriceLists().getDefaultPricelist(),
                                                                                   ImmutableList.<PriceList>copyOf(inputCatalog.getPriceLists().getChildPriceLists()),
                                                                                   rules,
                                                                                   ImmutableList.<Unit>copyOf(inputCatalog.getCurrentUnits()));
        return result;
    }

    private static final class TestPlanRules implements PlanRules {

        private final Iterable<Product> products;
        private final Iterable<Plan> plans;
        private final Iterable<PriceList> priceLists;

        private final List<CaseChangePlanPolicy> caseChangePlanPolicy;
        private final List<CaseChangePlanAlignment> caseChangePlanAlignment;
        private final List<CaseCancelPolicy> caseCancelPolicy;
        private final List<CaseCreateAlignment> caseCreateAlignment;
        private final List<CaseBillingAlignment> caseBillingAlignments;
        private final List<CasePriceList> casePriceList;

        public TestPlanRules(final Iterable<Product> products, Iterable<Plan> plans, Iterable<PriceList> priceLists) {
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
            caseBillingAlignments.add(new TestCaseBillingAlignment(product, productCategory, billingPeriod, priceList, phaseType, billingAlignment));
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
            caseCancelPolicy.add(new TestCaseCancelPolicy(product, productCategory, billingPeriod, priceList, phaseType, billingActionPolicy));

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
            caseChangePlanAlignment.add(new TestCaseChangePlanAlignment(phaseType, findProduct(fromProduct), fromProductCategory, fromBillingPeriod, findPriceList(fromPriceList), findProduct(toProduct), toProductCategory, toBillingPeriod, findPriceList(toPriceList), planAlignmentChange));
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
            caseChangePlanPolicy.add(new TestCaseChangePlanPolicy(phaseType, findProduct(fromProduct), fromProductCategory, fromBillingPeriod, findPriceList(fromPriceList), findProduct(toProduct), toProductCategory, toBillingPeriod, findPriceList(toPriceList), policy));
        }

        public void addCaseCreateAlignmentRule(final CaseCreateAlignment input) {
            caseCreateAlignment.add(input);
        }

        public void addCaseCreateAlignmentRule(final Product product,
                                               final ProductCategory productCategory,
                                               final BillingPeriod billingPeriod,
                                               final PriceList priceList,
                                               final PlanAlignmentCreate planAlignmentCreate) {
            caseCreateAlignment.add(new TestCaseCreateAlignment(product, productCategory, billingPeriod, priceList, planAlignmentCreate));
        }

        public void addPriceListRule(final CasePriceList input) {
            casePriceList.add(input);
        }

        public void addPriceListRule(final Product product,
                                     final ProductCategory productCategory,
                                     final BillingPeriod billingPeriod,
                                     final PriceList priceList,
                                     final PriceList destPriceList) {
            casePriceList.add(new TestCasePriceList(product, productCategory, billingPeriod, priceList, destPriceList));
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

    public static class TestCasePriceList extends TestCase implements CasePriceList {

        private final PriceList destPriceList;

        public TestCasePriceList(final Product product,
                                 final ProductCategory productCategory,
                                 final BillingPeriod billingPeriod,
                                 final PriceList priceList,
                                 final PriceList destPriceList) {
            super(product, productCategory, billingPeriod, priceList);
            this.destPriceList = destPriceList;
        }

        @Override
        public PriceList getDestinationPriceList() {
            return destPriceList;
        }
    }

    public static class TestCaseCreateAlignment extends TestCase implements CaseCreateAlignment {

        private final PlanAlignmentCreate planAlignmentCreate;

        public TestCaseCreateAlignment(final Product product,
                                       final ProductCategory productCategory,
                                       final BillingPeriod billingPeriod,
                                       final PriceList priceList,
                                       final PlanAlignmentCreate planAlignmentCreate) {
            super(product, productCategory, billingPeriod, priceList);
            this.planAlignmentCreate = planAlignmentCreate;
        }

        @Override
        public PlanAlignmentCreate getPlanAlignmentCreate() {
            return planAlignmentCreate;
        }
    }

    public static class TestCaseCancelPolicy extends TestCasePhase implements CaseCancelPolicy {

        private final BillingActionPolicy billingActionPolicy;

        public TestCaseCancelPolicy(final Product product,
                                    final ProductCategory productCategory,
                                    final BillingPeriod billingPeriod,
                                    final PriceList priceList,
                                    final PhaseType phaseType,
                                    final BillingActionPolicy billingActionPolicy) {
            super(product, productCategory, billingPeriod, priceList, phaseType);
            this.billingActionPolicy = billingActionPolicy;
        }

        @Override
        public BillingActionPolicy getBillingActionPolicy() {
            return billingActionPolicy;
        }
    }

    public static class TestCaseBillingAlignment extends TestCasePhase implements CaseBillingAlignment {

        private final BillingAlignment billingAlignment;

        public TestCaseBillingAlignment(final Product product,
                                        final ProductCategory productCategory,
                                        final BillingPeriod billingPeriod,
                                        final PriceList priceList,
                                        final PhaseType phaseType,
                                        final BillingAlignment billingAlignment) {
            super(product, productCategory, billingPeriod, priceList, phaseType);
            this.billingAlignment = billingAlignment;
        }

        @Override
        public BillingAlignment getBillingAlignment() {
            return billingAlignment;
        }
    }

    public static class TestCasePhase extends TestCase implements CasePhase {

        private final PhaseType phaseType;

        public TestCasePhase(final Product product,
                             final ProductCategory productCategory,
                             final BillingPeriod billingPeriod,
                             final PriceList priceList,
                             final PhaseType phaseType) {
            super(product, productCategory, billingPeriod, priceList);
            this.phaseType = phaseType;
        }

        @Override
        public PhaseType getPhaseType() {
            return phaseType;
        }
    }

    public static class TestCase implements Case {

        private final Product product;

        private final ProductCategory productCategory;

        private final BillingPeriod billingPeriod;

        private final PriceList priceList;

        public TestCase(final Product product,
                        final ProductCategory productCategory,
                        final BillingPeriod billingPeriod,
                        final PriceList priceList) {
            this.product = product;
            this.productCategory = productCategory;
            this.billingPeriod = billingPeriod;
            this.priceList = priceList;
        }

        @Override
        public Product getProduct() {
            return product;
        }

        @Override
        public ProductCategory getProductCategory() {
            return productCategory;
        }

        @Override
        public BillingPeriod getBillingPeriod() {
            return billingPeriod;
        }

        @Override
        public PriceList getPriceList() {
            return priceList;
        }
    }

    public static class TestCaseChangePlanAlignment extends TestCaseChange implements CaseChangePlanAlignment {

        private final PlanAlignmentChange planAlignmentChange;

        public TestCaseChangePlanAlignment(final PhaseType phaseType,
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

    public static class TestCaseChangePlanPolicy extends TestCaseChange implements CaseChangePlanPolicy {

        private final BillingActionPolicy billingPolicy;

        public TestCaseChangePlanPolicy(final PhaseType phaseType,
                                        final Product fromProduct,
                                        final ProductCategory fromProductCategory,
                                        final BillingPeriod fromBillingPeriod,
                                        final PriceList fromPriceList,
                                        final Product toProduct,
                                        final ProductCategory toProductCategory,
                                        final BillingPeriod toBillingPeriod,
                                        final PriceList toPriceList,
                                        final BillingActionPolicy billingPolicy) {
            super(phaseType, fromProduct, fromProductCategory, fromBillingPeriod, fromPriceList, toProduct, toProductCategory, toBillingPeriod, toPriceList);
            this.billingPolicy = billingPolicy;
        }

        @Override
        public BillingActionPolicy getBillingActionPolicy() {
            return billingPolicy;
        }
    }

    public static class TestCaseChange implements CaseChange {

        private final PhaseType phaseType;

        private final Product fromProduct;

        private final ProductCategory fromProductCategory;

        private final BillingPeriod fromBillingPeriod;

        private final PriceList fromPriceList;

        private final Product toProduct;

        private final ProductCategory toProductCategory;

        private final BillingPeriod toBillingPeriod;

        private final PriceList toPriceList;

        public TestCaseChange(final PhaseType phaseType,
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

    private static class TestStandalonePluginCatalog implements StandalonePluginCatalog {

        private final DateTime effectiveDate;

        private final Iterable<Currency> currency;

        private final Iterable<Product> products;

        private final Iterable<Plan> plans;

        private final PriceList defaultPriceList;

        private final Iterable<PriceList> childrenPriceLists;

        private final PlanRules planRules;

        private final Iterable<Unit> units;

        public TestStandalonePluginCatalog(final DateTime effectiveDate,
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
}
