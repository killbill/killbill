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

package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.bus.api.PersistentBus.EventBusException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.payment.MockRecurringInvoiceItem;
import com.ning.billing.payment.PaymentTestSuiteWithEmbeddedDB;

public class TestPaymentApi extends PaymentTestSuiteWithEmbeddedDB {


    private Account account;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        super.beforeClass();
        account = testHelper.createTestAccount("bobo@gmail.com", false);
    }


    @Test(groups = "slow")
    public void testCreatePaymentWithNoDefaultPaymentMethod() throws InvoiceApiException, EventBusException, PaymentApiException {


        final LocalDate now = clock.getUTCToday();
        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD, callContext);

        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final BigDecimal requestedAmount = BigDecimal.TEN;

        invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(), account.getId(),
                                                            subscriptionId,
                                                            bundleId,
                                                            "test plan", "test phase",
                                                            now,
                                                            now.plusMonths(1),
                                                            requestedAmount,
                                                            new BigDecimal("1.0"),
                                                            Currency.USD));

        try {
            paymentApi.createPayment(account, invoice.getId(), requestedAmount, callContext);
        } catch (PaymentApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD.getCode());
        }

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), callContext);
        Assert.assertEquals(payments.size(), 1);

        final Payment payment = payments.get(0);
        Assert.assertEquals(payment.getPaymentStatus(), PaymentStatus.PAYMENT_FAILURE_ABORTED);
    }
}
