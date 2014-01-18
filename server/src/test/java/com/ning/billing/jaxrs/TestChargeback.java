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

package com.ning.billing.jaxrs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.client.KillBillClientException;
import com.ning.billing.client.model.Account;
import com.ning.billing.client.model.Chargeback;
import com.ning.billing.client.model.Invoice;
import com.ning.billing.client.model.Payment;
import com.ning.billing.client.model.Subscription;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestChargeback extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can create a chargeback")
    public void testAddChargeback() throws Exception {
        final Payment payment = createAccountWithInvoiceAndPayment();
        createAndVerifyChargeback(payment);
    }

    @Test(groups = "slow", description = "Can create multiple chargebacks")
    public void testMultipleChargeback() throws Exception {
        final Payment payment = createAccountWithInvoiceAndPayment();

        // We get a 249.95 payment so we do 4 chargeback and then the fifth should fail
        final Chargeback input = new Chargeback();
        input.setAmount(new BigDecimal("50.00"));
        input.setPaymentId(payment.getPaymentId());

        int count = 4;
        while (count-- > 0) {
            assertNotNull(killBillClient.createChargeBack(input, createdBy, reason, comment));
        }

        // Last attempt should fail because this is more than the Payment
        try {
            killBillClient.createChargeBack(input, createdBy, reason, comment);
            fail();
        } catch (final KillBillClientException e) {
        }

        // Find the chargeback by account
        List<Chargeback> chargebacks = killBillClient.getChargebacksForAccount(payment.getAccountId());
        assertEquals(chargebacks.size(), 4);
        for (final Chargeback chargeBack : chargebacks) {
            assertTrue(chargeBack.getAmount().compareTo(input.getAmount()) == 0);
            assertEquals(chargeBack.getPaymentId(), input.getPaymentId());
        }

        // Find the chargeback by payment
        chargebacks = killBillClient.getChargebacksForPayment(payment.getPaymentId());
        assertEquals(chargebacks.size(), 4);
    }

    @Test(groups = "slow", description = "Can add a chargeback for deleted payment methods")
    public void testAddChargebackForDeletedPaymentMethod() throws Exception {
        final Payment payment = createAccountWithInvoiceAndPayment();

        // Check the payment method exists
        assertEquals(killBillClient.getAccount(payment.getAccountId()).getPaymentMethodId(), payment.getPaymentMethodId());
        assertEquals(killBillClient.getPaymentMethod(payment.getPaymentMethodId()).getAccountId(), payment.getAccountId());

        // Delete the payment method
        killBillClient.deletePaymentMethod(payment.getPaymentMethodId(), true, createdBy, reason, comment);

        // Check the payment method was deleted
        assertNull(killBillClient.getAccount(payment.getAccountId()).getPaymentMethodId());

        createAndVerifyChargeback(payment);
    }

    @Test(groups = "slow", description = "Cannot add a chargeback for non existent payment")
    public void testInvoicePaymentDoesNotExist() throws Exception {
        final Chargeback input = new Chargeback();
        input.setAmount(BigDecimal.TEN);
        input.setPaymentId(UUID.randomUUID());
        assertNull(killBillClient.createChargeBack(input, createdBy, reason, comment));
    }

    @Test(groups = "slow", description = "Cannot add a badly formatted chargeback")
    public void testBadRequest() throws Exception {
        final Payment payment = createAccountWithInvoiceAndPayment();

        final Chargeback input = new Chargeback();
        input.setAmount(BigDecimal.TEN.negate());
        input.setPaymentId(payment.getPaymentId());

        try {
            killBillClient.createChargeBack(input, createdBy, reason, comment);
            fail();
        } catch (final KillBillClientException e) {
        }
    }

    @Test(groups = "slow", description = "Accounts can have zero chargeback")
    public void testNoChargebackForAccount() throws Exception {
        Assert.assertEquals(killBillClient.getChargebacksForAccount(UUID.randomUUID()).size(), 0);
    }

    @Test(groups = "slow", description = "Payments can have zero chargeback")
    public void testNoChargebackForPayment() throws Exception {
        Assert.assertEquals(killBillClient.getChargebacksForPayment(UUID.randomUUID()).size(), 0);
    }

    private void createAndVerifyChargeback(final Payment payment) throws KillBillClientException {
        // Create the chargeback
        final Chargeback chargeback = new Chargeback();
        chargeback.setPaymentId(payment.getPaymentId());
        chargeback.setAmount(BigDecimal.TEN);
        final Chargeback chargebackJson = killBillClient.createChargeBack(chargeback, createdBy, reason, comment);
        assertEquals(chargebackJson.getAmount().compareTo(chargeback.getAmount()), 0);
        assertEquals(chargebackJson.getPaymentId(), chargeback.getPaymentId());

        // Find the chargeback by account
        List<Chargeback> chargebacks = killBillClient.getChargebacksForAccount(payment.getAccountId());
        assertEquals(chargebacks.size(), 1);
        assertEquals(chargebacks.get(0).getAmount().compareTo(chargeback.getAmount()), 0);
        assertEquals(chargebacks.get(0).getPaymentId(), chargeback.getPaymentId());

        // Find the chargeback by payment
        chargebacks = killBillClient.getChargebacksForPayment(payment.getPaymentId());
        assertEquals(chargebacks.size(), 1);
        assertEquals(chargebacks.get(0).getAmount().compareTo(chargeback.getAmount()), 0);
        assertEquals(chargebacks.get(0).getPaymentId(), chargeback.getPaymentId());
    }

    private Payment createAccountWithInvoiceAndPayment() throws Exception {
        final Invoice invoice = createAccountWithInvoice();
        return getPayment(invoice);
    }

    private Invoice createAccountWithInvoice() throws Exception {
        // Create account
        final Account accountJson = createAccountWithDefaultPaymentMethod();

        // Create subscription
        final Subscription subscriptionJson = createEntitlement(accountJson.getAccountId(), "6253283", "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        assertNotNull(subscriptionJson);

        // Move after the trial period to trigger an invoice with a non-zero invoice item
        clock.addDays(32);
        crappyWaitForLackOfProperSynchonization();

        // Retrieve the invoice
        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId());
        // We should have two invoices, one for the trial (zero dollar amount) and one for the first month
        assertEquals(invoices.size(), 2);
        assertTrue(invoices.get(1).getAmount().doubleValue() > 0);

        return invoices.get(1);
    }

    private Payment getPayment(final Invoice invoice) throws KillBillClientException {
        final List<Payment> payments = killBillClient.getPaymentsForInvoice(invoice.getInvoiceId());
        assertNotNull(payments);
        assertEquals(payments.size(), 1);
        return payments.get(0);
    }
}
