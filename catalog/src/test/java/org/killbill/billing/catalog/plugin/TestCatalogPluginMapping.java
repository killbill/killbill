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
import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.api.rules.CaseBillingAlignment;
import org.killbill.billing.catalog.api.rules.CaseCancelPolicy;
import org.killbill.billing.catalog.api.rules.CaseChangePlanAlignment;
import org.killbill.billing.catalog.api.rules.CaseChangePlanPolicy;
import org.killbill.billing.catalog.api.rules.CaseCreateAlignment;
import org.killbill.billing.catalog.api.rules.CasePriceList;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

public class TestCatalogPluginMapping extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testMappingFromExistingCatalog() throws Exception {
        final StandaloneCatalog inputCatalog = XMLLoader.getObjectFromString(Resources.getResource("SpyCarAdvanced.xml").toExternalForm(), StandaloneCatalog.class);
        final StandalonePluginCatalog pluginCatalog = buildStandalonePluginCatalog(inputCatalog);

        final StandaloneCatalogMapper mapper = new StandaloneCatalogMapper(inputCatalog.getCatalogName());

        final StandaloneCatalog output = mapper.toStandaloneCatalog(pluginCatalog, inputCatalog.getCatalogURI());
        output.setRecurringBillingMode(inputCatalog.getRecurringBillingMode());
        Assert.assertEquals(output, inputCatalog);

    }

    private StandalonePluginCatalog buildStandalonePluginCatalog(final StandaloneCatalog inputCatalog) throws Exception {

        final TestModelPlanRules rules = new TestModelPlanRules(ImmutableList.<Product>copyOf(inputCatalog.getCurrentProducts()),
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
        final TestModelStandalonePluginCatalog result = new TestModelStandalonePluginCatalog(new DateTime(inputCatalog.getEffectiveDate()),
                                                                                   ImmutableList.<Currency>copyOf(inputCatalog.getCurrentSupportedCurrencies()),
                                                                                   ImmutableList.<Product>copyOf(inputCatalog.getCurrentProducts()),
                                                                                   ImmutableList.<Plan>copyOf(inputCatalog.getCurrentPlans()),
                                                                                   inputCatalog.getPriceLists().getDefaultPricelist(),
                                                                                   ImmutableList.<PriceList>copyOf(inputCatalog.getPriceLists().getChildPriceLists()),
                                                                                   rules,
                                                                                   ImmutableList.<Unit>copyOf(inputCatalog.getCurrentUnits()));
        return result;
    }

}
