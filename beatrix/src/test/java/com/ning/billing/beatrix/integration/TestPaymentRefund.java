/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import com.ning.billing.beatrix.util.PaymentChecker.ExpectedPaymentCheck;
import com.ning.billing.beatrix.util.RefundChecker.ExpectedRefundCheck;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentStatus;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class TestPaymentRefund extends TestIntegrationBase {

    // Setup for all tests below
    private Account account;
    private Invoice invoice;
    private Payment payment;
    private Set<UUID> invoiceItems;
    private DateTime initialCreationDate;
    private int invoiceItemCount;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        invoiceItemCount = 1;
        setupRefundTest();
    }

    @Test(groups = "slow")
    public void testRefundWithNoAdjustments() throws Exception {
        // Although we don't adjust the invoice, the invoicing system sends an event because invoice balance changes and overdue system-- in particular-- needs to know about it.
        refundPaymentAndCheckForCompletion(account, payment, NextEvent.INVOICE_ADJUSTMENT);
        refundChecker.checkRefund(payment.getId(), callContext, new ExpectedRefundCheck(payment.getId(), false, new BigDecimal("233.83"), Currency.USD, initialCreationDate.toLocalDate()));
    }

    @Test(groups = "slow")
    public void testRefundWithInvoiceItemAdjustemts() throws Exception {
        refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment, invoiceItems, NextEvent.INVOICE_ADJUSTMENT);
        refundChecker.checkRefund(payment.getId(), callContext, new ExpectedRefundCheck(payment.getId(), true, new BigDecimal("233.83"), Currency.USD, initialCreationDate.toLocalDate()));
        invoice = invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext,
                                              new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2),
                                                                           new LocalDate(2012, 3, 31), InvoiceItemType.RECURRING, new BigDecimal("233.83")),
                                              new ExpectedInvoiceItemCheck(InvoiceItemType.ITEM_ADJ, new BigDecimal("-233.83")));
    }

    @Test(groups = "slow")
    public void testRefundWithInvoiceAdjustment() throws Exception {
        refundPaymentWithAdjustmenttAndCheckForCompletion(account, payment, NextEvent.INVOICE_ADJUSTMENT);
        refundChecker.checkRefund(payment.getId(), callContext, new ExpectedRefundCheck(payment.getId(), true, new BigDecimal("233.83"), Currency.USD, initialCreationDate.toLocalDate()));
        invoice = invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext,
                                              new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2),
                                                                           new LocalDate(2012, 3, 31), InvoiceItemType.RECURRING, new BigDecimal("233.83")),
                                              new ExpectedInvoiceItemCheck(InvoiceItemType.REFUND_ADJ, new BigDecimal("-233.83")));

    }

    private void setupRefundTest() throws Exception {

        final int billingDay = 31;
        initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);

        account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        // set clock to the initial start date
        clock.setTime(initialCreationDate);
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", callContext);

        invoiceItemCount = 0;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        SubscriptionData subscription = subscriptionDataFromSubscription(createSubscriptionAndCheckForCompletion(bundle.getId(), "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE));
        invoiceChecker.checkInvoice(account.getId(), ++invoiceItemCount, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(subscription.getId(), clock.getUTCToday(), callContext);

        setDateAndCheckForCompletion(new DateTime(2012, 3, 2, 23, 59, 59, 0, testTimeZone), NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        invoice = invoiceChecker.checkInvoice(account.getId(), ++invoiceItemCount, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2),
                                                                                                                             new LocalDate(2012, 3, 31), InvoiceItemType.RECURRING, new BigDecimal("233.83")));
        payment = paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 3, 2), new BigDecimal("233.83"), PaymentStatus.SUCCESS, invoice.getId(), Currency.USD));


        // Filter and extract UUId from all Recuring invoices
        invoiceItems = new HashSet<UUID>(Collections2.transform(Collections2.filter(invoice.getInvoiceItems(), new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(@Nullable final InvoiceItem invoiceItem) {
                return invoiceItem.getInvoiceItemType() == InvoiceItemType.RECURRING;
            }
        }), new Function<InvoiceItem, UUID>() {
            @Nullable
            @Override
            public UUID apply(@Nullable final InvoiceItem invoiceItem) {
                return invoiceItem.getId();
            }
        }));
    }
}

