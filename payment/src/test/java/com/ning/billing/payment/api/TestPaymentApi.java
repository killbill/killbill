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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.user.AccountBuilder;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.payment.TestHelper;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;

public abstract class TestPaymentApi {
    @Inject
    private Bus eventBus;
    @Inject
    protected PaymentApi paymentApi;
    @Inject
    protected TestHelper testHelper;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws EventBusException {
        eventBus.start();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws EventBusException {
        eventBus.stop();
    }

    @Test(enabled=true)
    public void testCreateCreditCardPayment() throws AccountApiException {
        final DateTime now = new DateTime(DateTimeZone.UTC);
        final Account account = testHelper.createTestCreditCardAccount();
        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);
        final BigDecimal amount = new BigDecimal("10.00");
        final UUID subscriptionId = UUID.randomUUID();

        invoice.addInvoiceItem(new RecurringInvoiceItem(invoice.getId(),
                                                       subscriptionId,
                                                       "test plan", "test phase",
                                                       now,
                                                       now.plusMonths(1),
                                                       amount,
                                                       new BigDecimal("1.0"),
                                                       Currency.USD,
                                                       now,
                                                       now));

        List<Either<PaymentError, PaymentInfo>> results = paymentApi.createPayment(account.getExternalKey(), Arrays.asList(invoice.getId().toString()));

        assertEquals(results.size(), 1);
        assertTrue(results.get(0).isRight());

        PaymentInfo paymentInfo = results.get(0).getRight();

        assertNotNull(paymentInfo.getPaymentId());
        assertTrue(paymentInfo.getAmount().compareTo(amount) == 0);
        assertNotNull(paymentInfo.getPaymentNumber());
        assertFalse(paymentInfo.getStatus().equals("Error"));

        PaymentAttempt paymentAttempt = paymentApi.getPaymentAttemptForPaymentId(paymentInfo.getPaymentId());
        assertNotNull(paymentAttempt);
        assertNotNull(paymentAttempt.getPaymentAttemptId());
        assertEquals(paymentAttempt.getInvoiceId(), invoice.getId());
        assertTrue(paymentAttempt.getAmount().compareTo(amount) == 0);
        assertEquals(paymentAttempt.getCurrency(), Currency.USD);
        assertEquals(paymentAttempt.getPaymentId(), paymentInfo.getPaymentId());
        assertEquals(paymentAttempt.getPaymentAttemptDate().withMillisOfSecond(0).withSecondOfMinute(0), now.withMillisOfSecond(0).withSecondOfMinute(0));

        List<PaymentInfo> paymentInfos = paymentApi.getPaymentInfo(Arrays.asList(invoice.getId().toString()));
        assertNotNull(paymentInfos);
        assertTrue(paymentInfos.size() > 0);

        PaymentInfo paymentInfoFromGet = paymentInfos.get(0);
        assertEquals(paymentInfo, paymentInfoFromGet);

        PaymentAttempt paymentAttemptFromGet = paymentApi.getPaymentAttemptForInvoiceId(invoice.getId().toString());
        assertEquals(paymentAttempt, paymentAttemptFromGet);

    }

    private PaymentProviderAccount setupAccountWithPaypalPaymentMethod() throws AccountApiException {
        final Account account = testHelper.createTestPayPalAccount();
        paymentApi.createPaymentProviderAccount(account);

        String accountKey = account.getExternalKey();

        PaypalPaymentMethodInfo paymentMethod = new PaypalPaymentMethodInfo.Builder()
                                                                           .setBaid("12345")
                                                                           .setEmail(account.getEmail())
                                                                           .setDefaultMethod(true)
                                                                           .build();
        Either<PaymentError, String> paymentMethodIdOrError = paymentApi.addPaymentMethod(accountKey, paymentMethod);

        assertTrue(paymentMethodIdOrError.isRight());
        assertNotNull(paymentMethodIdOrError.getRight());

        Either<PaymentError, PaymentMethodInfo> paymentMethodInfoOrError = paymentApi.getPaymentMethod(accountKey, paymentMethodIdOrError.getRight());

        assertTrue(paymentMethodInfoOrError.isRight());
        assertNotNull(paymentMethodInfoOrError.getRight());

        Either<PaymentError, PaymentProviderAccount> accountOrError = paymentApi.getPaymentProviderAccount(accountKey);

        assertTrue(accountOrError.isRight());

        return accountOrError.getRight();
    }

