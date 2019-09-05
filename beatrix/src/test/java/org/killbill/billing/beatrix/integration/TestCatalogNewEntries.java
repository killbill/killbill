/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration;

import java.util.HashMap;
import java.util.Map;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestCatalogNewEntries extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogRetireElements");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testNewPlan() throws Exception {

        // We start on the first version of the catalog where 'shotgun-monthly' exists but bazooka-monthly does not yet exist
        final LocalDate today = new LocalDate(2015, 11, 1);

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName,
                                                           ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhaseSpecifier bazookaSpec = new PlanPhaseSpecifier("bazooka-monthly");
        try {
            bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(bazookaSpec), null, null, callContext);
            Assert.fail("Change plan should fail because plan does not yet exist");
        } catch (EntitlementApiException ignore) {
        }

        // Move to 2015-12-1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // We will use the version 2 of the catalog which contains the new plan bazooka-monthly
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(bazookaSpec), new LocalDate(2015, 12, 1), null, callContext);
        assertListenerStatus();

    }

    @Test(groups = "slow")
    public void testNewPlanWithRetiredOldPlan() throws Exception {

        // We start on the first version of the catalog where 'pistol-monthly' exists but bazooka-monthly does not yet exist
        final LocalDate today = new LocalDate(2015, 11, 1);

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Pistol";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName,
                                                           ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        final PlanPhaseSpecifier bazookaSpec = new PlanPhaseSpecifier("bazooka-monthly");
        try {
            bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(bazookaSpec), null, null, callContext);
            Assert.fail("Change plan should fail because plan does not yet exist");
        } catch (EntitlementApiException ignore) {
        }

        // Move to 2015-12-1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // We will use the version 2 of the catalog which contains the new plan bazooka-monthly
        try {
            bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(bazookaSpec), new LocalDate(2015, 12, 1), null, callContext);
            Assert.fail("Change plan fails because current plan pistol-monthly does not exist anymore in the new version");
        } catch (EntitlementApiException ignore) {
        }

    }

}
