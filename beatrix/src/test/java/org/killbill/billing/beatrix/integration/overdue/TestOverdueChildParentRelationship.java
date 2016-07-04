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

package org.killbill.billing.beatrix.integration.overdue;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

// For all the tests, we set the the property org.killbill.payment.retry.days=8,8,8,8,8,8,8,8 so that Payment retry logic does not end with an ABORTED state
// preventing final instant payment to succeed.
//
// The tests are difficult to follow because there are actually two tracks of retry in logic:
// - The payment retries
// - The overdue notifications
//

public class TestOverdueChildParentRelationship extends TestOverdueBase {

    @Override
    public String getOverdueConfig() {
        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>10</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD3\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>26</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD3</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                                 "       </state>" +
                                 "       <state name=\"OD2\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>18</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD2</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>8</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>10</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>8</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";

        return configXml;
    }

    @Test(groups = "slow", description = "Test overdue stages and return to clear on CTD for Parent and Child accounts")
    public void testOverdueStagesParentChildAccounts() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        setupAccount();
        final Account childAccount = createAccountWithNonOsgiPaymentMethod(getChildAccountData(0, account.getId(), true));

        // Set next invoice to fail and create subscription
        paymentPlugin.makeAllInvoicesFailWithError(true);
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(childAccount.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        bundle = subscriptionApi.getSubscriptionBundle(baseEntitlement.getBundleId(), callContext);

        invoiceChecker.checkInvoice(childAccount.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        //invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.PARENT_SUMMARY, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 1), callContext);

        // 2012-05-2 => DAY 1 : Parent Invoice commit status
        addDaysAndCheckForCompletion(1, NextEvent.INVOICE);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(29, NextEvent.PHASE, NextEvent.INVOICE);

        // 2012-06-01 => Parent Invoice payment attempt
        addDaysAndCheckForCompletion(1, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkInvoice(childAccount.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        //invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.PARENT_SUMMARY, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // 2012-06-09 => DAY 8 : Retry P0
        addDaysAndCheckForCompletion(8, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());
        checkODState(OverdueWrapper.CLEAR_STATE_NAME, childAccount.getId());

        // 2012-06-11 => Day 10 - Retry P0 - Move to OD1 state
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK, NextEvent.BLOCK);
        checkODState("OD1", account.getId());
        checkODState("OD1", childAccount.getId());

        // 2012-06-17 => DAY 16 - Retry P1
        addDaysAndCheckForCompletion(6, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD1", account.getId());
        checkODState("OD1", childAccount.getId());

        // 2012-06-19 => Day 18 - Retry P0 - Move to OD2 state
        addDaysAndCheckForCompletion(2, NextEvent.TAG, NextEvent.BLOCK, NextEvent.TAG, NextEvent.BLOCK);
        checkODState("OD2", account.getId());
        checkODState("OD2", childAccount.getId());

        // 2012-06-25 => DAY 24 - Retry P2
        addDaysAndCheckForCompletion(6, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        checkODState("OD2", account.getId());
        checkODState("OD2", childAccount.getId());

        // 2012-06-27 => Day 26 - Retry P2 - Move to OD3 state
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK, NextEvent.BLOCK);
        checkODState("OD3", account.getId());
        checkODState("OD3", childAccount.getId());

        // Make sure the 'invoice-service:next-billing-date-queue' gets processed before we continue and since we are in AUTO_INVOICING_OFF
        // no event (NULL_INVOICE) will be generated and so we can't synchronize on any event, and we need to add a small amount of sleep
        Thread.sleep(1000);

        // Verify the account balance is 0
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoiceUserApi.getAccountBalance(childAccount.getId(), callContext).compareTo(new BigDecimal("249.95")), 0);

        allowPaymentsAndResetOverdueToClearByPayingAllUnpaidInvoices(false);
        checkODState(OverdueWrapper.CLEAR_STATE_NAME, childAccount.getId());

        // check invoice generated after clear child account
        invoiceChecker.checkInvoice(childAccount.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 19), new LocalDate(2012, 6, 27), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-66.65")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 27), new LocalDate(2012, 6, 27), InvoiceItemType.CBA_ADJ, new BigDecimal("66.65")));

        // Verify the account balance is now 0
        assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoiceUserApi.getAccountBalance(childAccount.getId(), callContext).compareTo(BigDecimal.valueOf(-66.65)), 0);
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
                        createPaymentAndCheckForCompletion(account, invoice, NextEvent.BLOCK, NextEvent.TAG, NextEvent.BLOCK, NextEvent.TAG, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE, NextEvent.NULL_INVOICE); // , NextEvent.INVOICE, NextEvent.NULL_INVOICE
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


}
