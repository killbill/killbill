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
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class TestCatalogWithEffectiveDateForExistingSubscriptionsCustomConfigNonMonthlyPlans extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogWithEffectiveDateForExistingSubscriptionsCustomConfigNonMonthlyPlans");
        // Custom subscription config to test the alignment for the catalog effectiveDateForExistingSubscriptions
        allExtraProperties.put("org.killbill.subscription.align.effectiveDateForExistingSubscriptions", "true");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1867")
    public void testMultiPhasePlanWithWeeklyBilling() throws Exception {

        final LocalDate today = new LocalDate(2023, 5, 5);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-with-trial-weekly has a a 7-day TRIAL phase followed by a WEEKLY EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(5));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-with-trial-weekly"), null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to trial phase
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 5), new LocalDate(2023, 5, 12), InvoiceItemType.RECURRING, new BigDecimal("1.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-5-12 (end of trial phase) - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 5, 12));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 12), new LocalDate(2023, 5, 19), InvoiceItemType.RECURRING, new BigDecimal("4.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-19 - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 19));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 19), new LocalDate(2023, 5, 26), InvoiceItemType.RECURRING, new BigDecimal("4.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-26 (v2 effective) - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 26));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 26), new LocalDate(2023, 6, 2), InvoiceItemType.RECURRING, new BigDecimal("6.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1867")
    public void testMultiPhasePlanWithMonthlyTrialWeeklyEvergreen() throws Exception {

        final LocalDate today = new LocalDate(2023, 4, 5);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-monthly-trial-weekly-evergreen has a a 3-MONTH TRIAL phase followed by a WEEKLY EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(5));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-monthly-trial-weekly-evergreen"), null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to trial phase as per v1
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 5), new LocalDate(2023, 5, 5), InvoiceItemType.RECURRING, new BigDecimal("1.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-5-5 - invoice corresponding to trial phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 5));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 5), new LocalDate(2023, 6, 5), InvoiceItemType.RECURRING, new BigDecimal("1.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-6-5 - invoice corresponding to trial phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 5));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 5), new LocalDate(2023, 7, 5), InvoiceItemType.RECURRING, new BigDecimal("1.0")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

        //move clock to 2023-07-5 - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 7, 5));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 7, 5), new LocalDate(2023, 7, 12), InvoiceItemType.RECURRING, new BigDecimal("6.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

        //move clock to 2023-07-12 - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 7, 12));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 7, 12), new LocalDate(2023, 7, 19), InvoiceItemType.RECURRING, new BigDecimal("6.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

        //move clock to 2023-07-19 - invoice corresponding to evergreen phase as per v3
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 7, 19));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 6, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 7, 19), new LocalDate(2023, 7, 26), InvoiceItemType.RECURRING, new BigDecimal("8.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(2).getEffectiveDate()), 0);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1867")
    public void testMultiPhasePlanWithBiWeeklyBilling() throws Exception {

        final LocalDate today = new LocalDate(2023, 4, 17);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-with-trial-biweekly has a a 14-day TRIAL phase followed by a BIWEEKLY EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(5));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-with-trial-biweekly"), null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to trial phase
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 17), new LocalDate(2023, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("1.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-5-1 (end of trial phase) - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 5, 1));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 1), new LocalDate(2023, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("4.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-15 - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 15));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 15), new LocalDate(2023, 5, 29), InvoiceItemType.RECURRING, new BigDecimal("4.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-05-29 (v2 effective) - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 29));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 29), new LocalDate(2023, 6, 12), InvoiceItemType.RECURRING, new BigDecimal("6.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1867")
    public void testMultiPhasePlanWithMonthlyBilling() throws Exception {

        final LocalDate today = new LocalDate(2023, 3, 7);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        //
        // liability-with-trial-monthly has a 1-month TRIAL phase followed by a MONTHLY EVERGREEN phase
        //

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(7));
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("liability-with-trial-monthly"), null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), callContext);
        final Entitlement bpEntitlement1 = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        assertListenerStatus();
        assertNotNull(bpEntitlement1);

        //invoice corresponding to trial phase
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2023, 3, 7), new LocalDate(2023, 4, 7), InvoiceItemType.RECURRING, new BigDecimal("1.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-4-7 (end of trial phase) - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 4, 7));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 7), new LocalDate(2023, 5, 7), InvoiceItemType.RECURRING, new BigDecimal("4.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-5-7 - invoice corresponding to evergreen phase as per v1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 7));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 7), new LocalDate(2023, 6, 7), InvoiceItemType.RECURRING, new BigDecimal("4.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        //move clock to 2023-6-7 (v2 effective) - invoice corresponding to evergreen phase as per v2
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 7));
        assertListenerStatus();
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 7), new LocalDate(2023, 7, 7), InvoiceItemType.RECURRING, new BigDecimal("6.5")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

    }

}
