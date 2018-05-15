/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.payment.MockRecurringInvoiceItem;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;
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
    private static final PaymentOptions PAYMENT_OPTIONS = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }

        @Override
        public List<String> getPaymentControlPluginNames() {
            return ImmutableList.<String>of(InvoicePaymentControlPluginApi.PLUGIN_NAME);
        }
    };

    private final Iterable<PluginProperty> PLUGIN_PROPERTIES = ImmutableList.<PluginProperty>of();
    private Account account;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        account = testHelper.createTestAccount("yoyo.yahoo.com", false);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();
        final PaymentMethodPlugin paymentMethodInfo = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
        account = testHelper.addTestPaymentMethod(account, paymentMethodInfo);
    }

    @Test(groups = "fast")
    public void testSimpleInvoicePaymentWithNoAmount() throws Exception {
        final BigDecimal invoiceAmount = new BigDecimal("10.0011");
        final BigDecimal requestedAmount = null;
        final BigDecimal expectedAmount = null;

        testSimplePayment(invoiceAmount, requestedAmount, expectedAmount);
    }

    @Test(groups = "fast")
    public void testSimpleInvoicePaymentWithInvoiceAmount() throws Exception {
        final BigDecimal invoiceAmount = BigDecimal.TEN;
        final BigDecimal requestedAmount = invoiceAmount;
        final BigDecimal expectedAmount = invoiceAmount;

        testSimplePayment(invoiceAmount, requestedAmount, expectedAmount);
    }

    @Test(groups = "fast")
    public void testSimpleInvoicePaymentWithLowerAmount() throws Exception {
        final BigDecimal invoiceAmount = BigDecimal.TEN;
        final BigDecimal requestedAmount = BigDecimal.ONE;
        final BigDecimal expectedAmount = requestedAmount;

        testSimplePayment(invoiceAmount, requestedAmount, expectedAmount);
    }

    @Test(groups = "fast")
    public void testSimpleInvoicePaymentWithInvalidAmount() throws Exception {
        final BigDecimal invoiceAmount = BigDecimal.ONE;
        final BigDecimal requestedAmount = BigDecimal.TEN;
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

            final List<PluginProperty> properties = new ArrayList<PluginProperty>();
            final PluginProperty prop1 = new PluginProperty(InvoicePaymentControlPluginApi.PROP_IPCD_INVOICE_ID, invoice.getId().toString(), false);
            properties.add(prop1);

            final Payment paymentInfo = paymentApi.createPurchaseWithPaymentControl(account, account.getPaymentMethodId(), null, requestedAmount, account.getCurrency(),  null,
                                                                                    invoice.getId().toString(), UUID.randomUUID().toString(), properties, PAYMENT_OPTIONS, callContext);
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
            assertEquals(paymentInfo.getTransactions().get(0).getPaymentId(), paymentInfo.getId());
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
        List<PaymentMethod> methods = paymentApi.getAccountPaymentMethods(account.getId(), false, false, PLUGIN_PROPERTIES, callContext);
        assertEquals(methods.size(), 1);

        final PaymentMethod initDefaultMethod = methods.get(0);
        assertEquals(initDefaultMethod.getId(), account.getPaymentMethodId());

        final PaymentMethodPlugin newPaymentMethod = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
        account = testHelper.addTestPaymentMethod(account, newPaymentMethod, PLUGIN_PROPERTIES);

        methods = paymentApi.getAccountPaymentMethods(account.getId(), false, false, PLUGIN_PROPERTIES, callContext);
        assertEquals(methods.size(), 2);

        boolean failed = false;
        try {
            paymentApi.deletePaymentMethod(account, account.getPaymentMethodId(), false, false, PLUGIN_PROPERTIES, callContext);
        } catch (final PaymentApiException e) {
            failed = true;
        }
        assertTrue(failed);

        paymentApi.deletePaymentMethod(account, initDefaultMethod.getId(), true, false, PLUGIN_PROPERTIES, callContext);
        methods = paymentApi.getAccountPaymentMethods(account.getId(), false, false, PLUGIN_PROPERTIES, callContext);
        assertEquals(methods.size(), 1);

        // NOW retry with default payment method with special flag
        paymentApi.deletePaymentMethod(account, account.getPaymentMethodId(), true, false, PLUGIN_PROPERTIES, callContext);

        methods = paymentApi.getAccountPaymentMethods(account.getId(), false, false, PLUGIN_PROPERTIES, callContext);
        assertEquals(methods.size(), 0);

    }
}
