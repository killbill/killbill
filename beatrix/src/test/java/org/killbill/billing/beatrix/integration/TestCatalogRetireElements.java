/*
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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionEvent;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestCatalogRetireElements extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogRetireElements");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow", description = "See https://github.com/killbill/killbill/issues/1110")
    public void testChangePlanTwiceWithNewPlan() throws Exception {
        // Catalog v1 starts in 2011-01-01
        // Catalog v2 starts in 2015-12-01
        // -> Start on catalog V1
        final LocalDate today = new LocalDate(2015, 11, 5);

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier(productName, term, "DEFAULT", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec1), "externalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);

        // Move out a month. Date > catalog V2
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Current date is > catalog V2
        // Change to a plan that exists in V2 but not in V1
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("bazooka-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        bpEntitlement = bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();

        // Change back to original plan: The code (subscription) chooses the latest version of the catalog when making the change and therefore the call succeeds
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec1), clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();

        //
        // The code normally goes through the grandfathering logic to find the version but specifies the transitionTime of the latest CHANGE (and not the subscriptionStartDate)
        // and therefore correctly find the latest catalog version, invoicing at the new price 295.95
        //
        final Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2015, 12, 5), new LocalDate(2016, 1, 5), InvoiceItemType.RECURRING, new BigDecimal("295.95")),
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2015, 12, 5), new LocalDate(2016, 1, 5), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-500.00")),
                                                               new ExpectedInvoiceItemCheck(new LocalDate(2015, 12, 5), new LocalDate(2015, 12, 5), InvoiceItemType.CBA_ADJ, new BigDecimal("204.05")));
        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);
        // RECURRING should be set against V2
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);
        Assert.assertNull(curInvoice.getInvoiceItems().get(1).getCatalogEffectiveDate());
        Assert.assertNull(curInvoice.getInvoiceItems().get(2).getCatalogEffectiveDate());



        final Subscription bpSubscription = subscriptionApi.getSubscriptionForEntitlementId(bpEntitlementId, false, callContext);
        final List<SubscriptionEvent> events = bpSubscription.getSubscriptionEvents();
        // We are seeing START_ENTITLEMENT, START_BILLING, and the **last CHANGE**
        // Note that the PHASE and intermediate CHANGE are not being returned (is_active = FALSE) because all coincided on the same date. This is debatable
        // whether this is a good semantics. See #1030
        assertEquals(events.size(), 3);
        // Verify what we return is the price from the correct catalog version. See #1120
        assertEquals(events.get(2).getNextPhase().getRecurring().getRecurringPrice().getPrice(account.getCurrency()).compareTo(new BigDecimal("295.95")), 0);

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
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out a month.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Catalog v2 should start now.

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, term, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        try {
            entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "externalKey2", null, null, false, true, Collections.emptyList(), callContext);
            fail(); // force to fail is there is not an exception
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.CAT_PLAN_NOT_FOUND.getCode());
        }

        final DefaultEntitlement bpEntitlement2 =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey2", "Bazooka",
                                                           ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertNotNull(bpEntitlement2);

        // Move out a month and verify 'Pistol' plan continue working as expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 4);

        // One more invoice to generate an item whose effectiveDate is past our original V1 version
        // and verify we still know how to compute the pretty name by finding the right initial
        // catalog version -- as pistol-monthly was retired on V1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 5);

        final Invoice fifthInvoice = invoices.get(4);
        assertEquals(fifthInvoice.getInvoiceItems().size(), 2);

        final InvoiceItem pistolInvoiceItem = fifthInvoice.getInvoiceItems().stream()
                .filter(input -> "pistol-monthly".equals(input.getPlanName()))
                .findFirst().get();
        assertEquals(pistolInvoiceItem.getPrettyPlanName(), "Beretta");
    }

    @Test(groups = "slow")
    public void testRetirePlanWithUncancel() throws Exception {
        // Catalog v1 starts in 2011-01-01
        // Catalog v2 starts in 2015-12-01
        final LocalDate today = new LocalDate(2015, 10, 5);

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final String productName = "Pistol";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        Entitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName,
                                                           ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);

        // Move out a month.
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Cancel entitlement
        bpEntitlement = bpEntitlement.cancelEntitlementWithDate(new LocalDate("2016-05-01"), true, Collections.emptyList(), callContext);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        // Move out a month.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Catalog v2 should start now.

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, term, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        try {
            entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "externalKey2", null, null, false, true, Collections.emptyList(), callContext);
            fail(); // force to fail is there is not an exception
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.CAT_PLAN_NOT_FOUND.getCode());
        }

        // Uncancel entitlement
        busHandler.pushExpectedEvents(NextEvent.UNCANCEL);
        bpEntitlement.uncancelEntitlement(Collections.emptyList(), callContext);
        assertListenerStatus();

        // Move out a month and verify 'Pistol' plan continue working as expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 4);
        for (final Invoice invoice : invoices) {
            assertEquals(invoice.getInvoiceItems().get(0).getPlanName(), "pistol-monthly");
        }
    }

    @Test(groups = "slow")
    public void testRetirePlanAfterChange() throws Exception {
        // Catalog v1 starts in 2011-01-01
        // Catalog v3 starts in 2016-01-01
        final LocalDate today = new LocalDate(2015, 7, 5);

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));

        final String productName = "Pistol";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier(productName, term, "DEFAULT", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec1), "externalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);

        assertNotNull(bpEntitlement);
        assertEquals(bpEntitlement.getLastActivePhase().getPhaseType(), PhaseType.TRIAL);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        // Move out after trial (2015-08-04)
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        assertEquals(entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext).getLastActivePhase().getPhaseType(), PhaseType.EVERGREEN);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 2);

        // 2015-08-05
        clock.addDays(1);
        assertListenerStatus();

        // Change to discount phase in SpecialDiscount pricelist (CBA generated, no payment)
        // Note that we need to trigger a CHANGE outside a TRIAL phase to generate a CHANGE event (otherwise, a CREATE is generated)
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier(productName, term, "SpecialDiscount", null);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        bpEntitlement = bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();

        assertEquals(entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext).getLastActivePhase().getPhaseType(), PhaseType.DISCOUNT);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 3);

        // Move out after discount phase (happens on 2015-11-04)
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2015-09-05
        clock.addMonths(1);
        assertListenerStatus();
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2015-10-05
        clock.addMonths(1);
        assertListenerStatus();
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2015-11-05
        clock.addMonths(1);
        // This verifies the PlanAligner.getNextTimedPhase codepath with a CHANGE transition type
        assertListenerStatus();

        assertEquals(entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext).getLastActivePhase().getPhaseType(), PhaseType.EVERGREEN);

        // Move out a month (2015-12-05)
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Move out a month (2016-01-01)
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Catalog v3 should start now.

        try {
            entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec2), "externalKey2", null, null, false, true, Collections.emptyList(), callContext);
            fail(); // force to fail is there is not an exception
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.CAT_NO_SUCH_PRODUCT.getCode());
        }

        // Move out a month and verify 'Pistol' discounted plan continues working as expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 9);
    }

    @Test(groups = "slow")
    public void testRetirePlanWithUncancelAfterChange() throws Exception {
        // Catalog v1 starts in 2011-01-01
        // Catalog v3 starts in 2016-01-01
        final LocalDate today = new LocalDate(2015, 7, 5);

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));

        final String productName = "Pistol";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier(productName, term, "DEFAULT", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec1), "externalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);

        assertNotNull(bpEntitlement);
        assertEquals(bpEntitlement.getLastActivePhase().getPhaseType(), PhaseType.TRIAL);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

        // Move out after trial (2015-08-04)
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        assertEquals(entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext).getLastActivePhase().getPhaseType(), PhaseType.EVERGREEN);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 2);

        // 2015-08-05
        clock.addDays(1);
        assertListenerStatus();

        // Change to discount phase in SpecialDiscount pricelist (CBA generated, no payment)
        // Note that we need to trigger a CHANGE outside a TRIAL phase to generate a CHANGE event (otherwise, a CREATE is generated)
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier(productName, term, "SpecialDiscount", null);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        bpEntitlement = bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();

        assertEquals(entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext).getLastActivePhase().getPhaseType(), PhaseType.DISCOUNT);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 3);

        // Cancel entitlement
        bpEntitlement = bpEntitlement.cancelEntitlementWithDate(new LocalDate("2016-05-01"), true, Collections.emptyList(), callContext);
        assertEquals(bpEntitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        // Move out after discount phase (happens on 2015-11-04)
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2015-09-05
        clock.addMonths(1);
        assertListenerStatus();
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2015-10-05
        clock.addMonths(1);
        assertListenerStatus();
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // 2015-11-05
        clock.addMonths(1);
        // This verifies the PlanAligner.getNextTimedPhase codepath with a CHANGE transition type
        assertListenerStatus();

        assertEquals(entitlementApi.getEntitlementForId(bpEntitlement.getId(), false, callContext).getLastActivePhase().getPhaseType(), PhaseType.EVERGREEN);

        // Move out a month (2015-12-05)
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Uncancel entitlement
        busHandler.pushExpectedEvents(NextEvent.UNCANCEL);
        bpEntitlement.uncancelEntitlement(Collections.emptyList(), callContext);
        assertListenerStatus();

        // Move out a month (2016-01-01)
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Catalog v3 should start now.

        try {
            entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec2), "externalKey2", null, null, false, true, Collections.emptyList(), callContext);
            fail(); // force to fail is there is not an exception
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.CAT_NO_SUCH_PRODUCT.getCode());
        }

        // Move out a month and verify 'Pistol' discounted plan continues working as expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 9);
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
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

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
            entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "externalKey2", null, null, false, true, Collections.emptyList(), callContext);
            fail(); // force to fail is there is not an exception
        } catch (final EntitlementApiException e) {
            assertTrue(e.getLocalizedMessage().startsWith("Could not find any product named 'Pistol'"));
        }

        // Move out a month and verify 'Pistol' plan continue working as expected.
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
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
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "externalKey", null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();
        final Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, false, callContext);

        assertNotNull(bpEntitlement);
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext).size(), 1);

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
            entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "externalKey2", null, null, false, true, Collections.emptyList(), callContext);
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

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 5);

        assertTrue(invoices.get(0).getInvoiceItems().get(0).getPhaseName().equals("discount-pistol-monthly-trial"));
        assertTrue(invoices.get(1).getInvoiceItems().get(0).getPhaseName().equals("discount-pistol-monthly-discount"));
        assertTrue(invoices.get(2).getInvoiceItems().get(0).getPhaseName().equals("discount-pistol-monthly-discount"));
        assertTrue(invoices.get(3).getInvoiceItems().get(0).getPhaseName().equals("discount-pistol-monthly-discount"));
        assertTrue(invoices.get(4).getInvoiceItems().get(0).getPhaseName().equals("discount-pistol-monthly-evergreen"));
    }
}
