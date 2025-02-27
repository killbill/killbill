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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.beatrix.util.PaymentChecker.ExpectedPaymentCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePaymentStatus;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;
import org.killbill.billing.payment.plugin.api.PaymentPluginStatus;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.xmlloader.XMLLoader;
import org.mockito.Mockito;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class TestInvoicePayment extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testCancellationEOTWithInvoiceItemAdjustmentsOnInvoiceWithMultipleItems() throws Exception {
        final int billingDay = 1;

        final DateTime initialCreationDate = new DateTime(2016, 9, 1, 0, 3, 42, 0, testTimeZone);

        // set clock to the initial start date
        clock.setTime(initialCreationDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));

        final DefaultEntitlement bpEntitlement1 = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey1", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement1);

        final DefaultEntitlement bpEntitlement2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey2", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement2);

        paymentPlugin.makeNextPaymentFailWithError();

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR, NextEvent.PAYMENT_ERROR);
        clock.addDays(30);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 10, 1), new LocalDate(2016, 11, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 10, 1), new LocalDate(2016, 11, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        clock.addDays(1);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        bpEntitlement1.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE, BillingActionPolicy.END_OF_TERM, Collections.emptyList(), callContext);
        assertListenerStatus();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        final Invoice thirdInvoice = invoices.get(2);
        final InvoiceItem itemForBPEntitlement1 = thirdInvoice.getInvoiceItems().stream()
                .filter(input -> input.getInvoiceItemType() == InvoiceItemType.RECURRING &&
                                 input.getSubscriptionId().equals(bpEntitlement1.getId()))
                .findFirst().orElse(null);
        Assert.assertNotNull(itemForBPEntitlement1);

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.insertInvoiceItemAdjustment(account.getId(), thirdInvoice.getId(), itemForBPEntitlement1.getId(), new LocalDate(2016, 10, 2), UUID.randomUUID().toString(), UUID.randomUUID().toString(), null, callContext);
        assertListenerStatus();

        // Expect also payment for previous invoice
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        final Invoice fourthInvoice = invoices.get(3);

        Assert.assertEquals(fourthInvoice.getInvoiceItems().size(), 1);
        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 11, 1), new LocalDate(2016, 12, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
    }

    @Test(groups = "slow")
    public void testPartialPaymentByPaymentPlugin() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final AccountData accountData = getAccountData(0);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 31), InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 31), callContext);

        // Trigger a partial payment on the next invoice
        paymentPlugin.overrideNextProcessedAmount(BigDecimal.TEN);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        Invoice invoice2 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Invoice is partially paid
        final Payment payment1 = paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 5, 31), new BigDecimal("249.95"), TransactionStatus.SUCCESS, invoice2.getId(), Currency.USD));
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("239.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // 2012-06-30
        addDaysAndCheckForCompletion(30, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        Invoice invoice3 = invoiceChecker.checkInvoice(account.getId(), 3, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 30), new LocalDate(2012, 7, 31), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 7, 31), callContext);

        // Invoice is fully paid
        final Payment payment2 = paymentChecker.checkPayment(account.getId(), 2, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 6, 30), new BigDecimal("249.95"), TransactionStatus.SUCCESS, invoice3.getId(), Currency.USD));
        Assert.assertEquals(payment2.getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment2.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("239.95")), 0);
        Assert.assertEquals(invoice3.getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // Fully pay the second invoice
        final Payment payment3 = createPaymentAndCheckForCompletion(account, invoice2, invoice2.getBalance(), account.getCurrency(), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        paymentChecker.checkPayment(account.getId(), 3, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 6, 30), new BigDecimal("239.95"), TransactionStatus.SUCCESS, invoice2.getId(), Currency.USD));
        Assert.assertEquals(payment3.getPurchasedAmount().compareTo(new BigDecimal("239.95")), 0);
        Assert.assertEquals(payment3.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("239.95")), 0);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        invoice3 = invoiceUserApi.getInvoice(invoice3.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(invoice3.getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1230")
    public void testMultiplePartialPaymentsWithSameExternalKeys() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final AccountData accountData = getAccountData(0);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 31), InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 31), callContext);

        // Put the account in AUTO_PAY_OFF to make sure payment system does not try to pay the initial invoice
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE);

        Invoice invoice2 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Invoice is not paid
        Assert.assertEquals(paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext).size(), 0);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // Trigger partial payment
        final Payment payment1 = createPaymentAndCheckForCompletion(account, invoice2, BigDecimal.TEN, account.getCurrency(), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 5, 31), BigDecimal.TEN, TransactionStatus.SUCCESS, invoice2.getId(), Currency.USD));
        Assert.assertEquals(payment1.getTransactions().size(), 1);
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("239.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // Trigger another partial payment with same external keys
        try {
            createPaymentAndCheckForCompletion(account, invoice2, BigDecimal.TEN, account.getCurrency(), payment1.getExternalKey(), payment1.getTransactions().get(0).getExternalKey(), NextEvent.INVOICE_PAYMENT_ERROR);
            Assert.fail("Shouldn't have been able to create a payment");
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), (int) ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow")
    public void testPartialRefunds() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final AccountData accountData = getAccountData(0);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 31), InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 31), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        Invoice invoice2 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Invoice is fully paid
        Payment payment1 = paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 5, 31), new BigDecimal("249.95"), TransactionStatus.SUCCESS, invoice2.getId(), Currency.USD));
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(invoice2.getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // Trigger first partial refund ($1), no adjustment
        payment1 = refundPaymentAndCheckForCompletion(account, payment1, BigDecimal.TEN, Currency.USD, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getRefundedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().size(), 2);
        Assert.assertEquals(payment1.getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // Trigger second partial refund ($1), with item adjustment
        final Map<UUID, BigDecimal> iias = new HashMap<UUID, BigDecimal>();
        iias.put(invoice2.getInvoiceItems().get(0).getId(), BigDecimal.ONE);
        payment1 = refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment1, BigDecimal.ONE, Currency.USD, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getRefundedAmount().compareTo(new BigDecimal("11")), 0);
        Assert.assertEquals(payment1.getTransactions().size(), 3);
        Assert.assertEquals(payment1.getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(2).getAmount().compareTo(BigDecimal.ONE), 0);
        Assert.assertEquals(payment1.getTransactions().get(2).getProcessedAmount().compareTo(BigDecimal.ONE), 0);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // Trigger third partial refund ($10), with item adjustment
        iias.put(invoice2.getInvoiceItems().get(0).getId(), BigDecimal.TEN);
        payment1 = refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment1, BigDecimal.TEN, Currency.USD, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getRefundedAmount().compareTo(new BigDecimal("21")), 0);
        Assert.assertEquals(payment1.getTransactions().size(), 4);
        Assert.assertEquals(payment1.getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(2).getAmount().compareTo(BigDecimal.ONE), 0);
        Assert.assertEquals(payment1.getTransactions().get(2).getProcessedAmount().compareTo(BigDecimal.ONE), 0);
        Assert.assertEquals(payment1.getTransactions().get(3).getAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(3).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);
    }

    @Test(groups = "slow")
    public void testPartialPaymentByPaymentPluginThenChargebackThenChargebackReversal() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final AccountData accountData = getAccountData(0);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 31), InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 31), callContext);

        // Trigger a partial payment on the next invoice
        paymentPlugin.overrideNextProcessedAmount(BigDecimal.TEN);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        Invoice invoice2 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Invoice is partially paid
        Payment payment1 = paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 5, 31), new BigDecimal("249.95"), TransactionStatus.SUCCESS, invoice2.getId(), Currency.USD));
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().size(), 1);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("239.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // Trigger chargeback
        payment1 = createChargeBackAndCheckForCompletion(account, payment1, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment1.getTransactions().size(), 2);
        Assert.assertEquals(payment1.getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // Trigger chargeback reversal
        payment1 = createChargeBackReversalAndCheckForCompletion(account, payment1, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().size(), 3);
        Assert.assertEquals(payment1.getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertNull(payment1.getTransactions().get(2).getAmount());
        Assert.assertEquals(payment1.getTransactions().get(2).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("239.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);
    }

    @Test(groups = "slow")
    public void testAUTO_PAY_OFFThenPartialPayment() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final AccountData accountData = getAccountData(0);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final Invoice invoice1 = invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 31), InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 31), callContext);

        // No invoice payment
        Assert.assertEquals(invoice1.getPayments().size(), 0);

        // There is one dangling payment attempt (not easy to get to...)
        final List<AuditLog> paymentAttemptsAuditLogs1 = new ArrayList<AuditLog>();
        for (final AuditLog auditLog : auditUserApi.getAccountAuditLogs(account.getId(), AuditLevel.FULL, callContext).getAuditLogs()) {
            if (auditLog.getAuditedObjectType() == ObjectType.PAYMENT_ATTEMPT) {
                paymentAttemptsAuditLogs1.add(auditLog);
            }
        }
        Assert.assertEquals(paymentAttemptsAuditLogs1.size(), 2); // One INSERT and one UPDATE
        final PaymentAttemptModelDao paymentAttempt1 = paymentDao.getPaymentAttempt(paymentAttemptsAuditLogs1.get(0).getAuditedEntityId(), internalCallContext);
        Assert.assertEquals(paymentAttempt1.getStateName(), "ABORTED");

        // Put the account in AUTO_PAY_OFF to make sure payment system does not try to pay the initial invoice
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE);

        Invoice invoice2 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Invoice is not paid
        Assert.assertEquals(paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext).size(), 0);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // No invoice payment
        Assert.assertEquals(invoice2.getPayments().size(), 0);

        // There is another dangling payment attempt (not easy to get to...)
        final List<AuditLog> paymentAttemptsAuditLogs2 = new ArrayList<AuditLog>();
        for (final AuditLog auditLog : auditUserApi.getAccountAuditLogs(account.getId(), AuditLevel.FULL, callContext).getAuditLogs()) {
            if (auditLog.getAuditedObjectType() == ObjectType.PAYMENT_ATTEMPT && !paymentAttemptsAuditLogs1.contains(auditLog)) {
                paymentAttemptsAuditLogs2.add(auditLog);
            }
        }
        Assert.assertEquals(paymentAttemptsAuditLogs2.size(), 2); // One INSERT and one UPDATE
        final PaymentAttemptModelDao paymentAttempt2 = paymentDao.getPaymentAttempt(paymentAttemptsAuditLogs2.get(0).getAuditedEntityId(), internalCallContext);
        Assert.assertEquals(paymentAttempt2.getStateName(), "ABORTED");
        // Just verify we've found the right one
        Assert.assertNotEquals(paymentAttempt2.getId(), paymentAttempt1.getId());

        // Trigger partial payment
        final Payment payment1 = createPaymentAndCheckForCompletion(account, invoice2, BigDecimal.TEN, account.getCurrency(), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 5, 31), BigDecimal.TEN, TransactionStatus.SUCCESS, invoice2.getId(), Currency.USD));
        Assert.assertEquals(payment1.getTransactions().size(), 1);
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.TEN), 0);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("239.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // Remove AUTO_PAY_OFF and verify the invoice is fully paid
        remove_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final Payment payment2 = paymentChecker.checkPayment(account.getId(), 2, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 5, 31), new BigDecimal("239.95"), TransactionStatus.SUCCESS, invoice2.getId(), Currency.USD));
        Assert.assertEquals(payment2.getTransactions().size(), 1);
        Assert.assertEquals(payment2.getPurchasedAmount().compareTo(new BigDecimal("239.95")), 0);
        Assert.assertEquals(payment2.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("239.95")), 0);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);
    }

    @Test(groups = "slow")
    public void testWithoutDefaultPaymentMethod() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final AccountData accountData = getAccountData(0);
        final Account account = createAccount(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final Invoice invoice1 = invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 31), InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 31), callContext);

        // No invoice payment
        Assert.assertEquals(invoice1.getPayments().size(), 0);

        // There is one dangling payment attempt (not easy to get to...)
        final List<AuditLog> paymentAttemptsAuditLogs1 = new ArrayList<AuditLog>();
        for (final AuditLog auditLog : auditUserApi.getAccountAuditLogs(account.getId(), AuditLevel.FULL, callContext).getAuditLogs()) {
            if (auditLog.getAuditedObjectType() == ObjectType.PAYMENT_ATTEMPT) {
                paymentAttemptsAuditLogs1.add(auditLog);
            }
        }
        Assert.assertEquals(paymentAttemptsAuditLogs1.size(), 2); // One INSERT and one UPDATE
        final PaymentAttemptModelDao paymentAttempt1 = paymentDao.getPaymentAttempt(paymentAttemptsAuditLogs1.get(0).getAuditedEntityId(), internalCallContext);
        Assert.assertEquals(paymentAttempt1.getStateName(), "ABORTED");

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        // Note that there is a INVOICE_PAYMENT_ERROR event in that case, unlike the AUTO_PAY_OFF/MANUAL_PAY usecase
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR);

        Invoice invoice2 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Invoice is not paid
        Assert.assertEquals(paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext).size(), 0);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // There is no invoice payment
        Assert.assertEquals(invoice2.getPayments().size(), 0);

        // There is another dangling payment attempt (not easy to get to...)
        final List<AuditLog> paymentAttemptsAuditLogs2 = new ArrayList<AuditLog>();
        for (final AuditLog auditLog : auditUserApi.getAccountAuditLogs(account.getId(), AuditLevel.FULL, callContext).getAuditLogs()) {
            if (auditLog.getAuditedObjectType() == ObjectType.PAYMENT_ATTEMPT && !paymentAttemptsAuditLogs1.contains(auditLog)) {
                paymentAttemptsAuditLogs2.add(auditLog);
            }
        }
        Assert.assertEquals(paymentAttemptsAuditLogs2.size(), 2); // One INSERT and one UPDATE
        final PaymentAttemptModelDao paymentAttempt2 = paymentDao.getPaymentAttempt(paymentAttemptsAuditLogs2.get(0).getAuditedEntityId(), internalCallContext);
        Assert.assertEquals(paymentAttempt2.getStateName(), "ABORTED");
        // Just verify we've found the right one
        Assert.assertNotEquals(paymentAttempt2.getId(), paymentAttempt1.getId());
    }

    @Test(groups = "slow")
    public void testWithoutDefaultPaymentMethodAndAUTO_PAY_OFF() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final AccountData accountData = getAccountData(0);
        final Account account = createAccount(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final Invoice invoice1 = invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 31), InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 31), callContext);

        // No invoice payment
        Assert.assertEquals(invoice1.getPayments().size(), 0);

        // There is one dangling payment attempt (not easy to get to...)
        final List<AuditLog> paymentAttemptsAuditLogs1 = new ArrayList<AuditLog>();
        for (final AuditLog auditLog : auditUserApi.getAccountAuditLogs(account.getId(), AuditLevel.FULL, callContext).getAuditLogs()) {
            if (auditLog.getAuditedObjectType() == ObjectType.PAYMENT_ATTEMPT) {
                paymentAttemptsAuditLogs1.add(auditLog);
            }
        }
        Assert.assertEquals(paymentAttemptsAuditLogs1.size(), 2); // One INSERT and one UPDATE
        final PaymentAttemptModelDao paymentAttempt1 = paymentDao.getPaymentAttempt(paymentAttemptsAuditLogs1.get(0).getAuditedEntityId(), internalCallContext);
        Assert.assertEquals(paymentAttempt1.getStateName(), "ABORTED");

        // Put the account in AUTO_PAY_OFF
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE);

        Invoice invoice2 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Invoice is not paid
        Assert.assertEquals(paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext).size(), 0);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // There is no invoice payment
        Assert.assertEquals(invoice2.getPayments().size(), 0);

        // There is another dangling payment attempt (not easy to get to...)
        final List<AuditLog> paymentAttemptsAuditLogs2 = new ArrayList<AuditLog>();
        for (final AuditLog auditLog : auditUserApi.getAccountAuditLogs(account.getId(), AuditLevel.FULL, callContext).getAuditLogs()) {
            if (auditLog.getAuditedObjectType() == ObjectType.PAYMENT_ATTEMPT && !paymentAttemptsAuditLogs1.contains(auditLog)) {
                paymentAttemptsAuditLogs2.add(auditLog);
            }
        }
        Assert.assertEquals(paymentAttemptsAuditLogs2.size(), 2); // One INSERT and one UPDATE
        final PaymentAttemptModelDao paymentAttempt2 = paymentDao.getPaymentAttempt(paymentAttemptsAuditLogs2.get(0).getAuditedEntityId(), internalCallContext);
        Assert.assertEquals(paymentAttempt2.getStateName(), "ABORTED");
        // Just verify we've found the right one
        Assert.assertNotEquals(paymentAttempt2.getId(), paymentAttempt1.getId());
    }

    @Test(groups = "slow")
    public void testPaymentDifferentCurrencyByPaymentPlugin() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final AccountData accountData = getAccountData(0);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 31), InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 31), callContext);

        // Trigger a payment on the next invoice with a different currency ($249.95 <-> 225.44€)
        paymentPlugin.overrideNextProcessedAmount(new BigDecimal("225.44"));
        paymentPlugin.overrideNextProcessedCurrency(Currency.EUR);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        Invoice invoice2 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Invoice is fully paid
        Payment payment1 = paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 5, 31), new BigDecimal("249.95"), TransactionStatus.SUCCESS, invoice2.getId(), Currency.USD));
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().size(), 1);
        Assert.assertEquals(payment1.getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getCurrency(), Currency.USD);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("225.44")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedCurrency(), Currency.EUR);
        Assert.assertEquals(invoice2.getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.ZERO), 0);

        // Trigger chargeback in the original currency
        payment1 = createChargeBackAndCheckForCompletion(account, payment1, new BigDecimal("225.44"), Currency.EUR, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(payment1.getTransactions().size(), 2);
        Assert.assertEquals(payment1.getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getCurrency(), Currency.USD);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("225.44")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedCurrency(), Currency.EUR);
        Assert.assertEquals(payment1.getTransactions().get(1).getAmount().compareTo(new BigDecimal("225.44")), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getCurrency(), Currency.EUR);
        Assert.assertEquals(payment1.getTransactions().get(1).getProcessedAmount().compareTo(new BigDecimal("225.44")), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getProcessedCurrency(), Currency.EUR);
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        // Reverse the chargeback
        payment1 = createChargeBackReversalAndCheckForCompletion(account, payment1, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        Assert.assertEquals(payment1.getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().size(), 3);
        Assert.assertEquals(payment1.getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getCurrency(), Currency.USD);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("225.44")), 0);
        Assert.assertEquals(payment1.getTransactions().get(0).getProcessedCurrency(), Currency.EUR);
        Assert.assertEquals(payment1.getTransactions().get(1).getAmount().compareTo(new BigDecimal("225.44")), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getCurrency(), Currency.EUR);
        Assert.assertEquals(payment1.getTransactions().get(1).getProcessedAmount().compareTo(new BigDecimal("225.44")), 0);
        Assert.assertEquals(payment1.getTransactions().get(1).getProcessedCurrency(), Currency.EUR);
        Assert.assertNull(payment1.getTransactions().get(2).getAmount());
        Assert.assertNull(payment1.getTransactions().get(2).getCurrency());
        Assert.assertEquals(payment1.getTransactions().get(2).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertNull(payment1.getTransactions().get(2).getProcessedCurrency());
        invoice2 = invoiceUserApi.getInvoice(invoice2.getId(), callContext);
        Assert.assertEquals(invoice2.getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testPartialRefundsOnPartialPayments() throws Exception {
        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        clock.setDay(new LocalDate(2012, 4, 1));

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusDays(5);
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, account.getId(), null, "Initial external charge", startDate, endDate, BigDecimal.TEN, Currency.USD, null);
        final InvoiceItem item1 = invoiceUserApi.insertExternalCharges(account.getId(), clock.getUTCToday(), List.of(externalCharge), true, null, callContext).get(0);
        assertListenerStatus();
        // Verify service period for external charge -- seee #151
        assertEquals(item1.getStartDate().compareTo(startDate), 0);
        assertEquals(item1.getEndDate().compareTo(endDate), 0);

        // Trigger first partial payment ($4) on first invoice
        final Invoice invoice = invoiceUserApi.getInvoice(item1.getInvoiceId(), callContext);
        Payment payment1 = createPaymentAndCheckForCompletion(account, invoice, new BigDecimal("4.00"), account.getCurrency(), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        Invoice invoice1 = invoiceUserApi.getInvoice(item1.getInvoiceId(), callContext);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("6.00")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(new BigDecimal("4.00")) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(BigDecimal.TEN) == 0);

        BigDecimal accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance.compareTo(new BigDecimal("6.00")) == 0);

        // Trigger second partial payment ($6) on first invoice
        Payment payment2 = createPaymentAndCheckForCompletion(account, invoice, new BigDecimal("6.00"), account.getCurrency(), NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoice1 = invoiceUserApi.getInvoice(item1.getInvoiceId(), callContext);
        assertTrue(invoice1.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.TEN) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(BigDecimal.TEN) == 0);

        accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance.compareTo(BigDecimal.ZERO) == 0);

        // Refund first payment with item adjustment
        final Map<UUID, BigDecimal> iias = new HashMap<UUID, BigDecimal>();
        iias.put(item1.getId(), new BigDecimal("4.00"));
        payment1 = refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment1, new BigDecimal("4.00"), Currency.USD, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        invoice1 = invoiceUserApi.getInvoice(item1.getInvoiceId(), callContext);
        assertTrue(invoice1.getBalance().compareTo(BigDecimal.ZERO) == 0);
        accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance.compareTo(BigDecimal.ZERO) == 0);

        // Refund second payment with item adjustment
        iias.put(item1.getId(), new BigDecimal("6.00"));
        payment2 = refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment2, new BigDecimal("6.00"), Currency.USD, iias, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.INVOICE_ADJUSTMENT);
        invoice1 = invoiceUserApi.getInvoice(item1.getInvoiceId(), callContext);
        assertTrue(invoice1.getBalance().compareTo(BigDecimal.ZERO) == 0);
        accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance.compareTo(BigDecimal.ZERO) == 0);

        Assert.assertEquals(invoice1.getPayments().size(), 4);

        // Verify links for payment 1
        Assert.assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(new BigDecimal("4.00")), 0);
        Assert.assertNull(invoice1.getPayments().get(0).getLinkedInvoicePaymentId());
        Assert.assertEquals(invoice1.getPayments().get(0).getPaymentCookieId(), payment1.getTransactions().get(0).getExternalKey());
        Assert.assertEquals(invoice1.getPayments().get(0).getPaymentId(), payment1.getId());
        Assert.assertEquals(invoice1.getPayments().get(0).getType(), InvoicePaymentType.ATTEMPT);
        Assert.assertTrue(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);

        // Verify links for payment 2
        Assert.assertEquals(invoice1.getPayments().get(1).getAmount().compareTo(new BigDecimal("6.00")), 0);
        Assert.assertNull(invoice1.getPayments().get(1).getLinkedInvoicePaymentId());
        Assert.assertEquals(invoice1.getPayments().get(1).getPaymentCookieId(), payment2.getTransactions().get(0).getExternalKey());
        Assert.assertEquals(invoice1.getPayments().get(1).getPaymentId(), payment2.getId());
        Assert.assertEquals(invoice1.getPayments().get(1).getType(), InvoicePaymentType.ATTEMPT);
        Assert.assertTrue(invoice1.getPayments().get(1).getStatus() == InvoicePaymentStatus.SUCCESS);

        // Verify links for refund 1
        Assert.assertEquals(invoice1.getPayments().get(2).getAmount().compareTo(new BigDecimal("-4.00")), 0);
        Assert.assertEquals(invoice1.getPayments().get(2).getLinkedInvoicePaymentId(), invoice1.getPayments().get(0).getId());
        Assert.assertEquals(invoice1.getPayments().get(2).getPaymentCookieId(), payment1.getTransactions().get(1).getExternalKey());
        Assert.assertEquals(invoice1.getPayments().get(2).getPaymentId(), payment1.getId());
        Assert.assertEquals(invoice1.getPayments().get(2).getType(), InvoicePaymentType.REFUND);
        Assert.assertTrue(invoice1.getPayments().get(2).getStatus() == InvoicePaymentStatus.SUCCESS);

        // Verify links for refund 2
        Assert.assertEquals(invoice1.getPayments().get(3).getAmount().compareTo(new BigDecimal("-6.00")), 0);
        Assert.assertEquals(invoice1.getPayments().get(3).getLinkedInvoicePaymentId(), invoice1.getPayments().get(1).getId());
        Assert.assertEquals(invoice1.getPayments().get(3).getPaymentCookieId(), payment2.getTransactions().get(1).getExternalKey());
        Assert.assertEquals(invoice1.getPayments().get(3).getPaymentId(), payment2.getId());
        Assert.assertEquals(invoice1.getPayments().get(3).getType(), InvoicePaymentType.REFUND);
        Assert.assertTrue(invoice1.getPayments().get(3).getStatus() == InvoicePaymentStatus.SUCCESS);
    }

    @Test(groups = "slow")
    public void testWithPaymentFailure() throws Exception {
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        paymentPlugin.makeNextPaymentFailWithError();

        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        clock.addDays(30);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice invoice1 = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices.get(0) : invoices.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice1.getPayments().get(0).getCurrency(), Currency.USD);
        assertFalse(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice1.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        // Verify fix for https://github.com/killbill/killbill/issues/349
        assertEquals(payments.get(0).getId().toString(), payments.get(0).getExternalKey());
        assertEquals(payments.get(0).getTransactions().get(0).getId().toString(), payments.get(0).getTransactions().get(0).getExternalKey());

        // Trigger the payment retry
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(8);
        assertListenerStatus();

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoice1.getId(), callContext);
        assertTrue(invoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertTrue(invoice2.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(BigDecimal.ZERO) == 0);

        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext);
        assertEquals(payments2.size(), 1);
        assertEquals(payments2.get(0).getTransactions().size(), 2);
        assertEquals(payments2.get(0).getTransactions().get(1).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(1).getCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(1).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(1).getProcessedCurrency(), Currency.USD);
    }

    @Test(groups = "slow", description = "Verify payment retry after Janitor fix -- https://github.com/killbill/killbill/issues/880")
    public void testWithPaymentUnknownThenFailureAndRetry() throws Exception {
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        paymentPlugin.makeNextPaymentUnknown();

        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_PLUGIN_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        clock.addDays(30);
        assertListenerStatus();

        final List<Invoice> invoices1 = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices1.size(), 2);

        final Invoice invoice1 = invoices1.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices1.get(0) : invoices1.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice1.getPayments().get(0).getCurrency(), Currency.USD);
        assertFalse(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice1.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments1 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments1.size(), 1);
        assertEquals(payments1.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments1.get(0).getTransactions().size(), 1);
        assertEquals(payments1.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments1.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments1.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments1.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        // Verify fix for https://github.com/killbill/killbill/issues/349
        assertEquals(payments1.get(0).getId().toString(), payments1.get(0).getExternalKey());
        assertEquals(payments1.get(0).getTransactions().get(0).getId().toString(), payments1.get(0).getTransactions().get(0).getExternalKey());
        assertEquals(payments1.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments1.get(0).getPaymentAttempts().get(0).getStateName(), "ABORTED");

        // The Janitor will try to fix the payment
        busHandler.pushExpectedEvents(NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        paymentPlugin.overridePaymentPluginStatus(payments1.get(0).getId(), payments1.get(0).getTransactions().get(0).getId(), PaymentPluginStatus.ERROR);
        // See PaymentConfig#getUnknownTransactionsRetries
        clock.addDeltaFromReality(5 * 60 * 1000);
        assertListenerStatus();

        final List<Invoice> invoices11 = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices11.size(), 2);

        final Invoice invoice11 = invoices11.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                  invoices11.get(0) : invoices11.get(1);
        assertTrue(invoice11.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice11.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice11.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice11.getPayments().size(), 1);
        assertEquals(invoice11.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice11.getPayments().get(0).getCurrency(), Currency.USD);
        assertFalse(invoice11.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice11.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance11 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance11.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments11 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments11.size(), 1);
        assertEquals(payments11.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments11.get(0).getTransactions().size(), 1);
        assertEquals(payments11.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments11.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments11.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments11.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments11.get(0).getPaymentAttempts().size(), 2);
        assertEquals(payments11.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        assertEquals(payments11.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");

        // Wait for the payment retry to happen
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(8);
        assertListenerStatus();

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoice11.getId(), callContext);
        assertTrue(invoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertTrue(invoice2.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(BigDecimal.ZERO) == 0);

        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments2.size(), 1);
        assertEquals(payments2.get(0).getTransactions().size(), 2);
        assertEquals(payments2.get(0).getTransactions().get(1).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(1).getCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(1).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(1).getProcessedCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getPaymentAttempts().size(), 2);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        assertEquals(payments2.get(0).getPaymentAttempts().get(1).getStateName(), "SUCCESS");
    }

    @Test(groups = "slow", description = "Verify API payment no retry after Janitor fix -- https://github.com/killbill/killbill/issues/880")
    public void testWithAPIPaymentUnknownThenFailureAndNoRetry() throws Exception {
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Put the account in AUTO_PAY_OFF to make sure payment system does not try to pay the initial invoice
        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        final List<Invoice> invoices1 = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices1.size(), 2);

        final Invoice invoice1 = invoices1.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices1.get(0) : invoices1.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 0);

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments1 = paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext);
        assertEquals(payments1.size(), 0);

        paymentPlugin.makeNextPaymentUnknown();

        // Trigger payment
        final Payment payment1 = createPaymentAndCheckForCompletion(account, invoice1, new BigDecimal("249.95"), account.getCurrency(), NextEvent.PAYMENT_PLUGIN_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 5, 1), new BigDecimal("249.95"), TransactionStatus.UNKNOWN, invoice1.getId(), Currency.USD));
        assertEquals(payment1.getPaymentAttempts().size(), 1);
        assertEquals(payment1.getPaymentAttempts().get(0).getStateName(), "ABORTED");

        final List<Invoice> invoices11 = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices11.size(), 2);

        final Invoice invoice11 = invoices11.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                  invoices11.get(0) : invoices11.get(1);
        assertTrue(invoice11.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice11.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice11.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice11.getPayments().size(), 1);
        assertEquals(invoice11.getPayments().get(0).getPaymentId(), payment1.getId());

        // Transition the payment to failure
        paymentPlugin.overridePaymentPluginStatus(payment1.getId(), payment1.getTransactions().get(0).getId(), PaymentPluginStatus.ERROR);

        // The Janitor will try to fix the payment (even if it comes from the API)
        busHandler.pushExpectedEvents(NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        // See PaymentConfig#getUnknownTransactionsRetries
        clock.addDeltaFromReality(5 * 60 * 1000);
        assertListenerStatus();

        final List<Invoice> invoices2 = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices2.size(), 2);

        final Invoice invoice2 = invoices2.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                  invoices2.get(0) : invoices2.get(1);
        assertTrue(invoice2.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertEquals(invoice2.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice2.getPayments().get(0).getCurrency(), Currency.USD);
        assertFalse(invoice2.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        assertEquals(invoice2.getPayments().get(0).getPaymentId(), payment1.getId());

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments2.size(), 1);
        assertEquals(payments2.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments2.get(0).getTransactions().size(), 1);
        assertEquals(payments2.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getStateName(), "ABORTED");

        // No payment retry will be attempted (because the payment originally came from the API)
        clock.addDays(8);
        assertListenerStatus();

        final List<Payment> payments3 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments3.size(), 1);
        assertEquals(payments3.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments3.get(0).getPaymentAttempts().get(0).getStateName(), "ABORTED");
    }

    @Test(groups = "slow", description = "Verify notifyPendingTransactionOfStateChanged behavior for PENDING->SUCCESS")
    public void testWithPendingPaymentThenSuccess() throws Exception {
        // Verify integration with Overdue in that particular test
        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>1</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>1</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        paymentPlugin.makeNextPaymentPending();

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // INVOICE_PAYMENT_ERROR is sent for PENDING payments
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 1), callContext);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice invoice1 = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices.get(0) : invoices.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice1.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.PENDING);
        assertNotNull(invoice1.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.PENDING);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "SUCCESS");

        // Verify account transitions to OD1 while payment is PENDING
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD1", account.getId());

        // Transition the payment to success
        final List<String> paymentControlPluginNames = List.of(InvoicePaymentControlPluginApi.PLUGIN_NAME);
        final PaymentOptions paymentOptions = Mockito.mock(PaymentOptions.class);
        Mockito.when(paymentOptions.getPaymentControlPluginNames()).thenReturn(paymentControlPluginNames);

        // Add a credit on the account
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        final InvoiceItem inputCredit = new CreditAdjInvoiceItem(null, account.getId(), new LocalDate(clock.getUTCNow(), account.getTimeZone()), "", BigDecimal.TEN, account.getCurrency(), null);
        invoiceUserApi.insertCredits(account.getId(), new LocalDate(clock.getUTCNow(), account.getTimeZone()), List.of(inputCredit), true, null, callContext);
        assertListenerStatus();

        // Shows the credit is reflected on the account
        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(new BigDecimal("239.95")) == 0);

        // As we transition the payment from PENDING to SUCCESS, we check the credit is not applied in the invoice, i.e the original payment remains the same
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        paymentApi.notifyPendingTransactionOfStateChangedWithPaymentControl(account, payments.get(0).getTransactions().get(0).getId(), true, paymentOptions, callContext);
        assertListenerStatus();

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoice1.getId(), callContext);
        assertTrue(invoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertTrue(invoice2.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);

        // Perform a similar PENDING payment transaction with a refund + item adjustments
        paymentPlugin.makeNextPaymentPending();
        final Map<UUID, BigDecimal> adjustments = new HashMap<UUID, BigDecimal>();
        final BigDecimal refundValue = new BigDecimal("13.45");
        adjustments.put(invoice2.getInvoiceItems().get(0).getId(), refundValue);

        // Shows the credit is still available on the account
        final BigDecimal accountBalance3 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance3.compareTo(new BigDecimal("-10.00")) == 0);


        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT_ERROR);
        invoicePaymentApi.createRefundForInvoicePayment(true, adjustments, account, payments.get(0).getId(), refundValue, payments.get(0).getCurrency(), null, UUID.randomUUID().toString(),
                                                        Collections.emptyList(), paymentOptions, callContext);
        assertListenerStatus();


        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        paymentApi.notifyPendingTransactionOfStateChangedWithPaymentControl(account, payments2.get(0).getTransactions().get(1).getId(), true, paymentOptions, callContext);
        assertListenerStatus();


        final List<Payment> payments3 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments3.size(), 1);
        assertEquals(payments3.get(0).getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments3.get(0).getTransactions().size(), 2);

        assertEquals(payments3.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments3.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments3.get(0).getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments3.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments3.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        assertEquals(payments3.get(0).getTransactions().get(1).getAmount().compareTo(refundValue), 0);
        assertEquals(payments3.get(0).getTransactions().get(1).getCurrency(), Currency.USD);
        assertEquals(payments3.get(0).getTransactions().get(1).getProcessedAmount().compareTo(refundValue), 0);
        assertEquals(payments3.get(0).getTransactions().get(1).getProcessedCurrency(), Currency.USD);
        assertEquals(payments3.get(0).getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);

        assertEquals(payments3.get(0).getPaymentAttempts().size(), 2);
        assertEquals(payments3.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments3.get(0).getPaymentAttempts().get(0).getStateName(), "SUCCESS");

        assertEquals(payments3.get(0).getPaymentAttempts().get(1).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments3.get(0).getPaymentAttempts().get(1).getStateName(), "SUCCESS");
    }

    @Test(groups = "slow", description = "Verify notifyPendingTransactionOfStateChanged behavior for PENDING->FAILURE")
    public void testWithPendingPaymentThenFailure() throws Exception {
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        paymentPlugin.makeNextPaymentPending();

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // INVOICE_PAYMENT_ERROR is sent for PENDING payments
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 1), callContext);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice invoice1 = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices.get(0) : invoices.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice1.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.PENDING);
        assertNotNull(invoice1.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.PENDING);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "SUCCESS");

        // Transition the payment to failure
        final List<String> paymentControlPluginNames = List.of(InvoicePaymentControlPluginApi.PLUGIN_NAME);
        final PaymentOptions paymentOptions = Mockito.mock(PaymentOptions.class);
        Mockito.when(paymentOptions.getPaymentControlPluginNames()).thenReturn(paymentControlPluginNames);

        busHandler.pushExpectedEvents(NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        paymentApi.notifyPendingTransactionOfStateChangedWithPaymentControl(account, payments.get(0).getTransactions().get(0).getId(), false, paymentOptions, callContext);
        assertListenerStatus();

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoice1.getId(), callContext);
        assertEquals(invoice2, invoice1);

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(accountBalance1) == 0);

        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments2.size(), 1);
        assertEquals(payments2.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments2.get(0).getTransactions().size(), 1);
        assertEquals(payments2.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        assertEquals(payments2.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        // Note that because notifyPendingTransactionOfStateChangedWithPaymentControl is considered an API call, no retry will be attempted
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getStateName(), "ABORTED");
    }

    @Test(groups = "slow", description = "Verify fixPaymentTransactionState behavior for SUCCESS->FAILURE")
    public void testWithSuccessfulPaymentFixedToFailure() throws Exception {
        // Verify integration with Overdue in that particular test
        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>1</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>1</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 1), callContext);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice invoice1 = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices.get(0) : invoices.get(1);
        assertTrue(invoice1.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice1.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice1.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(BigDecimal.ZERO) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "SUCCESS");

        // Transition the payment to failure
        busHandler.pushExpectedEvents(NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        adminPaymentApi.fixPaymentTransactionState(payments.get(0), payments.get(0).getTransactions().get(0), TransactionStatus.PAYMENT_FAILURE, null, null, Collections.emptyList(), callContext);
        assertListenerStatus();

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoice1.getId(), callContext);
        assertTrue(invoice2.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertEquals(invoice2.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice2.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice2.getPayments().get(0).getStatus() == InvoicePaymentStatus.INIT);
        assertNotNull(invoice2.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments2.size(), 1);
        assertEquals(payments2.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments2.get(0).getTransactions().size(), 1);
        assertEquals(payments2.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        assertEquals(payments2.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        // Note that because fixPaymentTransactionState is considered an API call, no retry will be attempted
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getStateName(), "ABORTED");

        // Verify account transitions to OD1
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD1", account.getId());
    }

    @Test(groups = "slow", description = "Verify fixPaymentTransactionState behavior for SUCCESS->FAILURE and retries enabled")
    public void testWithSuccessfulPaymentFixedToFailureWithRetries() throws Exception {
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 1), callContext);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        Invoice invoice = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                          invoices.get(0) : invoices.get(1);
        assertEquals(invoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice.getPaidAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice.getChargedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice.getPayments().size(), 1);
        assertEquals(invoice.getPayments().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice.getPayments().get(0).getCurrency(), Currency.USD);
        assertSame(invoice.getPayments().get(0).getStatus(), InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice.getPayments().get(0).getPaymentId());

        BigDecimal accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);

        List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "SUCCESS");

        // Transition the payment to failure
        busHandler.pushExpectedEvents(NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);
        PluginProperty ipcdRetryProperty = new PluginProperty("IPCD_RETRIES", true, false);
        adminPaymentApi.fixPaymentTransactionState(payments.get(0), payments.get(0).getTransactions().get(0), TransactionStatus.PAYMENT_FAILURE, null, null, List.of(ipcdRetryProperty), callContext);
        assertListenerStatus();

        invoice = invoiceUserApi.getInvoice(invoice.getId(), callContext);
        assertEquals(invoice.getBalance().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice.getPaidAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice.getChargedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice.getPayments().size(), 1);
        assertEquals(invoice.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice.getPayments().get(0).getCurrency(), Currency.USD);
        assertSame(invoice.getPayments().get(0).getStatus(), InvoicePaymentStatus.INIT);
        assertNotNull(invoice.getPayments().get(0).getPaymentId());

        accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertEquals(accountBalance.compareTo(new BigDecimal("249.95")), 0);

        payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 2);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        // Payment will be retried since the IPCD_RETRIES=true plugin property is specified
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        assertEquals(payments.get(0).getPaymentAttempts().get(1).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");

        // Trigger the payment retry
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(8);
        assertListenerStatus();

        invoice = invoiceUserApi.getInvoice(invoice.getId(), callContext);
        assertEquals(invoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice.getPaidAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice.getChargedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice.getPayments().size(), 1);
        assertEquals(invoice.getPayments().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice.getPayments().get(0).getCurrency(), Currency.USD);
        assertSame(invoice.getPayments().get(0).getStatus(), InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice.getPayments().get(0).getPaymentId());

        accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);

        payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().size(), 2);
        assertEquals(payments.get(0).getTransactions().get(1).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(1).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(1).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(1).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(1).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 2);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        assertEquals(payments.get(0).getPaymentAttempts().get(1).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(1).getStateName(), "SUCCESS");
    }


    @Test(groups = "slow", description = "Verify fixPaymentTransactionState behavior for FAILURE->SUCCESS")
    public void testWithFailedPaymentFixedToSuccess() throws Exception {
        // Verify integration with Overdue in that particular test
        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>1</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>1</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        paymentPlugin.makeNextPaymentFailWithError();

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 1), callContext);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice invoice1 = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices.get(0) : invoices.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice1.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.INIT);
        assertNotNull(invoice1.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.PAYMENT_FAILURE);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 2);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "RETRIED");
        assertEquals(payments.get(0).getPaymentAttempts().get(1).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(1).getStateName(), "SCHEDULED");

        // Verify account transitions to OD1
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD1", account.getId());

        // Transition the payment to success
        final PaymentTransaction existingPaymentTransaction = payments.get(0).getTransactions().get(0);
        final PaymentTransaction updatedPaymentTransaction = Mockito.mock(PaymentTransaction.class);
        Mockito.when(updatedPaymentTransaction.getId()).thenReturn(existingPaymentTransaction.getId());
        Mockito.when(updatedPaymentTransaction.getExternalKey()).thenReturn(existingPaymentTransaction.getExternalKey());
        Mockito.when(updatedPaymentTransaction.getTransactionType()).thenReturn(existingPaymentTransaction.getTransactionType());
        Mockito.when(updatedPaymentTransaction.getProcessedAmount()).thenReturn(new BigDecimal("249.95"));
        Mockito.when(updatedPaymentTransaction.getProcessedCurrency()).thenReturn(existingPaymentTransaction.getCurrency());
        Mockito.when(updatedPaymentTransaction.getAmount()).thenReturn(existingPaymentTransaction.getAmount());
        Mockito.when(updatedPaymentTransaction.getCurrency()).thenReturn(existingPaymentTransaction.getCurrency());
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        adminPaymentApi.fixPaymentTransactionState(payments.get(0), updatedPaymentTransaction, TransactionStatus.SUCCESS, null, null, Collections.emptyList(), callContext);
        assertListenerStatus();

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoice1.getId(), callContext);
        assertTrue(invoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertEquals(invoice2.getPayments().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice2.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice2.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice2.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(BigDecimal.ZERO) == 0);

        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments2.size(), 1);
        assertEquals(payments2.get(0).getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().size(), 1);
        assertEquals(payments2.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payments2.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getStateName(), "SUCCESS");
    }

    @Test(groups = "slow")
    public void testWithIncompletePaymentAttempt() throws Exception {
        // 2012-05-01T00:03:42.000Z
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        final AccountData accountData = getAccountData(0);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 31), InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 5, 31), callContext);

        // 2012-05-31 => DAY 30 have to get out of trial {I0, P0}
        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        Invoice invoice2 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 30), callContext);

        // Invoice is fully paid
        final Payment originalPayment = paymentChecker.checkPayment(account.getId(), 1, callContext, new ExpectedPaymentCheck(new LocalDate(2012, 5, 31), new BigDecimal("249.95"), TransactionStatus.SUCCESS, invoice2.getId(), Currency.USD));
        Assert.assertEquals(originalPayment.getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(originalPayment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(originalPayment.getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        Assert.assertEquals(invoice2.getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        final PaymentTransaction originalTransaction = originalPayment.getTransactions().get(0);

        // Let 's hack invoice_payment table by hand to simulate a non completion of the payment (onSuccessCall was never called)
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("update invoice_payments set status='INIT' where payment_cookie_id = ?", originalTransaction.getExternalKey());
                return null;
            }
        });

        final Invoice updateInvoice2 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        // Invoice now shows as unpaid
        Assert.assertEquals(updateInvoice2.getBalance().compareTo(originalPayment.getPurchasedAmount()), 0);
        Assert.assertEquals(updateInvoice2.getPayments().size(), 1);
        Assert.assertEquals(updateInvoice2.getPayments().get(0).getPaymentCookieId(), originalTransaction.getExternalKey());

        //
        // Now trigger invoice payment again (no new payment should be made as code should detect broken state and fix it by itself)
        // We expect an INVOICE_PAYMENT that indicates the invoice was repaired, and also an exception because plugin aborts payment call since there is nothing to do.
        //
        busHandler.pushExpectedEvents(NextEvent.INVOICE_PAYMENT);
        try {
            invoicePaymentApi.createPurchaseForInvoicePayment(account,
                                                              updateInvoice2.getId(),
                                                              account.getPaymentMethodId(),
                                                              null,
                                                              updateInvoice2.getBalance(),
                                                              updateInvoice2.getCurrency(),
                                                              null,
                                                              UUID.randomUUID().toString(),
                                                              UUID.randomUUID().toString(),
                                                              Collections.emptyList(),
                                                              PAYMENT_OPTIONS,
                                                              callContext);
            Assert.fail("The payment should not succeed (and yet it will repair the broken state....)");
        } catch (final PaymentApiException expected) {
            Assert.assertEquals(expected.getCode(), ErrorCode.PAYMENT_PLUGIN_API_ABORTED.getCode());
        }
        assertListenerStatus();

        final Invoice updateInvoice3 = invoiceChecker.checkInvoice(account.getId(), 2, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 31), new LocalDate(2012, 6, 30), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        Assert.assertEquals(updateInvoice3.getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(updateInvoice3.getPayments().size(), 1);
        Assert.assertEquals(updateInvoice3.getPayments().get(0).getPaymentCookieId(), originalTransaction.getExternalKey());
        Assert.assertTrue(updateInvoice3.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        Assert.assertEquals(invoiceUserApi.getAccountBalance(account.getId(), callContext).compareTo(invoice2.getBalance()), 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, false, Collections.emptyList(), callContext);
        Assert.assertEquals(payments.size(), 1);
        Assert.assertEquals(payments.get(0).getTransactions().size(), 1);

    }

    @Test(groups = "slow", description = "Verify fixPaymentTransactionState behavior for UNKNOWN->SUCCESS")
    public void testWithUNKNOWNPaymentFixedToSuccess() throws Exception {
        // Verify integration with Overdue in that particular test
        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>1</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>1</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        paymentPlugin.makeNextPaymentUnknown();

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_PLUGIN_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 1), callContext);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice invoice1 = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices.get(0) : invoices.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice1.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.INIT);
        assertNotNull(invoice1.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.UNKNOWN);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "ABORTED");

        // Verify account transitions to OD1
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD1", account.getId());

        // Verify we cannot trigger double payments
        try {
            invoicePaymentApi.createPurchaseForInvoicePayment(account,
                                                              invoice1.getId(),
                                                              payments.get(0).getPaymentMethodId(),
                                                              null,
                                                              invoice1.getBalance(),
                                                              invoice1.getCurrency(),
                                                              clock.getUTCNow(),
                                                              null,
                                                              null,
                                                              Collections.emptyList(),
                                                              PAYMENT_OPTIONS,
                                                              callContext);
            Assert.fail();
        } catch (final PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_PLUGIN_API_ABORTED.getCode());
            assertListenerStatus();
        }

        // Transition the payment to success
        final PaymentTransaction existingPaymentTransaction = payments.get(0).getTransactions().get(0);
        final PaymentTransaction updatedPaymentTransaction = Mockito.mock(PaymentTransaction.class);
        Mockito.when(updatedPaymentTransaction.getId()).thenReturn(existingPaymentTransaction.getId());
        Mockito.when(updatedPaymentTransaction.getExternalKey()).thenReturn(existingPaymentTransaction.getExternalKey());
        Mockito.when(updatedPaymentTransaction.getTransactionType()).thenReturn(existingPaymentTransaction.getTransactionType());
        Mockito.when(updatedPaymentTransaction.getProcessedAmount()).thenReturn(new BigDecimal("249.95"));
        Mockito.when(updatedPaymentTransaction.getProcessedCurrency()).thenReturn(existingPaymentTransaction.getCurrency());
        Mockito.when(updatedPaymentTransaction.getAmount()).thenReturn(existingPaymentTransaction.getAmount());
        Mockito.when(updatedPaymentTransaction.getCurrency()).thenReturn(existingPaymentTransaction.getCurrency());
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        adminPaymentApi.fixPaymentTransactionState(payments.get(0), updatedPaymentTransaction, TransactionStatus.SUCCESS, null, null, Collections.emptyList(), callContext);
        assertListenerStatus();

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoice1.getId(), callContext);
        assertTrue(invoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertEquals(invoice2.getPayments().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice2.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice2.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice2.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(BigDecimal.ZERO) == 0);

        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments2.size(), 1);
        assertEquals(payments2.get(0).getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().size(), 1);
        assertEquals(payments2.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payments2.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getStateName(), "SUCCESS");
    }

    @Test(groups = "slow", description = "Verify fixPaymentTransactionState behavior for UNKNOWN->SUCCESS when overriding processed amount -- https://github.com/killbill/killbill/issues/1061#issuecomment-521911301")
    public void testWithUNKNOWNPaymentFixedToSuccessAndOverrideProcessedAmount() throws Exception {
        // Verify integration with Overdue in that particular test
        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>1</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>1</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        paymentPlugin.makeNextPaymentUnknown();

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_PLUGIN_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 1), callContext);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice invoice1 = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices.get(0) : invoices.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice1.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.INIT);
        assertNotNull(invoice1.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.UNKNOWN);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "ABORTED");

        // Verify account transitions to OD1
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD1", account.getId());

        // Transition the payment to success
        final PaymentTransaction existingPaymentTransaction = payments.get(0).getTransactions().get(0);
        final PaymentTransaction updatedPaymentTransaction = Mockito.mock(PaymentTransaction.class);
        Mockito.when(updatedPaymentTransaction.getId()).thenReturn(existingPaymentTransaction.getId());
        Mockito.when(updatedPaymentTransaction.getExternalKey()).thenReturn(existingPaymentTransaction.getExternalKey());
        Mockito.when(updatedPaymentTransaction.getTransactionType()).thenReturn(existingPaymentTransaction.getTransactionType());
        // This could happen when invoked via JAX-RS: we completely bypass the plugin, so Kill Bill doesn't know about the processed amount yet
        // See https://github.com/killbill/killbill/issues/1061#issuecomment-521911301
        Mockito.when(updatedPaymentTransaction.getProcessedAmount()).thenReturn(BigDecimal.ZERO);
        Mockito.when(updatedPaymentTransaction.getProcessedCurrency()).thenReturn(existingPaymentTransaction.getCurrency());
        Mockito.when(updatedPaymentTransaction.getAmount()).thenReturn(existingPaymentTransaction.getAmount());
        Mockito.when(updatedPaymentTransaction.getCurrency()).thenReturn(existingPaymentTransaction.getCurrency());
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        adminPaymentApi.fixPaymentTransactionState(payments.get(0), updatedPaymentTransaction, TransactionStatus.SUCCESS, null, null, Collections.emptyList(), callContext);
        assertListenerStatus();

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoice1.getId(), callContext);
        assertTrue(invoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertEquals(invoice2.getPayments().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice2.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice2.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice2.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(BigDecimal.ZERO) == 0);

        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments2.size(), 1);
        assertEquals(payments2.get(0).getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().size(), 1);
        assertEquals(payments2.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payments2.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getStateName(), "SUCCESS");
    }

    @Test(groups = "slow", description = "Verify getPayment behavior for UNKNOWN->SUCCESS -- https://github.com/killbill/killbill/issues/1017")
    public void testWithUNKNOWNPaymentFixedToSuccessViaGETJanitorFlow() throws Exception {
        // Verify integration with Overdue in that particular test
        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>1</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>1</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        paymentPlugin.makeNextPaymentUnknown();

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_PLUGIN_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 1), callContext);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice invoice1 = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices.get(0) : invoices.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice1.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.INIT);
        assertNotNull(invoice1.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.UNKNOWN);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "ABORTED");

        // Verify account transitions to OD1
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD1", account.getId());

        // Transition the payment to success (Janitor flow)
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        paymentPlugin.overridePaymentPluginStatus(payments.get(0).getId(), payments.get(0).getTransactions().get(0).getId(), PaymentPluginStatus.PROCESSED);
        paymentApi.getPayment(payments.get(0).getId(), true, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoice1.getId(), callContext);
        assertTrue(invoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertEquals(invoice2.getPayments().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice2.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice2.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice2.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(BigDecimal.ZERO) == 0);

        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), true, true, Collections.emptyList(), callContext);
        assertEquals(payments2.size(), 1);
        assertEquals(payments2.get(0).getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().size(), 1);
        assertEquals(payments2.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payments2.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getStateName(), "SUCCESS");
    }

    @Test(groups = "slow", description = "Verify Janitor notification queue behavior for UNKNOWN->SUCCESS -- https://github.com/killbill/killbill/issues/1061")
    public void testWithUNKNOWNPaymentFixedToSuccessViaImplicitJanitorFlow() throws Exception {
        // Verify integration with Overdue in that particular test
        final String configXml = "<overdueConfig>" +
                                 "   <accountOverdueStates>" +
                                 "       <initialReevaluationInterval>" +
                                 "           <unit>DAYS</unit><number>1</number>" +
                                 "       </initialReevaluationInterval>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>1</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "       </state>" +
                                 "   </accountOverdueStates>" +
                                 "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        paymentPlugin.makeNextPaymentUnknown();

        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        addDaysAndCheckForCompletion(30, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_PLUGIN_ERROR, NextEvent.INVOICE_PAYMENT_ERROR);

        invoiceChecker.checkChargedThroughDate(baseEntitlement.getId(), new LocalDate(2012, 6, 1), callContext);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice invoice1 = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                                 invoices.get(0) : invoices.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertEquals(invoice1.getPayments().get(0).getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice1.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice1.getPayments().get(0).getStatus() == InvoicePaymentStatus.INIT);
        assertNotNull(invoice1.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, true, Collections.emptyList(), callContext);
        assertEquals(payments.size(), 1);
        assertEquals(payments.get(0).getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().size(), 1);
        assertEquals(payments.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(payments.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.UNKNOWN);
        assertEquals(payments.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getStateName(), "ABORTED");
        // We store the amount that was set in the prior call (https://github.com/killbill/killbill/issues/1281)
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments.get(0).getPaymentAttempts().get(0).getCurrency(), Currency.USD);

        // Verify account transitions to OD1
        addDaysAndCheckForCompletion(2, NextEvent.BLOCK);
        checkODState("OD1", account.getId());

        // Transition the payment to success (Janitor flow)
        busHandler.pushExpectedEvents(NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT, NextEvent.BLOCK);
        paymentPlugin.overridePaymentPluginStatus(payments.get(0).getId(), payments.get(0).getTransactions().get(0).getId(), PaymentPluginStatus.PROCESSED);
        // See PaymentConfig#getUnknownTransactionsRetries
        clock.addDeltaFromReality(60 * 60 * 1000);
        assertListenerStatus();

        checkODState(OverdueWrapper.CLEAR_STATE_NAME, account.getId());

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoice1.getId(), callContext);
        assertTrue(invoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertEquals(invoice2.getPayments().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(invoice2.getPayments().get(0).getCurrency(), Currency.USD);
        assertTrue(invoice2.getPayments().get(0).getStatus() == InvoicePaymentStatus.SUCCESS);
        assertNotNull(invoice2.getPayments().get(0).getPaymentId());

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(BigDecimal.ZERO) == 0);

        final List<Payment> payments2 = paymentApi.getAccountPayments(account.getId(), true, true, Collections.emptyList(), callContext);
        assertEquals(payments2.size(), 1);
        assertEquals(payments2.get(0).getPurchasedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().size(), 1);
        assertEquals(payments2.get(0).getTransactions().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getTransactions().get(0).getProcessedCurrency(), Currency.USD);
        assertEquals(payments2.get(0).getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);
        assertEquals(payments2.get(0).getPaymentAttempts().size(), 1);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getPluginName(), InvoicePaymentControlPluginApi.PLUGIN_NAME);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getStateName(), "SUCCESS");
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getAmount().compareTo(new BigDecimal("249.95")), 0);
        assertEquals(payments2.get(0).getPaymentAttempts().get(0).getCurrency(), Currency.USD);
    }
}
