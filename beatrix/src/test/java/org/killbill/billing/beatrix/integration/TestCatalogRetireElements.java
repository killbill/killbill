/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.util.List;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestCatalogRetireElements extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource() {
        return super.getConfigSource("/beatrixCatalogRetireElements.properties");
    }

    @Test(groups = "slow")
    public void testRetirePlan() throws Exception {
        // Catalog v1 starts in 2011-01-01
        // Catalog v2 starts in 2015-12-01
        final LocalDate today = new LocalDate(2015, 11, 5);

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        //accountUserApi.createAccount(getAccountData(1), callContext);

        final String productName = "Pistol";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName,
                                                           ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out a month.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Catalog v2 should start now.

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, term, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        try {
            entitlementApi.createBaseEntitlement(account.getId(), spec, "externalKey2", null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
            fail(); // force to fail is there is not an exception
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.CAT_PLAN_NOT_FOUND.getCode());
        }

        // Move out a month and verify 'Pistol' plan continue working as expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 3);
        for (final Invoice invoice : invoices) {
            assertEquals(invoice.getInvoiceItems().get(0).getPlanName(), "pistol-monthly");
        }
    }

    @Test(groups = "slow")
    public void testRetireProduct() throws Exception {
        // Catalog v1 starts in 2011-01-01
        // Catalog v3 starts in 2016-01-01
        final LocalDate today = new LocalDate(2015, 11, 5);

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        //accountUserApi.createAccount(getAccountData(1), callContext);

        final String productName = "Pistol";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName,
                                                           ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out a month.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Move out a month.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Catalog v3 should start now.

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, term, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        try {
            entitlementApi.createBaseEntitlement(account.getId(), spec, "externalKey2", null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
            fail(); // force to fail is there is not an exception
        } catch (final EntitlementApiException e) {
            assertTrue(e.getLocalizedMessage().startsWith("Could not find any product named 'Pistol'"));
        }

        // Move out a month and verify 'Pistol' plan continue working as expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 4);
        for (final Invoice invoice : invoices) {
            assertEquals(invoice.getInvoiceItems().get(0).getPlanName(), "pistol-monthly");
        }
    }

    @Test(groups = "slow")
    public void testRetirePriceList() throws Exception {
        // Catalog v1 starts in 2011-01-01
        // Catalog v2 starts in 2015-12-01
        // Catalog v3 starts in 2016-01-01
        final LocalDate today = new LocalDate(2015, 11, 1);

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        //accountUserApi.createAccount(getAccountData(1), callContext);

        final String productName = "Pistol";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, term, "SpecialDiscount", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final Entitlement bpEntitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, "externalKey", null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext).size(), 1);

        // Move out a month.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Move out a month.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // PriceList "SpecialDiscount" at this point.

        try {
            entitlementApi.createBaseEntitlement(account.getId(), spec, "externalKey2", null, null, null, false, ImmutableList.<PluginProperty>of(), callContext);
            fail(); // force to fail is there is not an exception
        } catch (final EntitlementApiException e) {
            assertTrue(e.getLocalizedMessage().startsWith("Could not find any product named 'Pistol'"));
        }

        // Move out a month and verify 'Pistol' plan continue working as expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Move out a month.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, callContext);
        assertEquals(invoices.size(), 5);

        assertTrue(invoices.get(0).getInvoiceItems().get(0).getPhaseName().equals("discount-pistol-monthly-trial"));
        assertTrue(invoices.get(1).getInvoiceItems().get(0).getPhaseName().equals("discount-pistol-monthly-discount"));
        assertTrue(invoices.get(2).getInvoiceItems().get(0).getPhaseName().equals("discount-pistol-monthly-discount"));
        assertTrue(invoices.get(3).getInvoiceItems().get(0).getPhaseName().equals("discount-pistol-monthly-discount"));
        assertTrue(invoices.get(4).getInvoiceItems().get(0).getPhaseName().equals("discount-pistol-monthly-evergreen"));
    }
}