    @Test(enabled=true)
    public void testCreatePaypalPaymentMethod() throws AccountApiException {
        PaymentProviderAccount account = setupAccountWithPaypalPaymentMethod();
        assertNotNull(account);
        Either<PaymentError, List<PaymentMethodInfo>> paymentMethodsOrError = paymentApi.getPaymentMethods(account.getAccountKey());
    }

    @Test(enabled=true)
    public void testUpdatePaymentProviderAccountContact() throws AccountApiException {
        final Account account = testHelper.createTestPayPalAccount();
        paymentApi.createPaymentProviderAccount(account);

        String newName = "Tester " + RandomStringUtils.randomAlphanumeric(10);
        String newNumber = "888-888-" + RandomStringUtils.randomNumeric(4);

        final Account accountToUpdate = new AccountBuilder(account.getId())
                                                                  .name(newName)
                                                                  .firstNameLength(newName.length())
                                                                  .externalKey(account.getExternalKey())
                                                                  .phone(newNumber)
                                                                  .email(account.getEmail())
                                                                  .currency(account.getCurrency())
                                                                  .billingCycleDay(account.getBillCycleDay())
                                                                  .build();

        Either<PaymentError, Void> voidOrError = paymentApi.updatePaymentProviderAccountContact(accountToUpdate.getExternalKey());
        assertTrue(voidOrError.isRight());
    }

    @Test(enabled=true)
    public void testCannotDeleteDefaultPaymentMethod() throws AccountApiException {
        PaymentProviderAccount account = setupAccountWithPaypalPaymentMethod();

        Either<PaymentError, Void> errorOrVoid = paymentApi.deletePaymentMethod(account.getAccountKey(), account.getDefaultPaymentMethodId());

        assertTrue(errorOrVoid.isLeft());
    }

    @Test(enabled=true)
    public void testDeleteNonDefaultPaymentMethod() throws AccountApiException {
        final Account account = testHelper.createTestPayPalAccount();
        paymentApi.createPaymentProviderAccount(account);

        String accountKey = account.getExternalKey();

        PaypalPaymentMethodInfo paymentMethod1 = new PaypalPaymentMethodInfo.Builder().setDefaultMethod(false).setBaid("12345").setEmail(account.getEmail()).build();
        Either<PaymentError, String> paymentMethodIdOrError1 = paymentApi.addPaymentMethod(accountKey, paymentMethod1);

        assertTrue(paymentMethodIdOrError1.isRight());
        assertNotNull(paymentMethodIdOrError1.getRight());

        PaypalPaymentMethodInfo paymentMethod2 = new PaypalPaymentMethodInfo.Builder().setDefaultMethod(true).setBaid("12345").setEmail(account.getEmail()).build();

        Either<PaymentError, String> paymentMethodIdOrError2 = paymentApi.addPaymentMethod(accountKey, paymentMethod2);

        assertTrue(paymentMethodIdOrError2.isRight());
        assertNotNull(paymentMethodIdOrError2.getRight());

        Either<PaymentError, List<PaymentMethodInfo>> paymentMethodsOrError = paymentApi.getPaymentMethods(accountKey);

        assertTrue(paymentMethodsOrError.isRight());

        Either<PaymentError, Void> errorOrVoid1 = paymentApi.deletePaymentMethod(accountKey, paymentMethodIdOrError1.getRight());
        Either<PaymentError, Void> errorOrVoid2 = paymentApi.deletePaymentMethod(accountKey, paymentMethodIdOrError2.getRight());

        assertTrue(errorOrVoid1.isRight());
        assertTrue(errorOrVoid2.isLeft());
    }

}
