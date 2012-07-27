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

package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.mock.glue.MockClockModule;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.payment.MockRecurringInvoiceItem;
import com.ning.billing.payment.PaymentTestSuite;
import com.ning.billing.payment.TestHelper;
import com.ning.billing.payment.api.Payment.PaymentAttempt;
import com.ning.billing.payment.glue.PaymentTestModuleWithMocks;
import com.ning.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.glue.CallContextModule;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Guice(modules = {PaymentTestModuleWithMocks.class, MockClockModule.class, MockJunctionModule.class, CallContextModule.class})
public class TestPaymentApi extends PaymentTestSuite {
    private static final Logger log = LoggerFactory.getLogger(TestPaymentApi.class);

    @Inject
    private Bus eventBus;
    @Inject
    protected PaymentApi paymentApi;
    @Inject
    protected AccountUserApi accountApi;
    @Inject
    protected TestHelper testHelper;
    @Inject
    protected InvoicePaymentApi invoicePaymentApi;
    @Inject
    protected Clock clock;

    protected CallContext context;

    private Account account;

    @Inject
    public TestPaymentApi(final Clock clock) {
        context = new DefaultCallContext("Payment Tests", CallOrigin.INTERNAL, UserType.SYSTEM, clock);
    }

    @BeforeClass(groups = "fast")
    public void setupClass() throws Exception {
        account = testHelper.createTestAccount("yoyo.yahoo.com");
    }

    @BeforeMethod(groups = "fast")
    public void setUp() throws EventBusException {
        eventBus.start();
    }

    @AfterMethod(groups = "fast")
    public void tearDown() throws EventBusException {
        eventBus.stop();
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
                                                            "test plan", "test phase",
                                                            now,
                                                            now.plusMonths(1),
                                                            invoiceAmount,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));

        try {
            final Payment paymentInfo = paymentApi.createPayment(account, invoice.getId(), requestedAmount, context);
            if (expectedAmount == null) {
                fail("Expected to fail because requested amount > invoice amount");
            }
            assertNotNull(paymentInfo.getId());
            assertTrue(paymentInfo.getAmount().compareTo(expectedAmount.setScale(2, RoundingMode.HALF_EVEN)) == 0);
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
        List<PaymentMethod> methods = paymentApi.getPaymentMethods(account, false);
        assertEquals(methods.size(), 1);

        final PaymentMethod initDefaultMethod = methods.get(0);
        assertEquals(initDefaultMethod.getId(), account.getPaymentMethodId());

        final PaymentMethodPlugin newPaymenrMethod = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
        final UUID newPaymentMethodId = paymentApi.addPaymentMethod(PaymentTestModuleWithMocks.PLUGIN_TEST_NAME, account, true, newPaymenrMethod, context);
        Mockito.when(account.getPaymentMethodId()).thenReturn(newPaymentMethodId);

        methods = paymentApi.getPaymentMethods(account, false);
        assertEquals(methods.size(), 2);

        assertEquals(newPaymentMethodId, account.getPaymentMethodId());

        boolean failed = false;
        try {
            paymentApi.deletedPaymentMethod(account, newPaymentMethodId, context);
        } catch (PaymentApiException e) {
            failed = true;
        }
        assertTrue(failed);

        paymentApi.deletedPaymentMethod(account, initDefaultMethod.getId(), context);
        methods = paymentApi.getPaymentMethods(account, false);
        assertEquals(methods.size(), 1);
    }
}
