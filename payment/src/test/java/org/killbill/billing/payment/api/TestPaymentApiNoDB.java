/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.payment.MockRecurringInvoiceItem;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.control.InvoicePaymentControlPluginApi;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestPaymentApiNoDB extends PaymentTestSuiteNoDB {

    private static final Logger log = LoggerFactory.getLogger(TestPaymentApiNoDB.class);

    private final Iterable<PluginProperty> PLUGIN_PROPERTIES = ImmutableList.<PluginProperty>of();
    private final static PaymentOptions PAYMENT_OPTIONS = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }
        @Override
        public String getPaymentControlPluginName() {
            return InvoicePaymentControlPluginApi.PLUGIN_NAME;
        }
    };

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
        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);

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
            final DirectPayment paymentInfo = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(),
                                                                                          invoice.getId().toString(), UUID.randomUUID().toString(), PLUGIN_PROPERTIES, PAYMENT_OPTIONS, callContext);
            if (expectedAmount == null) {
                fail("Expected to fail because requested amount > invoice amount");
            }
            assertNotNull(paymentInfo.getId());
            assertTrue(paymentInfo.getPurchasedAmount().compareTo(expectedAmount) == 0);
            assertNotNull(paymentInfo.getPaymentNumber());
            assertEquals(paymentInfo.getExternalKey(), invoice.getId().toString());
            assertEquals(paymentInfo.getCurrency(), Currency.USD);
            assertTrue(paymentInfo.getTransactions().get(0).getAmount().compareTo(expectedAmount) == 0);
            assertEquals(paymentInfo.getTransactions().get(0).getCurrency(), Currency.USD);
            assertEquals(paymentInfo.getTransactions().get(0).getDirectPaymentId(), paymentInfo.getId());
            assertEquals(paymentInfo.getTransactions().get(0).getTransactionType(), TransactionType.PURCHASE);
            assertEquals(paymentInfo.getTransactions().get(0).getTransactionStatus(), TransactionStatus.SUCCESS);

        } catch (final PaymentApiException e) {
            if (expectedAmount != null) {
                fail("Failed to create payment", e);
            } else {
                log.info(e.getMessage());
            }
        }
    }

    @Test(groups = "fast")
    public void testPaymentMethods() throws Exception {
        List<PaymentMethod> methods = paymentApi.getAccountPaymentMethods(account.getId(), false, PLUGIN_PROPERTIES, callContext);
        assertEquals(methods.size(), 1);

        final PaymentMethod initDefaultMethod = methods.get(0);
        assertEquals(initDefaultMethod.getId(), account.getPaymentMethodId());

        final PaymentMethodPlugin newPaymenrMethod = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
        final UUID newPaymentMethodId = paymentApi.addPaymentMethod(UUID.randomUUID().toString(), account, MockPaymentProviderPlugin.PLUGIN_NAME, true, newPaymenrMethod, PLUGIN_PROPERTIES, callContext);
        Mockito.when(account.getPaymentMethodId()).thenReturn(newPaymentMethodId);

        methods = paymentApi.getAccountPaymentMethods(account.getId(), false, PLUGIN_PROPERTIES, callContext);
        assertEquals(methods.size(), 2);

        assertEquals(newPaymentMethodId, account.getPaymentMethodId());

        boolean failed = false;
        try {
            paymentApi.deletePaymentMethod(account, newPaymentMethodId, false, PLUGIN_PROPERTIES, callContext);
        } catch (final PaymentApiException e) {
            failed = true;
        }
        assertTrue(failed);

        paymentApi.deletePaymentMethod(account, initDefaultMethod.getId(), true, PLUGIN_PROPERTIES, callContext);
        methods = paymentApi.getAccountPaymentMethods(account.getId(), false, PLUGIN_PROPERTIES, callContext);
        assertEquals(methods.size(), 1);

        // NOW retry with default payment method with special flag
        paymentApi.deletePaymentMethod(account, newPaymentMethodId, true, PLUGIN_PROPERTIES, callContext);

        methods = paymentApi.getAccountPaymentMethods(account.getId(), false, PLUGIN_PROPERTIES, callContext);
        assertEquals(methods.size(), 0);
    }
}
