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

import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.rules.CaseBillingAlignment;
import org.killbill.billing.catalog.api.rules.CaseCancelPolicy;
import org.killbill.billing.catalog.api.rules.CaseChangePlanAlignment;
import org.killbill.billing.catalog.api.rules.CaseChangePlanPolicy;
import org.killbill.billing.catalog.api.rules.CaseCreateAlignment;
import org.killbill.billing.catalog.api.rules.CasePriceList;
import org.killbill.billing.catalog.api.rules.PlanRules;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCatalogPluginMapping extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testMappingFromExistingCatalog() throws Exception {
        final StandaloneCatalog inputCatalog = getCatalog("SpyCarAdvanced.xml");
        final StandalonePluginCatalog pluginCatalog = buildStandalonePluginCatalog(inputCatalog);

        final StandaloneCatalogMapper mapper = new StandaloneCatalogMapper(inputCatalog.getCatalogName());

        final StandaloneCatalog output = mapper.toStandaloneCatalog(pluginCatalog);
        output.setRecurringBillingMode(inputCatalog.getRecurringBillingMode());
        Assert.assertEquals(output, inputCatalog);

    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/1944")
    public void testMappingWithNullAvailableAndIncluded() {
        final StandalonePluginCatalog pluginCatalog = Mockito.mock(StandalonePluginCatalog.class);
        Mockito.when(pluginCatalog.getEffectiveDate()).thenReturn(DateTime.now());
        final Product mockProduct = Mockito.mock(Product.class);
        Mockito.when(mockProduct.getName()).thenReturn("Standard");
        Mockito.when(mockProduct.getAvailable()).thenReturn(null);
        Mockito.when(mockProduct.getIncluded()).thenReturn(null);
        Mockito.when(pluginCatalog.getProducts()).thenReturn(List.of(mockProduct));
        final PriceList defaultPriceList = Mockito.mock(PriceList.class);
        Mockito.when(defaultPriceList.getName()).thenReturn("DEFAULT");
        final Plan mockPlan = Mockito.mock(Plan.class);
        Mockito.when(mockPlan.getName()).thenReturn("standard-monthly");
        Mockito.when(mockPlan.getPriceList()).thenReturn(defaultPriceList);
        final PlanPhase planPhase = Mockito.mock(PlanPhase.class);
        Mockito.when(planPhase.getDuration()).thenReturn(Mockito.mock(Duration.class));
        Mockito.when(mockPlan.getFinalPhase()).thenReturn(planPhase);
        Mockito.when(pluginCatalog.getPlans()).thenReturn(List.of(mockPlan));
        Mockito.when(pluginCatalog.getDefaultPriceList()).thenReturn(defaultPriceList);
        Mockito.when(pluginCatalog.getCurrencies()).thenReturn((List.of(Mockito.mock(Currency.class))));
        Mockito.when(pluginCatalog.getPlanRules()).thenReturn(Mockito.mock(PlanRules.class));
        final StandaloneCatalogMapper mapper = new StandaloneCatalogMapper("test-catalog");

        try {
            final StandaloneCatalog output = mapper.toStandaloneCatalog(pluginCatalog);
        } catch (final NullPointerException e) {
            Assert.fail("NullPointerException occurs in mapper");
        }
    }

    private StandalonePluginCatalog buildStandalonePluginCatalog(final StandaloneCatalog inputCatalog) {

        final TestModelPlanRules rules = new TestModelPlanRules(List.copyOf(inputCatalog.getProducts()),
                                                                List.copyOf(inputCatalog.getPlans()),
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
        final TestModelStandalonePluginCatalog result = new TestModelStandalonePluginCatalog(new DateTime(inputCatalog.getEffectiveDate()),
                                                                                   List.of(inputCatalog.getSupportedCurrencies()),
                                                                                   List.copyOf(inputCatalog.getProducts()),
                                                                                   List.copyOf(inputCatalog.getPlans()),
                                                                                   inputCatalog.getPriceLists().getDefaultPricelist(),
                                                                                   List.of(inputCatalog.getPriceLists().getChildPriceLists()),
                                                                                   rules,
                                                                                   List.of(inputCatalog.getUnits()));
        return result;
    }

}
