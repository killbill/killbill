/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.beatrix.integration.overdue;

import java.math.BigDecimal;
import java.util.Collection;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.integration.BeatrixModule;
import com.ning.billing.beatrix.util.InvoiceChecker.ExpectedItemCheck;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;


@Test(groups = "slow")
@Guice(modules = {BeatrixModule.class})
public class TestOverdueIntegration extends TestOverdueBase {


    @Override
    public String getOverdueConfig() {
        final String configXml = "<overdueConfig>" +
                                 "   <bundleOverdueStates>" +
                                 "       <state name=\"OD3\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>50</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD3</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
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
                                 "               <unit>DAYS</unit><number>5</number>" +
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
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "   </bundleOverdueStates>" +
                                 "</overdueConfig>";

        return configXml;
    }


    // We set the the property killbill.payment.retry.days=8,8,8,8,8,8,8,8 so that Payment retry logic does not end with an ABORTED state
    // preventing final instant payment to succeed.
    @Test(groups = "slow")
    public void testBasicOverdueState() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final Subscription baseSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012, 5, 31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 6, 30), callContext);

        // 2012, 6, 8 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR);
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // 2012, 6, 16 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR);
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // 2012, 6, 24 => Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR);
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // 2012, 6, 31 => P1 (We se 6/31 instead of 6/30 because invoice might happen later in that day)
        addDaysAndCheckForCompletion(7, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);
        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31), callContext);

        // 2012, 7, 2 => Retry P0
        addDaysAndCheckForCompletion(1, NextEvent.PAYMENT_ERROR);
        checkODState("OD1");

        // 2012, 7, 9 => Retry P1
        addDaysAndCheckForCompletion(7, NextEvent.PAYMENT_ERROR);
        checkODState("OD1");

        // 2012, 7, 10 => Retry P0
        addDaysAndCheckForCompletion(1, NextEvent.PAYMENT_ERROR);
        checkODState("OD2");

        // 2012, 7, 17 => Retry P1
        addDaysAndCheckForCompletion(7, NextEvent.PAYMENT_ERROR);
        checkODState("OD2");

        // 2012, 7, 18 => Retry P0
        addDaysAndCheckForCompletion(1, NextEvent.PAYMENT_ERROR);
        checkODState("OD2");

        // 2012, 7, 23 => Should be 20 but notficationQ event occurs on 23...
        addDaysAndCheckForCompletion(5);
        checkODState("OD3");


        paymentPlugin.makeAllInvoicesFailWithError(false);
        final Collection<Invoice> invoices = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext);
        for (final Invoice invoice : invoices) {
            if (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                createPaymentAndCheckForCompletion(account, invoice, NextEvent.PAYMENT);
            }
        }

        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);
        checkChangePlanWithOverdueState(baseSubscription, false);

        invoiceChecker.checkRepairedInvoice(account.getId(), 3,
                                            callContext, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                            // We paid up to 07-31, hence the adjustment
                                            new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-249.95")),
                                            new ExpectedItemCheck(new LocalDate(2012, 7, 23), new LocalDate(2012, 7, 23), InvoiceItemType.CBA_ADJ, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    // Note the end date here is not 07-25, but 07-9. The overdue configuration disabled invoicing between 07-09 and 07-23 (e.g. the bundle
                                    // was inaccessible, hence we didn't want to charge the customer for that period, even though the account was overdue).
                                    new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 9), InvoiceItemType.RECURRING, new BigDecimal("74.99")),
                                    // Item for the upgraded recurring plan
                                    new ExpectedItemCheck(new LocalDate(2012, 7, 23), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("154.85")),
                                    // Credits consumed
                                    new ExpectedItemCheck(new LocalDate(2012, 7, 23), new LocalDate(2012, 7, 23), InvoiceItemType.CBA_ADJ, new BigDecimal("-229.84")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31), callContext);

        // Verify the account balance: 249.95 - 74.99 - 154.85
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(new BigDecimal("-20.11")), 0);
    }

    @Test(groups = "slow")
    public void testOverdueStateIfNoPaymentMethod() throws Exception {
        // This test is similar to the previous one - but there is no default payment method on the account, so there
        // won't be any payment retry

        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Make sure the account doesn't have any payment method
        accountInternalApi.removePaymentMethod(account.getId(), internalCallContext);

        // Create subscription
        final Subscription baseSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 5, 1), callContext);

        // DAY 30 have to get out of trial before first payment. A payment error, one for each invoice, should be on the bus (because there is no payment method)
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 65 - 35 days after invoice
        // Single PAYMENT_ERROR here here triggered by the invoice
        addDaysAndCheckForCompletion(20, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31), callContext);

        // Now we should be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // DAY 67 - 37 days after invoice
        addDaysAndCheckForCompletion(2);

        // Should still be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // DAY 75 - 45 days after invoice
        addDaysAndCheckForCompletion(8);

        // Should now be in OD2
        checkODState("OD2");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // DAY 85 - 55 days after invoice
        addDaysAndCheckForCompletion(10);

        // Should now be in OD3
        checkODState("OD3");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // Add a payment method and set it as default
        paymentApi.addPaymentMethod(BeatrixModule.PLUGIN_NAME, account, true, paymentMethodPlugin, callContext);

        // Pay all invoices
        final Collection<Invoice> invoices = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext);
        for (final Invoice invoice : invoices) {
            if (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                createPaymentAndCheckForCompletion(account, invoice, NextEvent.PAYMENT);
            }
        }

        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);
        checkChangePlanWithOverdueState(baseSubscription, false);

        invoiceChecker.checkRepairedInvoice(account.getId(), 3,
                                            callContext, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                            // We paid up to 07-31, hence the adjustment
                                            new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-249.95")),
                                            new ExpectedItemCheck(new LocalDate(2012, 7, 25), new LocalDate(2012, 7, 25), InvoiceItemType.CBA_ADJ, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    // Note the end date here is not 07-25, but 07-15. The overdue configuration disabled invoicing between 07-15 and 07-25 (e.g. the bundle
                                    // was inaccessible, hence we didn't want to charge the customer for that period, even though the account was overdue).
                                    new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("124.98")),
                                    // Item for the upgraded recurring plan
                                    new ExpectedItemCheck(new LocalDate(2012, 7, 25), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("116.09")),
                                    // Credits consumed
                                    new ExpectedItemCheck(new LocalDate(2012, 7, 25), new LocalDate(2012, 7, 25), InvoiceItemType.CBA_ADJ, new BigDecimal("-241.07")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31), callContext);

        // Verify the account balance: 249.95 - 124.98 - 116.09
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(new BigDecimal("-8.88")), 0);
    }

    @Test(groups = "slow")
    public void testShouldBeInOverdueAfterExternalCharge() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Create a subscription without failing payments
        final Subscription baseSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 5, 1), callContext);

        // Create an external charge on a new invoice
        addDaysAndCheckForCompletion(5);
        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceApi.insertExternalChargeForBundle(account.getId(), bundle.getId(), BigDecimal.TEN, "For overdue", new LocalDate(2012, 5, 6), Currency.USD, callContext);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 6), null, InvoiceItemType.EXTERNAL_CHARGE, BigDecimal.TEN));

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(25, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state - the invoice for the bundle has been paid, but not the invoice with the external charge
        // We refresh overdue just to be safe, see below
        overdueApi.refreshOverdueStateFor(bundle, callContext);
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // Past 30 days since the external charge
        addDaysAndCheckForCompletion(6);
        // Note! We need to explicitly refresh here because overdue won't get notified to refresh up until the next
        // payment (when the next invoice is generated)
        // TODO - we should fix this
        overdueApi.refreshOverdueStateFor(bundle, callContext);
        // We should now be in OD1
        checkODState("OD1");

        // Pay the invoice
        final Invoice externalChargeInvoice = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).iterator().next();
        createExternalPaymentAndCheckForCompletion(account, externalChargeInvoice, NextEvent.PAYMENT);
        // We should be clear now
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);
    }

    @Test(groups = "slow")
    public void testShouldBeInOverdueAfterRefundWithoutAdjustment() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Create subscription and don't fail payments
        final Subscription baseSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 5, 1), callContext);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 65 - 35 days after invoice
        addDaysAndCheckForCompletion(20, NextEvent.INVOICE, NextEvent.PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31), callContext);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // Now, refund the second (first non-zero dollar) invoice
        final Payment payment = paymentApi.getPayment(invoiceApi.getInvoicesByAccount(account.getId(), callContext).get(1).getPayments().get(0).getPaymentId(), callContext);
        refundPaymentAndCheckForCompletion(account, payment, NextEvent.INVOICE_ADJUSTMENT);
        // We should now be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);
    }

    @Test(groups = "slow")
    public void testShouldBeInOverdueAfterChargeback() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Create subscription and don't fail payments
        final Subscription baseSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 5, 1), callContext);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 65 - 35 days after invoice
        addDaysAndCheckForCompletion(20, NextEvent.INVOICE, NextEvent.PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31), callContext);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // Now, create a chargeback for the second (first non-zero dollar) invoice
        final InvoicePayment payment = invoicePaymentApi.getInvoicePayments(invoiceApi.getInvoicesByAccount(account.getId(), callContext).get(1).getPayments().get(0).getPaymentId(), callContext).get(0);
        createChargeBackAndCheckForCompletion(payment, NextEvent.INVOICE_ADJUSTMENT);
        // We should now be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);
    }

    @Test(groups = "slow")
    public void testOverdueStateShouldClearAfterExternalPayment() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final Subscription baseSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 5, 1), callContext);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15, NextEvent.PAYMENT_ERROR);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 65 - 35 days after invoice
        addDaysAndCheckForCompletion(20, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31), callContext);

        // Now we should be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // We have two unpaid non-zero dollar invoices at this point
        // Pay the first one via an external payment - we should then be 5 days apart from the second invoice
        // (which is the earliest unpaid one) and hence come back to a clear state (see configuration)
        final Invoice firstNonZeroInvoice = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).iterator().next();
        createExternalPaymentAndCheckForCompletion(account, firstNonZeroInvoice, NextEvent.PAYMENT);
        // We should be clear now
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);
    }

    @Test(groups = "slow", enabled = false)
    public void testOverdueStateAndWRITTEN_OFFTag() throws Exception {
        // TODO add/remove tag to invoice
    }

    @Test(groups = "slow")
    public void testOverdueStateShouldClearAfterCreditOrInvoiceItemAdjustment() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final Subscription baseSubscription = createSubscriptionAndCheckForCompletion(bundle.getId(), productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 5, 1), callContext);

        // DAY 30 have to get out of trial before first payment
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 6, 30), callContext);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 45 - 15 days after invoice
        addDaysAndCheckForCompletion(15, NextEvent.PAYMENT_ERROR);

        // Should still be in clear state
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 65 - 35 days after invoice
        addDaysAndCheckForCompletion(20, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31), callContext);

        // Now we should be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);

        // We have two unpaid non-zero dollar invoices at this point
        // Adjust the first (and only) item of the first invoice - we should then be 5 days apart from the second invoice
        // (which is the earliest unpaid one) and hence come back to a clear state (see configuration)
        final Invoice firstNonZeroInvoice = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext).iterator().next();
        fullyAdjustInvoiceItemAndCheckForCompletion(account, firstNonZeroInvoice, 1, NextEvent.INVOICE_ADJUSTMENT);
        // We should be clear now
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        invoiceChecker.checkRepairedInvoice(account.getId(), 2,
                                            callContext, new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                            new ExpectedItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 5, 31), InvoiceItemType.ITEM_ADJ, new BigDecimal("-249.95")));
        invoiceChecker.checkChargedThroughDate(baseSubscription.getId(), new LocalDate(2012, 7, 31), callContext);

        // DAY 70 - 10 days after second invoice
        addDaysAndCheckForCompletion(5);

        // We should still be clear
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 80 - 20 days after second invoice
        addDaysAndCheckForCompletion(10, NextEvent.PAYMENT_ERROR);

        // We should still be clear
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);

        // DAY 95 - 35 days after second invoice
        addDaysAndCheckForCompletion(15, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);

        // We should now be in OD1
        checkODState("OD1");
        checkChangePlanWithOverdueState(baseSubscription, true);

        invoiceChecker.checkInvoice(account.getId(), 4, callContext, new ExpectedItemCheck(new LocalDate(2012, 7, 31), new LocalDate(2012, 8, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        // Fully adjust all invoices
        final Collection<Invoice> invoices = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCToday(), callContext);
        for (final Invoice invoice : invoices) {
            if (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                fullyAdjustInvoiceAndCheckForCompletion(account, invoice, NextEvent.INVOICE_ADJUSTMENT);
            }
        }

        // We should be cleared again
        checkODState(DefaultBlockingState.CLEAR_STATE_NAME);
    }

    private void checkChangePlanWithOverdueState(final Subscription subscription, final boolean shouldFail) {
        if (shouldFail) {
            try {
                subscription.changePlan("Pistol", term, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), callContext);
            } catch (EntitlementUserApiException e) {
                assertTrue(e.getCause() instanceof BlockingApiException);
            }
        } else {
            // Upgrade - we don't expect a payment here due to the scenario (the account will have some CBA)
            changeSubscriptionAndCheckForCompletion(subscription, "Assault-Rifle", BillingPeriod.MONTHLY, NextEvent.CHANGE, NextEvent.INVOICE);
        }
    }
}
