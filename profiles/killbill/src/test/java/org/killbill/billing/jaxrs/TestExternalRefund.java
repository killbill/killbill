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

import org.joda.time.DateTime;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Invoice;
import org.killbill.billing.client.model.InvoiceItem;
import org.killbill.billing.client.model.InvoicePayment;
import org.killbill.billing.client.model.InvoicePaymentTransaction;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Payment;
import org.killbill.billing.client.model.Payments;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestExternalRefund extends TestJaxrsBase {

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
        assertEquals(invoicePaymentRefund.getPurchasedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
        assertEquals(invoicePaymentRefund.getRefundedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);
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
        final InvoicePayment invoicePaymentRefund = killBillClient.createInvoicePaymentRefund(invoicePaymentTransactionRequest, true, requestOptions);
        assertNotNull(invoicePaymentRefund);
        assertEquals(invoicePaymentRefund.getCreditedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);

        // TODO check account balance
        // TODO check invoice items

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
        final InvoicePayment invoicePaymentRefund = killBillClient.createInvoicePaymentRefund(invoicePaymentTransactionRequest, true, requestOptions);
        assertNotNull(invoicePaymentRefund);
        assertEquals(invoicePaymentRefund.getCreditedAmount().compareTo(BigDecimal.valueOf(249.95)), 0);

        // TODO check account balance
        // TODO check invoice items

    }

    @Test(groups = "slow", description = "#255 - Scenario 2b - Can refund an automatic payment though an external refund")
    public void testAutomaticPaymentAndRefundWithDifferentPM() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // TODO complete test

    }

}
