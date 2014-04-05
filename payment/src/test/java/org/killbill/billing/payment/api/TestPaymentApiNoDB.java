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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.payment.MockRecurringInvoiceItem;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestPaymentApiNoDB extends PaymentTestSuiteNoDB {

    private static final Logger log = LoggerFactory.getLogger(TestPaymentApiNoDB.class);

    private Account account;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        super.beforeClass();
        account = testHelper.createTestAccount("yoyo.yahoo.com", false);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final PaymentMethodPlugin paymentMethodInfo = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
        testHelper.addTestPaymentMethod(account, paymentMethodInfo);
    }

    @Test(groups = "fast")
    public void testSimplePaymentWithNoAmount() throws Exception {
        final BigDecimal invoiceAmount = new BigDecimal("10.0011");
        final BigDecimal requestedAmount = null;
        final BigDecimal expectedAmount = invoiceAmount;

        testSimplePayment(invoiceAmount, requestedAmount, expectedAmount);
    }

    @Test(groups = "fast")
    public void testSimplePaymentWithInvoiceAmount() throws Exception {
        final BigDecimal invoiceAmount = new BigDecimal("10.0011");
        final BigDecimal requestedAmount = invoiceAmount;
        final BigDecimal expectedAmount = invoiceAmount;

        testSimplePayment(invoiceAmount, requestedAmount, expectedAmount);
    }

    @Test(groups = "fast")
    public void testSimplePaymentWithLowerAmount() throws Exception {
        final BigDecimal invoiceAmount = new BigDecimal("10.0011");
        final BigDecimal requestedAmount = new BigDecimal("8.0091");
        final BigDecimal expectedAmount = requestedAmount;

        testSimplePayment(invoiceAmount, requestedAmount, expectedAmount);
    }

    @Test(groups = "fast")
    public void testSimplePaymentWithInvalidAmount() throws Exception {
        final BigDecimal invoiceAmount = new BigDecimal("10.0011");
        final BigDecimal requestedAmount = new BigDecimal("80.0091");
        final BigDecimal expectedAmount = null;

        testSimplePayment(invoiceAmount, requestedAmount, expectedAmount);
    }

    private void testSimplePayment(final BigDecimal invoiceAmount, final BigDecimal requestedAmount, final BigDecimal expectedAmount) throws Exception {
        final LocalDate now = clock.getUTCToday();
        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD, callContext);

        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();

        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan", "test phase", null,
                                                            now,
                                                            now.plusMonths(1),
                                                            invoiceAmount,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));

        try {
            final Payment paymentInfo = paymentApi.createPayment(account, invoice.getId(), requestedAmount, callContext);
            if (expectedAmount == null) {
                fail("Expected to fail because requested amount > invoice amount");
            }
            assertNotNull(paymentInfo.getId());
            assertTrue(paymentInfo.getAmount().compareTo(expectedAmount) == 0);
            assertNotNull(paymentInfo.getPaymentNumber());
            assertEquals(paymentInfo.getPaymentStatus(), PaymentStatus.SUCCESS);
            assertEquals(paymentInfo.getAttempts().size(), 1);
            assertEquals(paymentInfo.getInvoiceId(), invoice.getId());
            assertEquals(paymentInfo.getCurrency(), Currency.USD);

            final PaymentAttempt paymentAttempt = paymentInfo.getAttempts().get(0);
            assertNotNull(paymentAttempt);
            assertNotNull(paymentAttempt.getId());
        } catch (PaymentApiException e) {
            if (expectedAmount != null) {
                fail("Failed to create payment", e);
            } else {
                log.info(e.getMessage());
                assertEquals(e.getCode(), ErrorCode.PAYMENT_AMOUNT_DENIED.getCode());
            }
        }
    }

    @Test(groups = "fast")
    public void testPaymentMethods() throws Exception {
        List<PaymentMethod> methods = paymentApi.getPaymentMethods(account, false, callContext);
        assertEquals(methods.size(), 1);

        final PaymentMethod initDefaultMethod = methods.get(0);
        assertEquals(initDefaultMethod.getId(), account.getPaymentMethodId());

        final PaymentMethodPlugin newPaymenrMethod = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
        final UUID newPaymentMethodId = paymentApi.addPaymentMethod(MockPaymentProviderPlugin.PLUGIN_NAME, account, true, newPaymenrMethod, callContext);
        Mockito.when(account.getPaymentMethodId()).thenReturn(newPaymentMethodId);

        methods = paymentApi.getPaymentMethods(account, false, callContext);
        assertEquals(methods.size(), 2);

        assertEquals(newPaymentMethodId, account.getPaymentMethodId());

        boolean failed = false;
        try {
            paymentApi.deletedPaymentMethod(account, newPaymentMethodId, false, callContext);
        } catch (PaymentApiException e) {
            failed = true;
        }
        assertTrue(failed);

        paymentApi.deletedPaymentMethod(account, initDefaultMethod.getId(), true,  callContext);
        methods = paymentApi.getPaymentMethods(account, false, callContext);
        assertEquals(methods.size(), 1);

        // NOW retry with default payment method with special flag
        paymentApi.deletedPaymentMethod(account, newPaymentMethodId, true, callContext);

        methods = paymentApi.getPaymentMethods(account, false, callContext);
        assertEquals(methods.size(), 0);
    }
}
