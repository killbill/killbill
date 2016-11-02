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

package org.killbill.billing.jaxrs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Invoice;
import org.killbill.billing.client.model.InvoiceItem;
import org.killbill.billing.client.model.InvoicePayment;
import org.killbill.billing.client.model.InvoicePaymentTransaction;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.Payments;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.TransactionType;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestExternalRefund extends TestJaxrsBase {

    @Test(groups = "slow", description = "#255 - Scenario 0 - Can refund an automatic payment. This is a test to validate the correct behaviour.")
    public void testAutomaticPaymentAndRefund() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
        final Payments paymentsForAccount = killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions);
        final Payment payment = paymentsForAccount.get(paymentsForAccount.size() - 1);

        Invoices invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, true, requestOptions);
        final List<InvoiceItem> itemsToBeAdjusted = invoices.get(1).getItems();

        // regular refund
        final InvoicePaymentTransaction invoicePaymentTransactionRequest = new InvoicePaymentTransaction();
        invoicePaymentTransactionRequest.setAmount(BigDecimal.valueOf(249.95));
        invoicePaymentTransactionRequest.setCurrency(accountJson.getCurrency().toString());
        invoicePaymentTransactionRequest.setPaymentId(payment.getPaymentId());
        final InvoicePayment invoicePaymentRefund = killBillClient.createInvoicePaymentRefund(invoicePaymentTransactionRequest, requestOptions);
        assertNotNull(invoicePaymentRefund);

        assertSingleInvoicePaymentRefund(invoicePaymentRefund);
        assertRefundInvoiceNoAdjustments(accountJson.getAccountId());
        assertRefundAccountBalance(accountJson.getAccountId(), BigDecimal.valueOf(249.95), BigDecimal.ZERO);
    }

    @Test(groups = "slow", description = "#255 - Scenario 0 - Can refund an automatic payment over item adjustments. This is a test to validate the correct behaviour.")
    public void testAutomaticPaymentAndRefundWithAdjustments() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
        final Payments paymentsForAccount = killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions);
        final Payment payment = paymentsForAccount.get(paymentsForAccount.size() - 1);

        Invoices invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, true, requestOptions);
        final List<InvoiceItem> itemsToBeAdjusted = invoices.get(1).getItems();

        // regular refund
        final InvoicePaymentTransaction invoicePaymentTransactionRequest = new InvoicePaymentTransaction();
        invoicePaymentTransactionRequest.setAmount(BigDecimal.valueOf(249.95));
        invoicePaymentTransactionRequest.setCurrency(accountJson.getCurrency().toString());
        invoicePaymentTransactionRequest.setPaymentId(payment.getPaymentId());
        invoicePaymentTransactionRequest.setIsAdjusted(true);
        invoicePaymentTransactionRequest.setAdjustments(itemsToBeAdjusted);
        final InvoicePayment invoicePaymentRefund = killBillClient.createInvoicePaymentRefund(invoicePaymentTransactionRequest, requestOptions);
        assertNotNull(invoicePaymentRefund);

        assertSingleInvoicePaymentRefund(invoicePaymentRefund);
        assertRefundInvoiceAdjustments(accountJson.getAccountId());
        assertRefundAccountBalance(accountJson.getAccountId(), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test(groups = "slow", description = "#255 - Scenario 1 - Can refund a manual payment though an external refund")
    public void testManualPaymentAndExternalRefund() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithExternalPMBundleAndSubscriptionAndManualPayTagAndWaitForFirstInvoice();

        final Invoices invoicesForAccount = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        final Invoice unpaidInvoice = invoicesForAccount.get(1);
        assertEquals(unpaidInvoice.getBalance().compareTo(BigDecimal.valueOf(249.95)), 0);

        final Payments paymentsForAccount = killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions);
        assertEquals(paymentsForAccount.size(), 0);

        final InvoicePayment invoicePaymentRequest = new InvoicePayment();
        invoicePaymentRequest.setTargetInvoiceId(unpaidInvoice.getInvoiceId());
        invoicePaymentRequest.setAccountId(accountJson.getAccountId());
        invoicePaymentRequest.setCurrency(unpaidInvoice.getCurrency().toString());
        invoicePaymentRequest.setPurchasedAmount(unpaidInvoice.getAmount());
        final InvoicePayment invoicePayment = killBillClient.createInvoicePayment(invoicePaymentRequest, true, requestOptions);
        assertEquals(invoicePayment.getPurchasedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(invoicePayment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);

        final InvoicePaymentTransaction invoicePaymentTransactionRequest = new InvoicePaymentTransaction();
        invoicePaymentTransactionRequest.setAmount(BigDecimal.valueOf(249.95));
        invoicePaymentTransactionRequest.setPaymentId(invoicePayment.getPaymentId());
        final InvoicePayment invoicePaymentRefund = killBillClient.createInvoicePaymentRefund(invoicePaymentTransactionRequest, requestOptions);
        assertNotNull(invoicePaymentRefund);

        assertSingleInvoicePaymentRefund(invoicePaymentRefund);
        assertRefundInvoiceNoAdjustments(accountJson.getAccountId());
        assertRefundAccountBalance(accountJson.getAccountId(), BigDecimal.valueOf(249.95), BigDecimal.ZERO);
    }

    @Test(groups = "slow", description = "#255 - Scenario 1 - Can refund a manual payment though an external refund over item adjustments")
    public void testManualPaymentAndExternalRefundWithAdjustments() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithExternalPMBundleAndSubscriptionAndManualPayTagAndWaitForFirstInvoice();

        final Invoices invoicesForAccount = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        final Invoice unpaidInvoice = invoicesForAccount.get(1);
        assertEquals(unpaidInvoice.getBalance().compareTo(BigDecimal.valueOf(249.95)), 0);

        final Payments paymentsForAccount = killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions);
        assertEquals(paymentsForAccount.size(), 0);

        final InvoicePayment invoicePaymentRequest = new InvoicePayment();
        invoicePaymentRequest.setTargetInvoiceId(unpaidInvoice.getInvoiceId());
        invoicePaymentRequest.setAccountId(accountJson.getAccountId());
        invoicePaymentRequest.setCurrency(unpaidInvoice.getCurrency().toString());
        invoicePaymentRequest.setPurchasedAmount(unpaidInvoice.getAmount());
        final InvoicePayment invoicePayment = killBillClient.createInvoicePayment(invoicePaymentRequest, true, requestOptions);
        assertEquals(invoicePayment.getPurchasedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(invoicePayment.getRefundedAmount().compareTo(BigDecimal.ZERO), 0);

        final InvoicePaymentTransaction invoicePaymentTransactionRequest = new InvoicePaymentTransaction();
        invoicePaymentTransactionRequest.setAmount(BigDecimal.valueOf(249.95));
        invoicePaymentTransactionRequest.setPaymentId(invoicePayment.getPaymentId());
        invoicePaymentTransactionRequest.setIsAdjusted(true);
        invoicePaymentTransactionRequest.setAdjustments(unpaidInvoice.getItems());
        final InvoicePayment invoicePaymentRefund = killBillClient.createInvoicePaymentRefund(invoicePaymentTransactionRequest, requestOptions);
        assertNotNull(invoicePaymentRefund);

        assertSingleInvoicePaymentRefund(invoicePaymentRefund);
        assertRefundInvoiceAdjustments(accountJson.getAccountId());
        assertRefundAccountBalance(accountJson.getAccountId(), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test(groups = "slow", description = "#255 - Scenario 2a - Can refund an automatic payment though an external refund")
    public void testAutomaticPaymentAndExternalRefund() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
        // delete PM
        killBillClient.deletePaymentMethod(accountJson.getPaymentMethodId(), true, true, requestOptions);
        final Payments paymentsForAccount = killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions);
        final Payment payment = paymentsForAccount.get(paymentsForAccount.size() - 1);

        // external refund
        final InvoicePaymentTransaction invoicePaymentTransactionRequest = new InvoicePaymentTransaction();
        invoicePaymentTransactionRequest.setAmount(BigDecimal.valueOf(249.95));
        invoicePaymentTransactionRequest.setCurrency(accountJson.getCurrency().toString());
        invoicePaymentTransactionRequest.setPaymentId(payment.getPaymentId());
        final InvoicePayment invoicePaymentExternalRefund = killBillClient.createInvoicePaymentRefund(invoicePaymentTransactionRequest, true, null, requestOptions);
        assertNotNull(invoicePaymentExternalRefund);

        assertInvoicePaymentsExternalRefund(accountJson.getAccountId(), invoicePaymentExternalRefund);
        assertRefundInvoiceNoAdjustments(accountJson.getAccountId());
        assertRefundAccountBalance(accountJson.getAccountId(), BigDecimal.valueOf(249.95), BigDecimal.ZERO);

    }

    @Test(groups = "slow", description = "#255 - Scenario 2a - Can refund an automatic payment though an external refund over item adjustments")
    public void testAutomaticPaymentAndExternalRefundWithAdjustments() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();
        // delete PM
        killBillClient.deletePaymentMethod(accountJson.getPaymentMethodId(), true, true, requestOptions);

        final Payments paymentsForAccount = killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions);
        final Payment payment = paymentsForAccount.get(paymentsForAccount.size() - 1);

        final Invoices invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, true, requestOptions);
        final List<InvoiceItem> itemsToBeAdjusted = invoices.get(1).getItems();

        // external refund
        final InvoicePaymentTransaction invoicePaymentTransactionRequest = new InvoicePaymentTransaction();
        invoicePaymentTransactionRequest.setAmount(BigDecimal.valueOf(249.95));
        invoicePaymentTransactionRequest.setCurrency(accountJson.getCurrency().toString());
        invoicePaymentTransactionRequest.setPaymentId(payment.getPaymentId());
        invoicePaymentTransactionRequest.setIsAdjusted(true);
        invoicePaymentTransactionRequest.setAdjustments(itemsToBeAdjusted);
        final InvoicePayment invoicePaymentExternalRefund = killBillClient.createInvoicePaymentRefund(invoicePaymentTransactionRequest, true, null, requestOptions);
        assertNotNull(invoicePaymentExternalRefund);

        assertInvoicePaymentsExternalRefund(accountJson.getAccountId(), invoicePaymentExternalRefund);
        assertRefundInvoiceAdjustments(accountJson.getAccountId());
        assertRefundAccountBalance(accountJson.getAccountId(), BigDecimal.ZERO, BigDecimal.ZERO);

    }

    @Test(groups = "slow", description = "#255 - Scenario 2b - Can refund an automatic payment though another existing payment method")
    public void testAutomaticPaymentAndExternalRefundWithDifferentPM() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // delete PM
        killBillClient.deletePaymentMethod(accountJson.getPaymentMethodId(), true, true, requestOptions);

        // create another PM
        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, UUID.randomUUID().toString(), accountJson.getAccountId(), false, PLUGIN_NAME, info);
        final PaymentMethod otherPaymentMethod = killBillClient.createPaymentMethod(paymentMethodJson, requestOptions);

        final Payments paymentsForAccount = killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions);
        final Payment payment = paymentsForAccount.get(paymentsForAccount.size() - 1);

        final Invoices invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, true, requestOptions);
        final List<InvoiceItem> itemsToBeAdjusted = invoices.get(1).getItems();

        // external refund
        final InvoicePaymentTransaction invoicePaymentTransactionRequest = new InvoicePaymentTransaction();
        invoicePaymentTransactionRequest.setAmount(BigDecimal.valueOf(249.95));
        invoicePaymentTransactionRequest.setCurrency(accountJson.getCurrency().toString());
        invoicePaymentTransactionRequest.setPaymentId(payment.getPaymentId());
        invoicePaymentTransactionRequest.setIsAdjusted(true);
        invoicePaymentTransactionRequest.setAdjustments(itemsToBeAdjusted);
        final InvoicePayment invoicePaymentExternalRefund = killBillClient.createInvoicePaymentRefund(invoicePaymentTransactionRequest, true, otherPaymentMethod.getPaymentMethodId(), requestOptions);
        assertNotNull(invoicePaymentExternalRefund);
        assertEquals(invoicePaymentExternalRefund.getPaymentMethodId(), otherPaymentMethod.getPaymentMethodId());

        assertInvoicePaymentsExternalRefund(accountJson.getAccountId(), invoicePaymentExternalRefund);
        assertRefundInvoiceAdjustments(accountJson.getAccountId());
        assertRefundAccountBalance(accountJson.getAccountId(), BigDecimal.ZERO, BigDecimal.ZERO);

    }

    private void assertRefundInvoiceAdjustments(final UUID accountId) throws KillBillClientException {
        final Invoices invoices;
        invoices = killBillClient.getInvoicesForAccount(accountId, true, true, requestOptions);
        final Invoice invoiceWithRefund = invoices.get(1);
        assertEquals(invoiceWithRefund.getAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoiceWithRefund.getRefundAdj().compareTo(BigDecimal.valueOf(249.95).negate()), 0);
        assertEquals(invoiceWithRefund.getItems().size(), 2);
        assertEquals(invoiceWithRefund.getItems().get(0).getItemType(), InvoiceItemType.RECURRING.toString());
        assertEquals(invoiceWithRefund.getItems().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(invoiceWithRefund.getItems().get(1).getItemType(), InvoiceItemType.ITEM_ADJ.toString());
        assertEquals(invoiceWithRefund.getItems().get(1).getAmount().compareTo(BigDecimal.valueOf(249.95).negate()), 0);
    }

    private void assertRefundInvoiceNoAdjustments(final UUID accountId) throws KillBillClientException {
        final Invoices invoices = killBillClient.getInvoicesForAccount(accountId, true, true, requestOptions);
        final Invoice invoiceWithRefund = invoices.get(1);
        assertEquals(invoiceWithRefund.getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(invoiceWithRefund.getRefundAdj().compareTo(BigDecimal.valueOf(249.95).negate()), 0);
        assertEquals(invoiceWithRefund.getItems().size(), 1);
        assertEquals(invoiceWithRefund.getItems().get(0).getItemType(), InvoiceItemType.RECURRING.toString());
        assertEquals(invoiceWithRefund.getItems().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
    }

    private void assertRefundAccountBalance(final UUID accountId, final BigDecimal balanceAmount, final BigDecimal cbaAmount) throws KillBillClientException {
        final Account account = killBillClient.getAccount(accountId, true, true, requestOptions);
        assertEquals(account.getAccountBalance().compareTo(balanceAmount), 0);
        assertEquals(account.getAccountCBA().compareTo(cbaAmount), 0);
    }

    private void assertInvoicePaymentsExternalRefund(final UUID accountId, final InvoicePayment invoicePaymentExternalRefund) throws KillBillClientException {
        final InvoicePayments invoicePaymentsForAccount = killBillClient.getInvoicePaymentsForAccount(accountId, requestOptions);
        assertEquals(invoicePaymentsForAccount.size(), 2);

        // INVOICE PAYMENT FOR ORIGINAL PURCHASE
        final InvoicePayment invoicePaymentPurchase = invoicePaymentsForAccount.get(0);
        assertEquals(invoicePaymentPurchase.getPurchasedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(invoicePaymentPurchase.getCreditedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoicePaymentPurchase.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE.toString());
        assertEquals(invoicePaymentPurchase.getTransactions().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);

        // INVOICE PAYMENT FOR EXTERNAL REFUND
        final InvoicePayment creditInvoicePayment = invoicePaymentsForAccount.get(1);
        assertTrue(creditInvoicePayment.equals(invoicePaymentExternalRefund));

        assertEquals(creditInvoicePayment.getPurchasedAmount().compareTo(BigDecimal.ZERO), 0);
        assertEquals(creditInvoicePayment.getCreditedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(creditInvoicePayment.getTransactions().size(), 1);
        assertEquals(creditInvoicePayment.getTransactions().get(0).getTransactionType(), TransactionType.CREDIT.toString());
        assertEquals(creditInvoicePayment.getTransactions().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
    }

    private void assertSingleInvoicePaymentRefund(final InvoicePayment invoicePaymentRefund) {
        // ONLY ONE INVOICE PAYMENT IS GENERATED FOR BOTH, PURCHASE AND REFUND.
        assertEquals(invoicePaymentRefund.getPurchasedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(invoicePaymentRefund.getRefundedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);

        assertEquals(invoicePaymentRefund.getTransactions().size(), 2);
        assertEquals(invoicePaymentRefund.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE.toString());
        assertEquals(invoicePaymentRefund.getTransactions().get(0).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(invoicePaymentRefund.getTransactions().get(1).getTransactionType(), TransactionType.REFUND.toString());
        assertEquals(invoicePaymentRefund.getTransactions().get(1).getAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
    }

}
