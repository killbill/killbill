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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertNotNull;

public class TestCatalogWithEffectiveDateForExistingSubscriptionsCustomConfigMultiPhasePlan extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogWithEffectiveDateForExistingSubscriptionsCustomConfigMultiPhasePlan");
        // Custom subscription config to test the alignment for the catalog effectiveDateForExistingSubscriptions
        allExtraProperties.put("org.killbill.subscription.align.effectiveDateForExistingSubscriptions", "true");
        return super.getConfigSource(null, allExtraProperties);
    }
    
    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1862")
    public void testMultiPhasePlanWithTrialPhase() throws Exception {

        final LocalDate today = new LocalDate(2023, 3, 18);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-monthly-trial has a a 14-day TRIAL phase followed by an EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(18));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-monthly-trial"), null, null, null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to trial phase
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 18), new LocalDate(2023, 4, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-04-01 (end of trial phase) - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 4, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 1), new LocalDate(2023, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("4")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-01 - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 1), new LocalDate(2023, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("4")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-06-01 (v2 effective) - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 1), new LocalDate(2023, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("8.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

    }
    
    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1862")
    public void testMultiPhasePlanWithDiscountPhaseSubAlignedWithChange() throws Exception { //This test is exactly the same as the scenario reported in https://github.com/killbill/killbill/issues/1862

        final LocalDate today = new LocalDate(2023, 3, 18);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-monthly-discount has a a 14-day DISCOUNT phase followed by an EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(18));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-monthly-discount"), null, null, null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to discount phase
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 18), new LocalDate(2023, 4, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-04-01 (end of discount phase) - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 4, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 1), new LocalDate(2023, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-01 - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 1), new LocalDate(2023, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-06-01 (v2 effective) - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 1), new LocalDate(2023, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("10.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1862")
    public void testMultiPhasePlanWithDiscountPhaseSubNotAlignedWithChange() throws Exception {

        final LocalDate today = new LocalDate(2023, 4, 1);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-monthly-discount has a a 14-day DISCOUNT phase followed by an EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(18));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-monthly-discount"), null, null, null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to discount phase
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 1), new LocalDate(2023, 4, 15), InvoiceItemType.RECURRING, BigDecimal.ZERO));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-04-15 (end of discount phase) - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 4, 15));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 15), new LocalDate(2023, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-15 - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 15));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 15), new LocalDate(2023, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-06-15 (v2 effective) - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 15));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 15), new LocalDate(2023, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("10.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1862")
    public void testMultiPhasePlanWithDiscountPhaseAndThreeCatalogVersions() throws Exception {

        final LocalDate today = new LocalDate(2023, 3, 18);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-monthly-discount has a a 14-day DISCOUNT phase followed by an EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(18));

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-monthly-discount"), null, null, null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to discount phase
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 18), new LocalDate(2023, 4, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-04-01 (end of discount phase) - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 4, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 1), new LocalDate(2023, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-01 - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 1), new LocalDate(2023, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-06-01 (v2 effective) - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 1), new LocalDate(2023, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("10.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

        //move clock to 2023-07-01 (v2 effective) - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 7, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 7, 1), new LocalDate(2023, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("10.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

        //move clock to 2023-08-01 (v3 effective) - invoice corresponding to evergreen phase as per v3
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 8, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 6, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 1), new LocalDate(2023, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("15.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(2).getEffectiveDate()), 0);

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1862")
    public void testMultiPhasePlanPriceChangeInDiscountPhaseSubAlignedWithChange() throws Exception {

        final LocalDate today = new LocalDate(2023, 3, 18);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-monthly-discount3 has a 4-MONTH DISCOUNT phase followed by an EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(18));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-monthly-discount3"), null, null, null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to discount phase as per v1
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 18), new LocalDate(2023, 4, 18), InvoiceItemType.RECURRING, new BigDecimal("5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-04-18 invoice generated for discount phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 4, 18));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 18), new LocalDate(2023, 5, 18), InvoiceItemType.RECURRING, new BigDecimal("5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-18 - invoice generated for discount phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 18));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 18), new LocalDate(2023, 6, 18), InvoiceItemType.RECURRING, new BigDecimal("7")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

        //move clock to 2023-06-18 - invoice generated for discount phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 18));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 18), new LocalDate(2023, 7, 18), InvoiceItemType.RECURRING, new BigDecimal("7")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

        //move clock to 2023-07-18 - invoice generated for evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 7, 18));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 7, 18), new LocalDate(2023, 8, 18), InvoiceItemType.RECURRING, new BigDecimal("10.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1862")
    public void testMultiPhasePlanPriceChangeInDiscountPhaseSubNotAlignedWithChange() throws Exception {

        final LocalDate today = new LocalDate(2023, 3, 1);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-monthly-discount3 has a 4-MONTH DISCOUNT phase followed by an EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(18));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-monthly-discount3"), null, null, null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to discount phase as per v1
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 1), new LocalDate(2023, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-04-1 invoice generated for discount phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 4, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 1), new LocalDate(2023, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-1 - invoice generated for discount phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 1), new LocalDate(2023, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-06-1 - invoice generated for discount phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 1), new LocalDate(2023, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("7")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

        //move clock to 2023-07-1 - invoice generated for evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 7, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 7, 1), new LocalDate(2023, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("10.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1862")
    public void testMultiPhasePlanWithoutEffectiveDateForExistingSubs() throws Exception {

        final LocalDate today = new LocalDate(2023, 3, 18);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-monthly-discount2 has a a 14-day DISCOUNT phase followed by an EVERGREEN phase and no effectiveDateForExistingSubscriptions
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(18));

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-monthly-discount2"), null, null, null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to discount phase
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 18), new LocalDate(2023, 4, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-04-01 (end of discount phase) - invoice generated for evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 4, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 1), new LocalDate(2023, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-01 - invoice generated for evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 1), new LocalDate(2023, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-06-01 (v2 effective) - Invoice generated as per v1 since effectiveDateForExistingSubscriptions is not specified
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 1), new LocalDate(2023, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("6.99")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

    }
    
    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1862")
    public void testMultiPhasePlanChangePlanFromTrialToDiscount() throws Exception {

        final LocalDate today = new LocalDate(2023, 3, 18);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-monthly-trial has a 14-day TRIAL phase followed by an EVERGREEN phase
        // liability-monthly-discount has a 14-day DISCOUNT phase followed by an EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(18));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-monthly-trial"), null, null, null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to trial phase
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 18), new LocalDate(2023, 4, 1), InvoiceItemType.RECURRING, BigDecimal.ZERO));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-04-01 (end of trial phase) - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 4, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 1), new LocalDate(2023, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("4")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-01 - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 1), new LocalDate(2023, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("4")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-06-01 (v2 effective) - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 1), new LocalDate(2023, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("8.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);
        
        //change to liability-monthly-discount with END_OF_TERM policy
        bpEntitlement1.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-monthly-discount"), null, null, null), null, BillingActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), callContext);
        
        //move clock to 2023-07-01 (switched to liability-monthly-discount)  - invoice corresponding to evergreen phase of liability-monthly-discount as per v2 (since START_OF_SUBSCRIPTION change alignment is used, change is aligned with EVERGREEN phase)
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 7, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                             	new ExpectedInvoiceItemCheck(new LocalDate(2023, 7, 1), new LocalDate(2023, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("10.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);
        
        //move clock to 2023-08-01 (v3 effective) - invoice corresponding to evergreen phase of liability-monthly-discount as per v3
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 8, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 6, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 8, 1), new LocalDate(2023, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("15.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(2).getEffectiveDate()), 0);
    }
    
}
