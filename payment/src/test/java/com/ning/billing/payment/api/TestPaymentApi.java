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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.MockAccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.MockInvoicePaymentApi;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoiceItem;

public abstract class TestPaymentApi {
    @Inject
    protected MockInvoicePaymentApi invoicePaymentApi;
    @Inject
    protected MockAccountUserApi accountUserApi;
    @Inject
    protected PaymentApi paymentApi;

    protected Account createAccount() {
        String name = "First" + RandomStringUtils.random(5) + " " + "Last" + RandomStringUtils.random(5);
        String externalKey = "12345";
        return accountUserApi.createAccount(UUID.randomUUID(),
                                            externalKey,
                                            "user@example.com",
                                            name,
                                            name.length(),
                                            "123-456-7890",
                                            Currency.USD,
                                            1,
                                            null,
                                            BigDecimal.ZERO);
    }

    protected Invoice createInvoice(Account account,
                                    DateTime targetDate,
                                    Currency currency) {
        Invoice invoice = new DefaultInvoice(account.getId(), targetDate, currency);

        invoicePaymentApi.add(invoice);
        return invoice;
    }

    @Test
    public void testCreatePayment() {
        final DateTime now = new DateTime();
        final Account account = createAccount();
        final Invoice invoice = createInvoice(account, now, Currency.USD);
        final BigDecimal amount = new BigDecimal("10.00");
        final UUID subscriptionId = UUID.randomUUID();

        invoice.add(new DefaultInvoiceItem(invoice.getId(),
                                           subscriptionId,
                                           now,
                                           now.plusMonths(1),
                                           "Test",
                                           amount,
                                           new BigDecimal("1.0"),
                                           Currency.USD));

        List<Either<PaymentError, PaymentInfo>> results = paymentApi.createPayment(account.getExternalKey(), Arrays.asList(invoice.getId().toString()));

        assertEquals(results.size(), 1);
        assertTrue(results.get(0).isRight());

        PaymentInfo paymentInfo = results.get(0).getRight();

        assertNotNull(paymentInfo.getId());
        assertEquals(paymentInfo.getAmount().doubleValue(), amount.doubleValue());
    }
}
