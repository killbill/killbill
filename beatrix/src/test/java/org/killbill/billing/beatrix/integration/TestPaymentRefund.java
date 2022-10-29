/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.beatrix.util.PaymentChecker.ExpectedPaymentCheck;
import org.killbill.billing.beatrix.util.RefundChecker.ExpectedRefundCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

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
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        invoiceItemCount = 1;
        setupRefundTest();
    }

    @Test(groups = "slow")
    public void testRefundWithNoAdjustments() throws Exception {
        // Although we don't adjust the invoice, the invoicing system sends an event because invoice balance changes and overdue system-- in particular-- needs to know about it.
        refundPaymentAndCheckForCompletion(account, payment, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        refundChecker.checkRefund(payment.getId(), callContext, new ExpectedRefundCheck(payment.getId(), false, new BigDecimal("233.82"), Currency.USD, initialCreationDate.toLocalDate()));


        final Invoice invoiceRefreshed =  invoiceUserApi.getInvoice(invoice.getId(), callContext);
        assertTrue(invoiceRefreshed.getBalance().compareTo(new BigDecimal("233.82")) == 0);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance.compareTo(new BigDecimal("233.82")) == 0);


    }

    @Test(groups = "slow")
    public void testRefundWithInvoiceItemAdjustemts() throws Exception {

        final Map<UUID, BigDecimal> iias = new HashMap<UUID, BigDecimal>();
        iias.put(invoice.getInvoiceItems().get(0).getId(), null);
        refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        refundChecker.checkRefund(payment.getId(), callContext, new ExpectedRefundCheck(payment.getId(), true, new BigDecimal("233.82"), Currency.USD, initialCreationDate.toLocalDate()));
        invoice = invoiceChecker.checkInvoice(account.getId(), invoiceItemCount++, callContext,
                                              new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2),
                                                                           new LocalDate(2012, 3, 31), InvoiceItemType.RECURRING, new BigDecimal("233.82")),
                                              new ExpectedInvoiceItemCheck(InvoiceItemType.ITEM_ADJ, new BigDecimal("-233.82"))
                                             );
    }

    @Test(groups = "slow")
    public void testFailedRefundWithInvoiceAdjustment() throws Exception {
        try {
            invoicePaymentApi.createRefundForInvoicePayment(true, null, account, payment.getId(), payment.getPurchasedAmount(), payment.getCurrency(), null, UUID.randomUUID().toString(),
                                                            Collections.emptyList(), PAYMENT_OPTIONS, callContext);
            fail("Refund with invoice adjustment should now throw an Exception");
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCause(), null);
            // Unfortunately we lose the original error code : INVOICE_ITEMS_ADJUSTMENT_MISSING
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_PLUGIN_EXCEPTION.getCode());
        }
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/255")
    public void testRefundWithDeletedPaymentMethod() throws Exception {

        // delete payment method
        busHandler.pushExpectedEvent(NextEvent.TAG);
        paymentApi.deletePaymentMethod(account, account.getPaymentMethodId(), true, true, new ArrayList<PluginProperty>(), callContext);
        assertListenerStatus();

        // try to create a refund for a payment with its payment method deleted
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        paymentApi.createRefund(account, payment.getId(), payment.getPurchasedAmount(), payment.getCurrency(), null,
                                UUID.randomUUID().toString(), PLUGIN_PROPERTIES, callContext);
        assertListenerStatus();
    }

    private void setupRefundTest() throws Exception {

        final int billingDay = 31;
        initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);

        account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        // set clock to the initial start date
        clock.setTime(initialCreationDate);
        invoiceItemCount = 0;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        invoiceChecker.checkInvoice(account.getId(), ++invoiceItemCount, callContext, new ExpectedInvoiceItemCheck(initialCreationDate.toLocalDate(), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        // No end date for the trial item (fixed price of zero), and CTD should be today (i.e. when the trial started)
        invoiceChecker.checkChargedThroughDate(bpEntitlement.getId(), clock.getUTCToday(), callContext);

        setDateAndCheckForCompletion(new DateTime(2012, 3, 2, 23, 59, 0, 0, testTimeZone), NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoice = invoiceChecker.checkInvoice(account.getId(), ++invoiceItemCount, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 3, 2),
                                                                                                                             new LocalDate(2012, 3, 31), InvoiceItemType.RECURRING, new BigDecimal("233.82")));

        assertTrue(invoice.getChargedAmount().compareTo(new BigDecimal("233.82")) == 0);
        assertTrue(invoice.getBalance().compareTo(BigDecimal.ZERO) == 0);

        final BigDecimal accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance.compareTo(BigDecimal.ZERO) == 0);

        payment = paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 3, 2), new BigDecimal("233.82"), TransactionStatus.SUCCESS, invoice.getId(), Currency.USD));

        // Filter and extract UUId from all Recuring invoices
        invoiceItems = invoice.getInvoiceItems()
                .stream()
                .filter(invoiceItem -> invoiceItem.getInvoiceItemType() == InvoiceItemType.RECURRING)
                .map(InvoiceItem::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}

