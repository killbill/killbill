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

import org.joda.time.DateTime;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.model.DefaultInvoiceItem;
import com.ning.billing.payment.TestHelper;
import com.ning.billing.util.eventbus.EventBus;
import com.ning.billing.util.eventbus.EventBus.EventBusException;

public abstract class TestPaymentApi {
    @Inject
    private EventBus eventBus;
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

//    @Test(groups = "fast")
    @Test
    public void testCreatePayment() {
        final DateTime now = new DateTime();
        final Account account = testHelper.createTestAccount();
        final Invoice invoice = testHelper.createTestInvoice(account, now, Currency.USD);
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

        assertNotNull(paymentInfo.getPaymentId());
        assertEquals(paymentInfo.getAmount().doubleValue(), amount.doubleValue());
    }
}
