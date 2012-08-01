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

package com.ning.billing.jaxrs;

import java.math.BigDecimal;
import java.util.List;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.AccountJson;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentMethodJson;
import com.ning.billing.payment.provider.ExternalPaymentProviderPlugin;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestInvoice extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testInvoiceOk() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<InvoiceJsonSimple> invoices = getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(invoices.size(), 2);

        // Check we can retrieve an individual invoice
        final InvoiceJsonSimple invoiceJsonSimple = invoices.get(0);
        final InvoiceJsonSimple firstInvoiceJson = getInvoice(invoiceJsonSimple.getInvoiceId());
        assertEquals(firstInvoiceJson, invoiceJsonSimple);

        // Then create a dryRun Invoice
        final DateTime futureDate = clock.getUTCNow().plusMonths(1).plusDays(3);
        createDryRunInvoice(accountJson.getAccountId(), futureDate);

        // The one more time with no DryRun
        createInvoice(accountJson.getAccountId(), futureDate);

        // Check again # invoices, should be 3 this time
        final List<InvoiceJsonSimple> newInvoiceList = getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(newInvoiceList.size(), 3);
    }

    @Test(groups = "slow")
    public void testInvoicePayments() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final List<InvoiceJsonSimple> invoices = getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(invoices.size(), 2);

        for (final InvoiceJsonSimple cur : invoices) {
            final List<PaymentJsonSimple> objFromJson = getPaymentsForInvoice(cur.getInvoiceId());

            if (cur.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                assertEquals(objFromJson.size(), 0);
            } else {
                assertEquals(objFromJson.size(), 1);
                assertEquals(cur.getAmount().compareTo(objFromJson.get(0).getAmount()), 0);
            }
        }
    }

    @Test(groups = "slow")
    public void testInvoiceCreatePayment() throws Exception {
        clock.setTime(new DateTime(2012, 4, 25, 0, 3, 42, 0));

        // STEPH MISSING SET ACCOUNT AUTO_PAY_OFF
        final AccountJson accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Get the invoices
        final List<InvoiceJsonSimple> invoices = getInvoicesForAccount(accountJson.getAccountId());
        assertEquals(invoices.size(), 2);

        for (final InvoiceJsonSimple cur : invoices) {
            if (cur.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // CREATE INSTA PAYMENT
            final List<PaymentJsonSimple> objFromJson = createInstaPayment(accountJson, cur);
            assertEquals(objFromJson.size(), 1);
            assertEquals(cur.getAmount().compareTo(objFromJson.get(0).getAmount()), 0);
        }
    }

    @Test(groups = "slow")
    public void testExternalPayment() throws Exception {
        final AccountJson accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Verify we didn't get any payment
        final List<PaymentJsonSimple> noPaymentsFromJson = getPaymentsForAccount(accountJson.getAccountId());
        assertEquals(noPaymentsFromJson.size(), 0);

        // Get the invoices
        final List<InvoiceJsonSimple> invoices = getInvoicesForAccount(accountJson.getAccountId());
        // 2 invoices but look for the non zero dollar one
        assertEquals(invoices.size(), 2);
        final String invoiceId = invoices.get(1).getInvoiceId();

        // Post an external payment
        final BigDecimal paidAmount = BigDecimal.TEN;
        createExternalPayment(accountJson, invoiceId, paidAmount);

        // Verify we indeed got the payment
        final List<PaymentJsonSimple> paymentsFromJson = getPaymentsForAccount(accountJson.getAccountId());
        assertEquals(paymentsFromJson.size(), 1);
        assertEquals(paymentsFromJson.get(0).getPaidAmount().compareTo(paidAmount), 0);

        // Check the PaymentMethod from paymentMethodId returned in the Payment object
        final String paymentMethodId = paymentsFromJson.get(0).getPaymentMethodId();
        final PaymentMethodJson paymentMethodJson = getPaymentMethod(paymentMethodId);
        assertEquals(paymentMethodJson.getPaymentMethodId(), paymentMethodId);
        assertEquals(paymentMethodJson.getAccountId(), accountJson.getAccountId());
        assertEquals(paymentMethodJson.getPluginName(), ExternalPaymentProviderPlugin.PLUGIN_NAME);
        assertNull(paymentMethodJson.getPluginInfo());
    }
}
