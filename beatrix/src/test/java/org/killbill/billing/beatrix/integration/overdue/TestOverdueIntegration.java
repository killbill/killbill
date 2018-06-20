/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.beatrix.integration.overdue;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.api.FlakyRetryAnalyzer;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.BeatrixIntegrationModule;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.BlockingApiException;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

// For all the tests, we set the the property org.killbill.payment.retry.days=8,8,8,8,8,8,8,8 so that Payment retry logic does not end with an ABORTED state
// preventing final instant payment to succeed.
//
// The tests are difficult to follow because there are actually two tracks of retry in logic:
// - The payment retries
// - The overdue notifications
//

@Test(groups = "slow")
public class TestOverdueIntegration extends TestOverdueBase {

    @Override
    public String getOverdueConfig() {
        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>30</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD3\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>50</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD3</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                                 "       </state>" +
                                 "       <state name=\"OD2\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>40</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD2</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>10</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>30</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>10</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";

        return configXml;
    }

    @Test(groups = "slow", description = "Test overdue stages and return to clear prior to CTD")
    public void testOverdueStages1() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // 2012-06-08 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-16 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-24 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-30 => P1
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // 2012-07-02 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");

        // 2012-07-08 => Retry P1
        addDaysAndCheckForCompletion(6, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");

        // 2012-07-10 => Retry P0
        //
        // This is the first stage that will block the billing (and entitlement).
        //
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK, NextEvent.TAG, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-16 => Retry P1
        addDaysAndCheckForCompletion(6, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-18 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-20
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD3");

        allowPaymentsAndResetOverdueToClearByPayingAllUnpaidInvoices(false);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 10), new LocalDate(2012, 7, 20), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-80.63")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 20), InvoiceItemType.CBA_ADJ, new BigDecimal("80.63")));

        // Add 11 days to generate next invoice. We verify that we indeed have a notification for nextBillingDate
        addDaysAndCheckForCompletion(11, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 31), new LocalDate(2012, 8, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 31), new LocalDate(2012, 7, 31), InvoiceItemType.CBA_ADJ, new BigDecimal("-80.63")));

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 8, 31), callContext);

        // Verify the account balance is now 0
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow", description = "Test overdue stages and return to clear on CTD")
    public void testOverdueStages2() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // 2012-06-08 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-16 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-24 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-30 => P1
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // 2012-07-02 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");

        // 2012-07-08 => Retry P1
        addDaysAndCheckForCompletion(6, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");

        // 2012-07-10 => Retry P0
        //
        // This is the first stage that will block the billing (and entitlement).
        //
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK, NextEvent.TAG, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-16 => Retry P1
        addDaysAndCheckForCompletion(6, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-18 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-20
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD3");

        // 2012-07-24 => Retry P1
        addDaysAndCheckForCompletion(4, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // 2012-07-26 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        // 2012-07-31 => No NEW INVOICE because OD2 -> still blocked
        addDaysAndCheckForCompletion(5);
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // Make sure the 'invoice-service:next-billing-date-queue' gets processed before we continue and since we are in AUTO_INVOICING_OFF
        // no event (NULL_INVOICE) will be generated and so we can't synchronize on any event, and we need to add a small amount of sleep
        Thread.sleep(1000);

        allowPaymentsAndResetOverdueToClearByPayingAllUnpaidInvoices(true);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    // New invoice for the partial period
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 31), new LocalDate(2012, 8, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 10), new LocalDate(2012, 7, 31), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-169.32")));

        // Move one month ahead, and check if we get the next invoice
        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 8, 31), new LocalDate(2012, 9, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // Verify the account balance is now 0
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow", description = "Test overdue stages and return to clear after CTD")
    public void testOverdueStages3() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // 2012-06-08 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-16 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-24 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-30
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // 2012-07-02 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");

        // 2012-07-08 => Retry P1
        addDaysAndCheckForCompletion(6, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");

        // 2012-07-10 => Retry P0
        //
        // This is the first stage that will block the billing (and entitlement).
        //
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK, NextEvent.TAG, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-16 => Retry P1
        addDaysAndCheckForCompletion(6, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-18 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-20
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD3");

        // 2012-07-24 => Retry P1
        addDaysAndCheckForCompletion(4, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // 2012-07-26 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        // 2012-07-31 => No NEW INVOICE because OD2 -> still blocked
        addDaysAndCheckForCompletion(5);
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // 2012-08-01 => Retry P1
        addDaysAndCheckForCompletion(1, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        allowPaymentsAndResetOverdueToClearByPayingAllUnpaidInvoices(true);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    // New invoice for the partial period
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 8, 1), new LocalDate(2012, 8, 31), InvoiceItemType.RECURRING, new BigDecimal("241.89")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 10), new LocalDate(2012, 7, 31), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-169.32")));

        // Move one month ahead, and check if we get the next invoice
        addDaysAndCheckForCompletion(30, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 8, 31), new LocalDate(2012, 9, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // Verify the account balance is now 0
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.ZERO), 0);
    }

    //
    // This test is similar to the previous one except that instead of moving the clock to check we will get the next invoice
    // at the end, we carry a change of plan.
    //
    @Test(groups = "slow", description = "Test overdue stages and follow with an immediate change of plan")
    public void testOverdueStagesFollowedWithImmediateChange1() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // 2012-06-08 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-16 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-24 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-30
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // 2012-07-02 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");

        // 2012-07-08 => Retry P1
        addDaysAndCheckForCompletion(6, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");

        // 2012-07-10 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK, NextEvent.TAG, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-16 => Retry P1
        addDaysAndCheckForCompletion(6, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-18 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-20
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD3");

        allowPaymentsAndResetOverdueToClearByPayingAllUnpaidInvoices(false);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 10), new LocalDate(2012, 7, 20), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-80.63")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 20), InvoiceItemType.CBA_ADJ, new BigDecimal("80.63")));

        // Do an upgrade now
        checkChangePlanWithOverdueState(baseEntitlement, false, true);

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 10), new LocalDate(2012, 7, 20), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-80.63")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 20), InvoiceItemType.CBA_ADJ, new BigDecimal("80.63")));

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    // Item for the upgraded recurring plan
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("212.89")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 31), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-88.69")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 20), InvoiceItemType.CBA_ADJ, new BigDecimal("-80.63")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // Verify the account balance is now 0
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow", description = "Test overdue stages and follow with an immediate change of plan and use of credit")
    public void testOverdueStagesFollowedWithImmediateChange2() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2013, 5, 31), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2013, 5, 31), callContext);

        // 2012-06-08 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-16 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-24 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-30 => OD1
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK);
        checkODState("OD1");

        // 2012-07-02 => Retry P0
        addDaysAndCheckForCompletion(2, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");

        // 2012-07-10 => Retry P0 & transition to OD2
        addDaysAndCheckForCompletion(8, NextEvent.BLOCK, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.TAG);
        checkODState("OD2");

        // 2012-07-18 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2");

        // 2012-07-20 => OD3
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD3");

        allowPaymentsAndResetOverdueToClearByPayingAllUnpaidInvoices(false);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2013, 5, 31), InvoiceItemType.RECURRING, new BigDecimal("2399.95")));

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 10), new LocalDate(2012, 7, 20), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-65.75")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 31), new LocalDate(2013, 5, 31), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-1998.86")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 20), InvoiceItemType.CBA_ADJ, new BigDecimal("2064.61")));

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2013, 5, 31), callContext);

        // Move to 2012-07-31 and make a change of plan
        addDaysAndCheckForCompletion(11);
        checkChangePlanWithOverdueState(baseEntitlement, false, false);

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 31), new LocalDate(2012, 8, 31), InvoiceItemType.RECURRING, new BigDecimal("599.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 31), new LocalDate(2012, 7, 31), InvoiceItemType.CBA_ADJ, new BigDecimal("-599.95")));

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 8, 31), callContext);
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(new BigDecimal("-1464.66")), 0);
    }

    @Test(groups = "slow", description = "Test overdue stages with missing payment method")
    public void testOverdueStateIfNoPaymentMethod() throws Exception {
        // This test is similar to the previous one - but there is no default payment method on the account, so there
        // won't be any payment retry

        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Make sure the account doesn't have any payment method
        accountInternalApi.removePaymentMethod(account.getId(), internalCallContext);

        // Create subscription
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial before first payment. An invoice payment error, one for each invoice, should be on the bus (because there is no payment method)
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-15 => DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-30 => DAY 65 - 30 days after invoice
        addDaysAndCheckForCompletion(15, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // Now we should be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);

        // 2012-07-07 => DAY 67 - 37 days after invoice
        addDaysAndCheckForCompletion(2);

        // Should still be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);

        // 2012-07-10 => DAY 75 - 40 days after invoice
        addDaysAndCheckForCompletion(8, NextEvent.BLOCK, NextEvent.TAG);

        // Should now be in OD2
        checkODState("OD2");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);

        // 2012-07-20 => DAY 85 - 50 days after invoice
        addDaysAndCheckForCompletion(10, NextEvent.BLOCK);

        // Should now be in OD3
        checkODState("OD3");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);

        // Add a payment method and set it as default
        paymentApi.addPaymentMethod(account, UUID.randomUUID().toString(), BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME, true, paymentMethodPlugin, PLUGIN_PROPERTIES, callContext);

        allowPaymentsAndResetOverdueToClearByPayingAllUnpaidInvoices(false);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    // Item for the upgraded recurring plan
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    // Item for the blocked period
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 10), new LocalDate(2012, 7, 20), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-80.63")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 20), InvoiceItemType.CBA_ADJ, new BigDecimal("80.63")));

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        checkChangePlanWithOverdueState(baseEntitlement, false, true);

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    // Item for the blocked period
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 10), new LocalDate(2012, 7, 20), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-80.63")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 20), InvoiceItemType.CBA_ADJ, new BigDecimal("80.63")));

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("212.89")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 31), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-88.69")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 20), new LocalDate(2012, 7, 20), InvoiceItemType.CBA_ADJ, new BigDecimal("-80.63")));

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow", description = "Test overdue for draft external charge")
    public void testShouldNotBeInOverdueAfterDraftExternalCharge() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Create a subscription without failing payments
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-06 => Create an external charge on a new invoice
        addDaysAndCheckForCompletion(5);
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, account.getId(), bundle.getId(), "For overdue", new LocalDate(2012, 5, 6), new LocalDate(2012, 6, 6), BigDecimal.TEN, Currency.USD, null);
        invoiceUserApi.insertExternalCharges(account.getId(), clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), false, null, callContext).get(0);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 6), new LocalDate(2012, 6, 6), InvoiceItemType.EXTERNAL_CHARGE, BigDecimal.TEN));

        // 2012-05-31 => DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(25, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state - the invoice for the bundle has been paid, but not the invoice with the external charge (because it is in draft mode)
        // We refresh overdue just to be safe, see below
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-06 => Past 30 days since the external charge
        addDaysAndCheckForCompletion(6);
        // We should still be clear
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        Assert.assertEquals(invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).size(), 0);
    }

    @Test(groups = "slow", description = "Test overdue after refund with no adjustment")
    public void testShouldBeInOverdueAfterRefundWithoutAdjustment() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Create subscription and don't fail payments
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-15 => DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-07-05 => DAY 65 - 35 days after invoice
        addDaysAndCheckForCompletion(20, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // Now, refund the second (first non-zero dollar) invoice
        final Payment payment = paymentApi.getPayment(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).get(1).getPayments().get(0).getPaymentId(), false, false, PLUGIN_PROPERTIES, callContext);
        refundPaymentAndCheckForCompletion(account, payment, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        // We should now be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);
    }

    @Test(groups = "slow", description = "Test overdue after chargeback")
    public void testShouldBeInOverdueAfterChargeback() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Create subscription and don't fail payments
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-15 => DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-07-05 => DAY 65 - 35 days after invoice
        addDaysAndCheckForCompletion(20, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // Now, create a chargeback for the second (first non-zero dollar) invoice
        final InvoicePayment invoicePayment = invoicePaymentApi.getInvoicePayments(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).get(1).getPayments().get(0).getPaymentId(), callContext).get(0);
        Payment payment = paymentApi.getPayment(invoicePayment.getPaymentId(), false, false, ImmutableList.<PluginProperty>of(), callContext);
        payment = createChargeBackAndCheckForCompletion(account, payment, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        // We should now be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);

        // Reverse the chargeback
        createChargeBackReversalAndCheckForCompletion(account, payment, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.BLOCK);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);
    }

    @Test(groups = "slow", description = "Test overdue clear after external payment")
    public void testOverdueStateShouldClearAfterExternalPayment() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-15 => DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-07-05 => DAY 65 - 35 days after invoice
        addDaysAndCheckForCompletion(20, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // Now we should be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);

        // We have two unpaid non-zero dollar invoices at this point
        // Pay the first one via an external payment - we should then be 5 days apart from the second invoice
        // (which is the earliest unpaid one) and hence come back to a clear state (see configuration)
        paymentPlugin.makeAllInvoicesFailWithError(false);
        final Invoice firstNonZeroInvoice = invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).iterator().next();
        createExternalPaymentAndCheckForCompletion(account, firstNonZeroInvoice, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        // We should be clear now
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);
    }

    @Test(groups = "slow", description = "Test overdue clear after item adjustment")
    public void testOverdueStateShouldClearAfterCreditOrInvoiceItemAdjustment() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // DAY 65 - 35 days after invoice
        addDaysAndCheckForCompletion(20, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // Now we should be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);

        // We have two unpaid non-zero dollar invoices at this point
        // Adjust the first (and only) item of the first invoice - we should then be 5 days apart from the second invoice
        // (which is the earliest unpaid one) and hence come back to a clear state (see configuration)
        final Invoice firstNonZeroInvoice = invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).iterator().next();
        fullyAdjustInvoiceItemAndCheckForCompletion(account, firstNonZeroInvoice, 1, NextEvent.BLOCK, NextEvent.INVOICE_ADJUSTMENT);
        // We should be clear now
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        invoiceChecker.checkInvoice(account.getId(), 2,
                                    callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 5, 31), InvoiceItemType.ITEM_ADJ, new BigDecimal("-249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // DAY 70 - 10 days after second invoice
        addDaysAndCheckForCompletion(5);

        // We should still be clear
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // DAY 80 - 20 days after second invoice
        addDaysAndCheckForCompletion(10, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        // We should still be clear
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // DAY 95 - 35 days after second invoice
        addDaysAndCheckForCompletion(15, NextEvent.BLOCK, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        // We should now be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);

        invoiceChecker.checkInvoice(account.getId(), 4, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 31), new LocalDate(2012, 8, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // Fully adjust all invoices
        final List<Invoice> invoicesToAdjust = getUnpaidInvoicesOrderFromRecent();
        for (int i = 0; i < invoicesToAdjust.size(); i++) {
            if (i == invoicesToAdjust.size() - 1) {
                fullyAdjustInvoiceAndCheckForCompletion(account, invoicesToAdjust.get(i), NextEvent.BLOCK, NextEvent.INVOICE_ADJUSTMENT);
            } else {
                fullyAdjustInvoiceAndCheckForCompletion(account, invoicesToAdjust.get(i), NextEvent.INVOICE_ADJUSTMENT);
            }
        }

        // We should be cleared again
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);
    }

    // Flaky, see https://github.com/killbill/killbill/issues/782
    @Test(groups = "slow", description = "Test overdue state with number of unpaid invoices condition", retryAnalyzer = FlakyRetryAnalyzer.class)
    public void testOverdueStateWithNumberOfUnpaidInvoicesCondition() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final DefaultOverdueConfig config = XMLLoader.getObjectFromString(Resources.getResource("overdueWithNumberOfUnpaidInvoicesCondition.xml").toExternalForm(), DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        setupAccount();

        paymentPlugin.makeAllInvoicesFailWithError(true);

        createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE,
                                                   term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2012-05-31 => DAY 30 have to get out of trial before first payment
        addMonthsAndCheckForCompletion(1, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // Verify that number of unpaid invoices is 1
        Assert.assertEquals(invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).size(), 1);
        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // Add 1 month
        addMonthsAndCheckForCompletion(1, NextEvent.INVOICE, NextEvent.BLOCK, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // Verify that number of unpaid invoices is 2
        Assert.assertEquals(invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).size(), 2);
        // Now we should be in OD1
        checkODState("OD1");

        // Add 1 month
        addMonthsAndCheckForCompletion(1, NextEvent.INVOICE, NextEvent.BLOCK, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // Verify that number of unpaid invoices is 3
        Assert.assertEquals(invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).size(), 3);
        // Now we should be in OD2
        checkODState("OD2");

        // Add 1 month
        addMonthsAndCheckForCompletion(1, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // Verify that number of unpaid invoices is 4
        Assert.assertEquals(invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).size(), 4);
        // We should still be in OD2
        checkODState("OD2");

        // Add 1 month
        addMonthsAndCheckForCompletion(1, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.TAG,  NextEvent.BLOCK);
        // Verify that number of unpaid invoices is 5
        Assert.assertEquals(invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).size(), 5);
        // Now we should be in OD3
        checkODState("OD3");

        // Get all unpaid invoices and pay them to clear the overdue state
        paymentPlugin.makeAllInvoicesFailWithError(false);
        List<Invoice> unpaidInvoices = getUnpaidInvoicesOrderFromRecent();
        createPaymentAndCheckForCompletion(account, unpaidInvoices.get(0), NextEvent.BLOCK, NextEvent.TAG, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        createPaymentAndCheckForCompletion(account, unpaidInvoices.get(1), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        createPaymentAndCheckForCompletion(account, unpaidInvoices.get(2), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        createPaymentAndCheckForCompletion(account, unpaidInvoices.get(3), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        createPaymentAndCheckForCompletion(account, unpaidInvoices.get(4), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // We should be clear now
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);
    }

    // Flaky, see https://github.com/killbill/killbill/issues/782
    @Test(groups = "slow", description = "Test overdue state with total unpaid invoice balance condition", retryAnalyzer = FlakyRetryAnalyzer.class)
    public void testOverdueStateWithTotalUnpaidInvoiceBalanceCondition() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final DefaultOverdueConfig config = XMLLoader.getObjectFromString(Resources.getResource("overdueWithTotalUnpaidInvoiceBalanceCondition.xml").toExternalForm(), DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        setupAccount();

        paymentPlugin.makeAllInvoicesFailWithError(true);

        createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE,
                                                   term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // 2012-05-31 => DAY 30 have to get out of trial before first payment
        addMonthsAndCheckForCompletion(1, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        // Amount balance should be USD 249.95
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.valueOf(249.95)), 0);
        // Should still be in clear state
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // Add 1 month
        addMonthsAndCheckForCompletion(1, NextEvent.INVOICE, NextEvent.BLOCK, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // Amount balance should be USD 499.90
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.valueOf(499.90)), 0);
        // Now we should be in OD1
        checkODState("OD1");

        // Add 1 month
        addMonthsAndCheckForCompletion(1, NextEvent.INVOICE, NextEvent.BLOCK, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // Amount balance should be USD 749.85
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.valueOf(749.85)), 0);
        // Now we should be in OD2
        checkODState("OD2");

        // Add 1 month
        addMonthsAndCheckForCompletion(1, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // Amount balance should be USD 999.80
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.valueOf(999.80)), 0);
        // We should still be in OD2
        checkODState("OD2");

        // Add 1 month
        addMonthsAndCheckForCompletion(1, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR,
                                       NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.TAG,  NextEvent.BLOCK);
        // Amount balance should be USD 1249.75
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.valueOf(1249.75)), 0);
        // Now we should be in OD3
        checkODState("OD3");

        // Get all unpaid invoices and pay them to clear the overdue state
        paymentPlugin.makeAllInvoicesFailWithError(false);
        List<Invoice> unpaidInvoices = getUnpaidInvoicesOrderFromRecent();
        createPaymentAndCheckForCompletion(account, unpaidInvoices.get(0), NextEvent.BLOCK, NextEvent.TAG, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        createPaymentAndCheckForCompletion(account, unpaidInvoices.get(1), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        createPaymentAndCheckForCompletion(account, unpaidInvoices.get(2), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        createPaymentAndCheckForCompletion(account, unpaidInvoices.get(3), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        createPaymentAndCheckForCompletion(account, unpaidInvoices.get(4), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // We should be clear now
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);
    }



    @Test(groups = "slow", description = "Test clearing balance with credit also clears overdue state")
    public void testOverdueClearWithCredit() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // 2012-06-08 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-16 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-24 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);

        // 2012-06-30 => P1
        addDaysAndCheckForCompletion(6, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseEntitlement, true, true);
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.BLOCK);
        invoiceUserApi.insertCredit(account.getId(), accountBalance, new LocalDate(2012, 06, 30), account.getCurrency(), true, "credit invoice", null, null, callContext);
        assertListenerStatus();

    }

    private void allowPaymentsAndResetOverdueToClearByPayingAllUnpaidInvoices(final boolean extraPayment) {

        // Reset plugin so payments should now succeed
        paymentPlugin.makeAllInvoicesFailWithError(false);

        //
        // We now pay all unpaid invoices.
        //
        // Upon paying the last invoice, the overdue system will clear the state and notify invoice that it should re-generate a new invoice
        // for the part that was unblocked, which explains why on the last payment we expect an additional invoice (and payment if needed).
        //
        final List<Invoice> sortedInvoices = getUnpaidInvoicesOrderFromRecent();

        int remainingUnpaidInvoices = sortedInvoices.size();
        for (final Invoice invoice : sortedInvoices) {
            if (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                remainingUnpaidInvoices--;
                if (remainingUnpaidInvoices > 0) {
                    createPaymentAndCheckForCompletion(account, invoice, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
                } else {
                    if (extraPayment) {
                        createPaymentAndCheckForCompletion(account, invoice, NextEvent.BLOCK, NextEvent.TAG, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
                    } else {
                        createPaymentAndCheckForCompletion(account, invoice, NextEvent.BLOCK, NextEvent.TAG, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
                    }
                }
            }
        }
        checkODState(OverdueWrapper.CLEAR_STATE_NAME);
    }

    private List<Invoice> getUnpaidInvoicesOrderFromRecent() {
        final Collection<Invoice> invoices = invoiceUserApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext);
        // Sort in reverse order to first pay most recent invoice-- that way overdue state may only flip when we reach the last one.
        final List<Invoice> sortedInvoices = new LinkedList<Invoice>(invoices);
        Collections.sort(sortedInvoices, new Comparator<Invoice>() {
            @Override
            public int compare(final Invoice i1, final Invoice i2) {
                return i2.getInvoiceDate().compareTo(i1.getInvoiceDate());
            }
        });
        return sortedInvoices;
    }

    private void checkChangePlanWithOverdueState(final Entitlement entitlement, final boolean shouldFail, final boolean expectedPayment) {
        if (shouldFail) {
            try {
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Pistol", term, PriceListSet.DEFAULT_PRICELIST_NAME);
                entitlement.changePlan(new DefaultEntitlementSpecifier(spec), ImmutableList.<PluginProperty>of(), callContext);
            } catch (EntitlementApiException e) {
                assertTrue(e.getCause() instanceof BlockingApiException || e.getCode() == ErrorCode.SUB_CHANGE_NON_ACTIVE.getCode(),
                           String.format("Cause is %s, message is %s", e.getCause(), e.getMessage()));
            }
        } else {
            // Upgrade - we don't expect a payment here due to the scenario (the account will have enough CBA)
            if (expectedPayment) {
                changeEntitlementAndCheckForCompletion(entitlement, "Assault-Rifle", BillingPeriod.MONTHLY, null, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
            } else {
                changeEntitlementAndCheckForCompletion(entitlement, "Assault-Rifle", BillingPeriod.MONTHLY, null, NextEvent.CHANGE, NextEvent.INVOICE);
            }
        }
    }
}
