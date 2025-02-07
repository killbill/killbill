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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class TestCatalogWithEffectiveDateForExistingSubscriptionsCustomConfig extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogWithEffectiveDateForExistingSubscriptionsCustomConfig");
        // Custom subscription config to test the alignment for the catalog effectiveDateForExistingSubscriptions
        allExtraProperties.put("org.killbill.subscription.align.effectiveDateForExistingSubscriptions", "true");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testSubscriptionNotAlignedWithVersionChange1() throws Exception {

        final LocalDate today = new LocalDate(2022, 3, 15);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(15));

        final DefaultEntitlement bpEntitlement =
                createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey1", "Liability",
                                                           ProductCategory.BASE, BillingPeriod.MONTHLY,
                                                           NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);

        assertNotNull(bpEntitlement);
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2022, 3, 15), new LocalDate(2022, 4, 15), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2022-04-15
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2022, 4, 15), new LocalDate(2022, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        // Catalog v2 is 2022-04-01, but Liability effDt is 2022-05-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2022-05-15
        assertListenerStatus();

        // We expect to see no pro-ration because of property 'align.effectiveDateForExistingSubscriptions' and new price
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2022, 5, 15), new LocalDate(2022, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("59.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

    }

    // This is a similar test than testSubscriptionNotAlignedWithVersionChange except we also test when we have a local BCD that aligns with start date.
    @Test(groups = "slow")
    public void testSubscriptionNotAlignedWithVersionChange2() throws Exception {

        final LocalDate today = new LocalDate(2022, 3, 15);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        // We chose 20 (> local BCD = 15) to ensure test fails if local bcd is not correct taken into account
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(20));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("liability-monthly-no-trial");

        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, 15, null, null, null), null, null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2022, 3, 15), new LocalDate(2022, 4, 15), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2022-04-15
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2022, 4, 15), new LocalDate(2022, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        // Catalog v2 is 2022-04-01, but Liability effDt is 2022-05-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2022-05-15

        //Thread.sleep(1000 * 3600);
        assertListenerStatus();

        // We expect to see no pro-ration because of property 'align.effectiveDateForExistingSubscriptions' and new price
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2022, 5, 15), new LocalDate(2022, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("59.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

    }

    @Test(groups = "slow")
    public void testSubscriptionNotAlignedWithVersionChange3() throws Exception {

        // Catalog v2 is 2022-04-01 so new price applies right away
        final LocalDate today = new LocalDate(2022, 4, 15);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        // We chose 20 (> local BCD = 15) to ensure test fails if local bcd is not correct taken into account
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(20));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("liability-monthly-no-trial");

        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, 15, null, null, null), null, null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        // We expect the C2 price right away as we started after C2 effDt
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2022, 4, 15), new LocalDate(2022, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("59.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2022-05-15
        assertListenerStatus();

        // We expect to see no pro-ration because of property 'align.effectiveDateForExistingSubscriptions' and new price
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2022, 5, 15), new LocalDate(2022, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("59.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);
    }


    @Test(groups = "slow")
    public void testSubscriptionNotAlignedWithVersionChange4() throws Exception {

        // Catalog v2 is 2022-04-01, but Collision effDt is 2022-05-15
        final LocalDate today = new LocalDate(2022, 3, 5);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        // We chose 20 (> local BCD = 15) to ensure test fails if local bcd is not correct taken into account
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(20));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("collision-monthly-no-trial");

        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, 5, null, null, null), null, null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2022, 3, 5), new LocalDate(2022, 4, 5), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2022-04-05
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2022, 4, 5), new LocalDate(2022, 5, 5), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2022-05-05
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2022, 5, 5), new LocalDate(2022, 6, 5), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); // 2022-06-05
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2022, 6, 5), new LocalDate(2022, 7, 5), InvoiceItemType.RECURRING, new BigDecimal("59.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/2091")
    public void testBCDAlignmentWithTrial() throws Exception {

        final LocalDate today = new LocalDate(2022, 3, 27);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(3));
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("comprehensive-monthly-with-trial");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(createdEntitlementId, false, callContext);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2022, 3, 27), new LocalDate(2022, 4, 3), InvoiceItemType.FIXED, new BigDecimal("0.0")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(7); // 2022-04-03
        assertListenerStatus();

        // Check we see new price from V2
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2022, 4, 3), new LocalDate(2022, 5, 3), InvoiceItemType.RECURRING, new BigDecimal("59.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

    }
}
