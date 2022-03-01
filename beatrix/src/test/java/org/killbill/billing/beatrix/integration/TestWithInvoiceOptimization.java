/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.features.KillbillFeatures;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import static org.killbill.billing.ErrorCode.INVOICE_NOTHING_TO_DO;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestWithInvoiceOptimization extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.putAll(DEFAULT_BEATRIX_PROPERTIES);
        allExtraProperties.put(KillbillFeatures.PROP_FEATURE_INVOICE_OPTIMIZATION, "true");
        return getConfigSource(null, allExtraProperties);
    }


    @Test(groups = "slow")
    public void testRecurringInAdvance() throws Exception {

        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2020-01-01T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // 2020-02-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("pistol-monthly-notrial");

        // Trigger a change way in the past (prior the cuttoff date)
        // org.killbill.invoice.maxInvoiceLimit= 1 month => cuttoff date = 2020-02-01
        //
        // We verify that invoice only tried to REPAIR from cutoff date -- and in particular the period 2020-01-15 - 2020-02-01 is left untouched.
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2, null, null, null), new LocalDate(2020, 1, 15), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("19.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 3, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("19.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 4, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 3, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("20.00")));

        // Issue a first dry-run on 2020-03-02
        // We should not see the items that were repaired a month prior now because of the cutoff date
        final DateTime nextDate1 = clock.getUTCNow().plusDays(1);
        checkNothingToInvoice(account.getId(), new LocalDate(nextDate1, testTimeZone), true);


        // Issue a series of dry-run starting on 2020-04-01
        DateTime nextDate = clock.getUTCNow().plusMonths(1);
        for (int i = 0; i < 5; i++) {
            final Invoice invoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), new LocalDate(nextDate, testTimeZone), new TestDryRunArguments(DryRunType.TARGET_DATE), callContext);
            // Filter to eliminate CBA
            final int actualRecurring = Iterables.size(Iterables.filter(invoice.getInvoiceItems(), new Predicate<InvoiceItem>() {
                @Override
                public boolean apply(final InvoiceItem invoiceItem) {
                    return invoiceItem.getInvoiceItemType() == InvoiceItemType.RECURRING;
                }
            }));
            //
            assertEquals(actualRecurring, 1);
            nextDate = nextDate.plusMonths(1);
        }
    }

    // Used to demonstrate what happens when maxInvoiceLimit = 0 and we do billing IN_ADVANCE
    @Test(groups = "slow")
    public void testRecurringInAdvance2() throws Exception {

        // Never fetch any existing invoices from disk during invoicing process
        invoiceConfig.setMaxInvoiceLimit(new Period("P0m"));

        clock.setTime(new DateTime("2020-01-01T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("pistol-monthly-notrial");

        // 2020-01-16
        clock.addDays(15);

        // Do a change one day in the past => Nothing will be generated
        // - System does not fetch any existing invoices as cutoffDt= 2020-01-16 and there are no invoices whose targetDt >= cutoffDt
        // - Proposed items correctly generate the 2 expected items but both are filtered because their startDt < cutoffDt
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.NULL_INVOICE);
        entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2, null, null, null), clock.getUTCToday().minusDays(1), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Do a change today => Only new item 2020-01-16 - 2020-02-01 gets generated
        // - System does not fetch any existing invoices as cutoffDt= 2020-01-16 and there are no invoices whose targetDt >= cutoffDt
        // - Proposed items correctly generate the 2 expected items but the first one is filtered because its start date  2020-01-15 < cutoffDt
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2, null, null, null), clock.getUTCToday(), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Double invoicing due to bad config! (no REPAIR)
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 16), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("10.30")));
    }



    @Test(groups = "slow")
    public void testRecurringInArrear() throws Exception {

        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2020-01-01T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("blowdart-in-arrear-monthly-notrial");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2020-02-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2020-04-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        // Cancel way in the past (prior the cuttoff date)
        // org.killbill.invoice.maxInvoiceLimit= 1 month => cuttoff date = 2020-03-01
        // NOTE that for IN_ARREAR a cuttoff date of 2020-03-01 returns items from 2020-02-01 - 2020-03-01
        //
        // We verify that invoice only tried to REPAIR from cutoff date -- and in particular the period 2020-01-15 - 2020-02-01 is left untouched.
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        entitlement.cancelEntitlementWithDate(new LocalDate(2020, 1, 15), true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 3, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 4, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 4, 1), new LocalDate(2020, 4, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("200.00")));
    }

    @Test(groups = "slow")
    public void testRecurringInArrear2() throws Exception {

        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2020-01-01T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("blowdart-in-arrear-monthly-notrial");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Create another subscription so we keep having something to invoice after cancellation of the first one
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId2 = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something2", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        // 2020-02-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));


        // Cancel in the past (previous period)
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        entitlement.cancelEntitlementWithDate(new LocalDate(2020, 1, 28), true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        // 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();


        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 3, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-12.90")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));


        // 2020-04-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();


        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        checkNothingToInvoice(account.getId(), new LocalDate(2020, 4, 1), false);

    }

    @Test(groups = "slow")
    public void testRecurringInArrear3() throws Exception {

        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2020-02-18T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("blowdart-in-arrear-monthly-notrial");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(12);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 18), new LocalDate(2020, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("41.38")));

        // 2020-04-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2020-04-01
        clock.addDays(15);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2020, 5, 1), callContext);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 4, 1), new LocalDate(2020, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

    }


    @Test(groups = "slow")
    public void testRecurringInArrear4() throws Exception {

        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2021-01-01T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);
        add_AUTO_INVOICING_REUSE_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("blowdart-in-arrear-monthly-notrial");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2021-02-01
        clock.addMonths(1);

        Invoice invoice = getCurrentDraftInvoice(account.getId(), null,10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 1, 1), new LocalDate(2021, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2021-03-01
        clock.addMonths(1);

        invoice = getCurrentDraftInvoice(account.getId(), null,10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 2, 1), new LocalDate(2021, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2021-04-01
        clock.addMonths(1);

        invoice = getCurrentDraftInvoice(account.getId(), null, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // Cancel on 2021-04-30
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        //busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.INVOICE);
        entitlement.cancelEntitlementWithDate(new LocalDate(2021, 4, 30), true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2021-05-01
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL);
        clock.addMonths(1);
        assertListenerStatus();

        invoice = getCurrentDraftInvoice(account.getId(), null, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 4, 1), new LocalDate(2021, 4, 30), InvoiceItemType.RECURRING, new BigDecimal("96.67")));

        // 2021-06-01 : Nothing to invoice
        clock.addMonths(1);
        assertListenerStatus();

        checkNothingToInvoice(account.getId(), new LocalDate(2021, 6, 1), false);

        // 2021-07-01  : Nothing to invoice
        clock.addMonths(1);
        assertListenerStatus();

        checkNothingToInvoice(account.getId(), new LocalDate(2021, 7, 1), false);
    }

    @Test(groups = "slow")
    public void testRecurringInArrear5() throws Exception {

        // If we want to catch the early cancelation, we need to set at least P1m (if not this is ignored)
        invoiceConfig.setMaxRawUsagePreviousPeriod(0);
        invoiceConfig.setZeroAmountUsageDisabled(true);
        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2020-01-01T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        // Don't allow system generated invoices
        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        // Reuse invoice until committed
        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);
        add_AUTO_INVOICING_REUSE_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("blowdart-in-arrear-monthly-notrial");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2020-02-01
        clock.addMonths(1);

        // Bill run on the 2020-02-01 with targetDate end of month (EOM)
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2020, 2, 28), callContext);

        Invoice invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 1;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2020-02-15
        clock.addDays(14);

        // Cancel in the past (previous period)
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        entitlement.cancelEntitlementWithDate(new LocalDate(2020, 2, 15), true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2020-03-01
        clock.addMonths(1);

        // Bill run on the 2020-02-01 with targetDate end of month (EOM)
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2020, 3, 31), callContext);

        invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 1;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 2, 15), InvoiceItemType.RECURRING, new BigDecimal("48.28")));

        checkNothingToInvoice(account.getId(), new LocalDate(2020, 3, 1), false);

    }



        //
    //  Usage tests as maxInvoiceLimit also affects USAGE generation -- we only have a partial view of existing invoices.
    //  Both maxInvoiceLimit and readMaxRawUsagePreviousPeriod need to be in sync
    //
    @Test(groups = "slow")
    public void testUsageInArrear1() throws Exception {

        // This setting also affects the USAGE items as it filters what we read from disk
        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setDay(new LocalDate(2021, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Base Plan required to get an add-on with usage
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        // AO subscription
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", new LocalDate(2021, 4, 1), 99L, callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2021, 4, 15), 100L, callContext);

        // 2020-05-01
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2021, 5, 1), new LocalDate(2022, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-1", "tracking-2"), internalCallContext);

        // $0 invoice
        // 2020-06-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2021, 5, 1), new LocalDate(2021, 6, 1), InvoiceItemType.USAGE, BigDecimal.ZERO));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of(), internalCallContext);

        recordUsageData(aoSubscription.getId(), "tracking-3", "bullets", new LocalDate(2021, 6, 1), 50L, callContext);
        recordUsageData(aoSubscription.getId(), "tracking-4", "bullets", new LocalDate(2021, 6, 16), 300L, callContext);

        // We have 2 conflicting properties (on purpose) for this test that will lead to re-invoice the usage on month 2021-04-01 -> 2021-05-01:
        // - org.killbill.invoice.maxInvoiceLimit= 1 -> meaning usage code does not see such invoice
        // - org.killbill.invoice.readMaxRawUsagePreviousPeriod = 2 -> meaning we pull usage data from back to 2021-04-01
        //

        // 2020-07-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2021, 6, 1), new LocalDate(2021, 7, 1), InvoiceItemType.USAGE, new BigDecimal("11.80")),
                                                 // Re-invoiced.
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-3", "tracking-4"), internalCallContext);
    }


    // Same test as testUsageInArrear1 but no double USAGE billing
    // We set both setMaxInvoiceLimit = P2m and readMaxRawUsagePreviousPeriod = 2
    @Test(groups = "slow")
    public void testUsageInArrear2() throws Exception {

        // This setting also affects the USAGE items as it filters what we read from disk
        invoiceConfig.setMaxInvoiceLimit(new Period("P2m"));

        clock.setDay(new LocalDate(2021, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Base Plan required to get an add-on with usage
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        // AO subscription
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", new LocalDate(2021, 4, 1), 99L, callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2021, 4, 15), 100L, callContext);

        // 2020-05-01
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2021, 5, 1), new LocalDate(2022, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-1", "tracking-2"), internalCallContext);

        // $0 invoice
        // 2020-06-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2021, 5, 1), new LocalDate(2021, 6, 1), InvoiceItemType.USAGE, BigDecimal.ZERO));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of(), internalCallContext);

        recordUsageData(aoSubscription.getId(), "tracking-3", "bullets", new LocalDate(2021, 6, 1), 50L, callContext);
        recordUsageData(aoSubscription.getId(), "tracking-4", "bullets", new LocalDate(2021, 6, 16), 300L, callContext);

        // 2020-07-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2021, 6, 1), new LocalDate(2021, 7, 1), InvoiceItemType.USAGE, new BigDecimal("11.80")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-3", "tracking-4"), internalCallContext);


        recordUsageData(aoSubscription.getId(), "tracking-5", "bullets", new LocalDate(2021, 7, 2), 40L, callContext);
        recordUsageData(aoSubscription.getId(), "tracking-6", "bullets", new LocalDate(2021, 7, 18), 310L, callContext);

        // 2020-08-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2021, 7, 1), new LocalDate(2021, 8, 1), InvoiceItemType.USAGE, new BigDecimal("11.80")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-5", "tracking-6"), internalCallContext);

    }



    //
    // 'Bill Run' types of scenarios where we generate invoices on the 1st with subscriptions not aligned on the 1st
    //
    @Test(groups = "slow")
    public void testBillRunInAdvance() throws Exception {

        // Set P1m to look one month back from NOW
        // E.g bill date = 2020-03-01 -> cutoff = 2020-02-01 and so any proposed item with startDt > 2020-02-01  will not be filtered out
        //     and we end up generating 2020-02-15 -> 2020-03-15
        // Notes: Setting P0m would not work with such a scenario
        //
        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2020-01-15T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(15));
        assertNotNull(account);

        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);
        add_AUTO_INVOICING_REUSE_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2020-02-01
        clock.addDays(17);

        Invoice invoice = getCurrentDraftInvoice(account.getId(),null,  10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 15), new LocalDate(2020, 2, 15), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // 2020-03-01
        clock.addMonths(1);


        invoice = getCurrentDraftInvoice(account.getId(), null, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 15), new LocalDate(2020, 3, 15), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // 2020-04-01
        clock.addMonths(1);

        invoice = getCurrentDraftInvoice(account.getId(), null, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 15), new LocalDate(2020, 4, 15), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

    }

    @Test(groups = "slow")
    public void testBillRunInAdvanceLeadingProration() throws Exception {

        // Set P1m to look one month back from NOW
        // Very interesting, we cannot set P0m otherwise the Draft invoice isn not being returned as part of the
        // existing invoices and we end up with 2 Draft invoices... ah ah...
        //
        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2020-01-15T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);
        add_AUTO_INVOICING_REUSE_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Check initial invoice got created
        Invoice invoice = getCurrentDraftInvoice(account.getId(), null, 10);

        // 2020-02-01
        clock.addDays(17);

        invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            // The first item is generated immediately as we create the subscription and the second as we move the clock but we have no event to sync on because of AUTO_INVOICING_DRAFT
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 2;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        // We see the leading pro-ration + the in-advance full period
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 15), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("16.42")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));


        // 2020-03-01
        clock.addMonths(1);


        invoice = getCurrentDraftInvoice(account.getId(), null, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // 2020-04-01
        clock.addMonths(1);

        invoice = getCurrentDraftInvoice(account.getId(), null, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 4, 1), new LocalDate(2020, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

    }



    @Test(groups = "slow")
    public void testBillRunInArrear() throws Exception {

        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2021-01-15T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(15));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("blowdart-in-arrear-monthly-notrial");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2021-02-01
        clock.addDays(16);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2021, 2, 28), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 1, 15), new LocalDate(2021, 2, 15), InvoiceItemType.RECURRING, new BigDecimal("100.00")));


        // 2021-03-01
        clock.addMonths(1);

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2021, 3, 31), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 2, 15), new LocalDate(2021, 3, 15), InvoiceItemType.RECURRING, new BigDecimal("100.00")));


        // 2021-04-01
        clock.addMonths(1);

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE, NextEvent.INVOICE,  NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2021, 4, 30), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 3, 15), new LocalDate(2021, 4, 15), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

    }



    @Test(groups = "slow")
    public void testBillRunInArrearLeadingProration() throws Exception {

        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2021-01-15T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("blowdart-in-arrear-monthly-notrial");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2021-02-01
        clock.addDays(16);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2021, 2, 28), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 1, 15), new LocalDate(2021, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("54.84")));

        // 2021-03-01
        clock.addMonths(1);

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2021, 3, 31), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 2, 1), new LocalDate(2021, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2021-04-01
        clock.addMonths(1);

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE, NextEvent.INVOICE,  NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2021, 4, 30), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

    }


    @Test(groups = "slow")
    public void testBillRunInArrearWithUsageAndRecurring() throws Exception {

        // Usage records are pulled using the most aggressive cuttoffDt
        // 1. Because we set zeroAmountUsageDisabled=true, we don't rely on existing state (i.e the latest endDate of all usage items) to find the starting point of the cuttoffDt
        // but instead, we default to 1 period (month) prior the date of the bill run
        // 2. Because maxRawUsagePreviousPeriod=0, we don't go past the cuttoffDt computed in 1
        // So, for bill run= Feb 1st -> (usage) cuttoffDt = Jan 1st - anything prior that will be ignored.
        //
        invoiceConfig.setMaxRawUsagePreviousPeriod(0);
        invoiceConfig.setZeroAmountUsageDisabled(true);
        // We make sure that our invoice optimization cuttOffDt is not greater than the usage optimization date, otherwise this could create issues
        // as reflected by the WARN https://github.com/killbill/killbill/blob/killbill-0.22.27/invoice/src/main/java/org/killbill/billing/invoice/generator/UsageInvoiceItemGenerator.java#L131
        //
        // To make it simpler we keep both cuttoffDt on the same value, but we could set any value, e.g P1m, P2m, ...
        // So, for bill run= Feb 1st -> invoice cuttOffDt = Jan 1st
        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2021-01-15T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(15));
        assertNotNull(account);

        // Don't allow system generated invoices
        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        // Reuse invoice until committed
        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);
        add_AUTO_INVOICING_REUSE_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);


        // Create an in-arrear RECURRING subscription
        final LocalDate effDt1 = new LocalDate(2020, 8, 15);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BCD_CHANGE);
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("blowdart-in-arrear-monthly-notrial");
        final UUID entitlementId1 = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec1, 15, null, null), null, effDt1, effDt1, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Subscription sub1 = subscriptionApi.getSubscriptionForEntitlementId(entitlementId1, callContext);
        assertListenerStatus();

        // Create an in-arrear USAGE subscription
        final LocalDate effDt2 = new LocalDate(2020, 9, 1);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BCD_CHANGE);
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("training-usage-in-arrear");
        final UUID entitlementId2 = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec2, 1, null, null), null, effDt2, effDt2, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Subscription sub2 = subscriptionApi.getSubscriptionForEntitlementId(entitlementId2, callContext);
        assertListenerStatus();

        // Generate some usage data for past months
        recordUsageData(sub2.getId(), "tracking-old-1", "hours", new LocalDate(2020, 9, 19), 1L, callContext);
        recordUsageData(sub2.getId(), "tracking-old-2", "hours", new LocalDate(2020, 10, 19), 1L, callContext);
        recordUsageData(sub2.getId(), "tracking-old-3", "hours", new LocalDate(2020, 11, 19), 1L, callContext);
        recordUsageData(sub2.getId(), "tracking-old-4", "hours", new LocalDate(2020, 12, 19), 1L, callContext);

        // Generate usage data for this month
        recordUsageData(sub2.getId(), "tracking-1", "hours", new LocalDate(2021, 1, 19), 1L, callContext);

        // 2021-02-01
        // (Hum... Interestingly, there is no future notification set on the 1st although sub2#BCD is the 1st...)
        clock.addDays(17);
        Thread.sleep(1000);

        // Bill run on the 2021-02-01 with targetDate end of month (EOM)
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2021, 2, 28), callContext);

        Invoice invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 3;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        // The original invoice is a catchup invoice since there is no invoice and both subscriptions started way in the past
        // However, with our aggressive cuttoffDt, we only see what we would normally expect to see on this invoice if we had invoiced up to this point
        // (The catchup invoice already contains everything it should have and nothing more)
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 12, 15), new LocalDate(2021, 1, 15), InvoiceItemType.RECURRING, new BigDecimal("100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 1, 15), new LocalDate(2021, 2, 15), InvoiceItemType.RECURRING, new BigDecimal("100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 1, 1), new LocalDate(2021, 2, 1), InvoiceItemType.USAGE, new BigDecimal("100.00")));

        // 2021-02-15
        // Invoice notification on the 15 (Recurring subscription)
        clock.addDays(14);
        Thread.sleep(1000);


        recordUsageData(sub2.getId(), "tracking-2-a", "hours", new LocalDate(2021, 2, 15), 1L, callContext);

        // 2021-03-01
        clock.addDays(14);
        Thread.sleep(1000);

        recordUsageData(sub2.getId(), "tracking-2-b", "hours", new LocalDate(2021, 2, 25), 1L, callContext);


        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2021, 3, 31), callContext);

        invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 2;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();


        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 2, 15), new LocalDate(2021, 3, 15), InvoiceItemType.RECURRING, new BigDecimal("100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 2, 1), new LocalDate(2021, 3, 1), InvoiceItemType.USAGE, new BigDecimal("200.00")));

        // 2021-03-15
        // Invoice notification on the 15 (Recurring subscription)
        clock.addDays(14);
        Thread.sleep(1000);


        recordUsageData(sub2.getId(), "tracking-3", "hours", new LocalDate(2021, 3, 15), 1L, callContext);

        // 2021-04-01
        clock.addDays(17);
        Thread.sleep(1000);

        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2021, 4, 30), callContext);

        invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 2;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();


        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 3, 15), new LocalDate(2021, 4, 15), InvoiceItemType.RECURRING, new BigDecimal("100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 3, 1), new LocalDate(2021, 4, 1), InvoiceItemType.USAGE, new BigDecimal("100.00")));


        // 2021-04-15
        // Invoice notification on the 15 (Recurring subscription)
        clock.addDays(14);
        Thread.sleep(1000);

        recordUsageData(sub2.getId(), "tracking-4", "hours", new LocalDate(2021, 4, 15), 1L, callContext);

        // 2021-05-01
        clock.addDays(16);
        Thread.sleep(1000);

        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2021, 5, 31), callContext);

        invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 2;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();


        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 4, 15), new LocalDate(2021, 5, 15), InvoiceItemType.RECURRING, new BigDecimal("100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 4, 1), new LocalDate(2021, 5, 1), InvoiceItemType.USAGE, new BigDecimal("100.00")));



    }


    @Test(groups = "slow")
    public void testBillRunInArrearWithPastCancelation() throws Exception {

        invoiceConfig.setMaxRawUsagePreviousPeriod(0);
        invoiceConfig.setZeroAmountUsageDisabled(true);
        invoiceConfig.setMaxInvoiceLimit(new Period("P1m"));

        clock.setTime(new DateTime("2020-01-01T3:56:02"));

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));
        assertNotNull(account);

        // Don't allow system generated invoices
        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        // Reuse invoice until committed
        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);
        add_AUTO_INVOICING_REUSE_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("blowdart-in-arrear-monthly-notrial");
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Create another subscription so we keep having something to invoice after cancellation of the first one
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId2 = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something2", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2020-02-01
        clock.addMonths(1);


        // Bill run on the 2020-02-01 with targetDate end of month (EOM)
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2020, 2, 28), callContext);

        Invoice invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 2;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));



        // Cancel (full repair)
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        entitlement.cancelEntitlementWithDate(new LocalDate(2020, 1, 1), true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2020-03-01
        clock.addMonths(1);

        // Bill run on the 2020-03-01 with targetDate end of month (EOM)
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2020, 3, 31), callContext);

        invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 2;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 2, 1), new LocalDate(2020, 3, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

        // 2020-04-01
        clock.addMonths(1);

        // Bill run on the 2020-03-01 with targetDate end of month (EOM)
        //
        // We verify that with invoice optimization ON, only the last invoice will be 'seen' and although it contains a 'dangling' REPAIR_ADJ
        // this is correctly handled by the system -- InvoicePruner correctly discards such item prior we enter the tree.
        //
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2020, 4, 30), callContext);

        invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 1;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 3, 1), new LocalDate(2020, 4, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));


        // 2020-05-01
        clock.addMonths(1);

        // Bill run on the 2020-03-01 with targetDate end of month (EOM)
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2020, 5, 31), callContext);

        invoice = getCurrentDraftInvoice(account.getId(), new Function<Invoice, Boolean>() {
            @Override
            public Boolean apply(final Invoice invoice) {
                return invoice.getInvoiceItems().size() == 1;
            }
        }, 10);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.commitInvoice(invoice.getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 4, 1), new LocalDate(2020, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("100.00")));


    }


    private void checkNothingToInvoice(final UUID accountId, final LocalDate targetDate, final boolean isDryRun) {
        try {
            if (isDryRun) {
                invoiceUserApi.triggerDryRunInvoiceGeneration(accountId, targetDate, new TestDryRunArguments(DryRunType.TARGET_DATE), callContext);
            } else {
                busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
                invoiceUserApi.triggerInvoiceGeneration(accountId, targetDate, callContext);
            }
            Assert.fail("Should not generate any invoice");
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), INVOICE_NOTHING_TO_DO.getCode());
        } finally {
            assertListenerStatus();
        }
    }


    private Invoice getCurrentDraftInvoice(final UUID accountId, @Nullable final Function<Invoice, Boolean> condFn, final int nbTries) {
        int curTry = nbTries;
        while (curTry-- > 0) {
            final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId, false, false, callContext);
            if (invoices.size() > 0) {
                final Invoice lastInvoice = invoices.get(invoices.size() - 1);
                if (lastInvoice.getStatus() == InvoiceStatus.DRAFT &&
                    (condFn == null || condFn.apply(lastInvoice))) /* If we have a condition, let's check it satisfies */ {
                    return lastInvoice;
                }
            }
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Assert.fail(e.getMessage());
            }
        }
        Assert.fail("Failed to find draft invoice for account");
        return null;
    }

}

