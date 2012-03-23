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

package com.ning.billing.payment;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.Months;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.glue.AccountModuleWithMocks;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.glue.InvoiceModuleWithMocks;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.payment.api.Either;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentInfo;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.provider.MockPaymentProviderPlugin;
import com.ning.billing.payment.provider.PaymentProviderPluginRegistry;
import com.ning.billing.payment.setup.PaymentConfig;
import com.ning.billing.payment.setup.PaymentTestModuleWithMocks;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.notificationq.MockNotificationQueue;
import com.ning.billing.util.notificationq.Notification;
import com.ning.billing.util.notificationq.NotificationQueueService;

@Guice(modules = { PaymentTestModuleWithMocks.class, AccountModuleWithMocks.class, InvoiceModuleWithMocks.class })
@Test(groups = "fast")
public class TestRetryService {
    @Inject
    private PaymentConfig paymentConfig;
    @Inject
    private Bus eventBus;
    @Inject
    private PaymentApi paymentApi;
    @Inject
    private TestHelper testHelper;
    @Inject
    private PaymentProviderPluginRegistry registry;
    @Inject
    private PaymentDao paymentDao;
    @Inject
    private RetryService retryService;
    @Inject
    private NotificationQueueService notificationQueueService;

    @Inject
    private Clock clock;

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;
    private MockNotificationQueue mockNotificationQueue;

    @BeforeClass(alwaysRun = true)
    public void initialize() throws Exception {
        retryService.initialize();
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        eventBus.start();
        retryService.start();

        mockPaymentProviderPlugin = (MockPaymentProviderPlugin)registry.getPlugin(null);
        mockNotificationQueue = (MockNotificationQueue)notificationQueueService.getNotificationQueue(RetryService.SERVICE_NAME, RetryService.QUEUE_NAME);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        retryService.stop();
        eventBus.stop();
    }

    @Test
    public void testSchedulesRetry() throws Exception {
        final Account account = testHelper.createTestCreditCardAccount();
        final Invoice invoice = testHelper.createTestInvoice(account, clock.getUTCNow(), Currency.USD);
        final BigDecimal amount = new BigDecimal("10.00");
        final UUID subscriptionId = UUID.randomUUID();

        final DateTime startDate = clock.getUTCNow();
        final DateTime endDate = startDate.plusMonths(1);
        invoice.addInvoiceItem(new RecurringInvoiceItem(invoice.getId(),
                                                       subscriptionId,
                                                       "test plan", "test phase",
                                                       startDate,
                                                       endDate,
                                                       amount,
                                                       new BigDecimal("1.0"),
                                                       Currency.USD,
                                                       clock.getUTCNow()));

        mockPaymentProviderPlugin.makeNextInvoiceFail();

        List<Either<PaymentError, PaymentInfo>> results = paymentApi.createPayment(account.getExternalKey(), Arrays.asList(invoice.getId().toString()));

        assertEquals(results.size(), 1);
        assertTrue(results.get(0).isLeft());

        List<Notification> pendingNotifications = mockNotificationQueue.getPendingEvents();

        assertEquals(pendingNotifications.size(), 1);

        Notification notification = pendingNotifications.get(0);
        PaymentAttempt paymentAttempt = paymentApi.getPaymentAttemptForInvoiceId(invoice.getId().toString());

        assertNotNull(paymentAttempt);
        assertEquals(notification.getNotificationKey(), paymentAttempt.getPaymentAttemptId().toString());

        DateTime expectedRetryDate = paymentAttempt.getPaymentAttemptDate().plusDays(paymentConfig.getPaymentRetryDays().get(0));

        assertEquals(notification.getEffectiveDate(), expectedRetryDate);
    }

    @Test(enabled = false)
    public void testRetries() throws Exception {
        final Account account = testHelper.createTestCreditCardAccount();
        final Invoice invoice = testHelper.createTestInvoice(account, clock.getUTCNow(), Currency.USD);
        final BigDecimal amount = new BigDecimal("10.00");
        final UUID subscriptionId = UUID.randomUUID();

        final DateTime now = clock.getUTCNow();

        invoice.addInvoiceItem(new RecurringInvoiceItem(invoice.getId(),
                                                       subscriptionId,
                                                       "test plan", "test phase",
                                                       now,
                                                       now.plusMonths(1),
                                                       amount,
                                                       new BigDecimal("1.0"),
                                                       Currency.USD,
                                                       now));

        int numberOfDays = paymentConfig.getPaymentRetryDays().get(0);
        DateTime nextRetryDate = now.plusDays(numberOfDays);
        PaymentAttempt paymentAttempt = new PaymentAttempt(UUID.randomUUID(), invoice).cloner()
                                                                                      .setRetryCount(1)
                                                                                      .setPaymentAttemptDate(now)
                                                                                      .build();

        PaymentAttempt attempt = paymentDao.createPaymentAttempt(paymentAttempt);
        retryService.scheduleRetry(paymentAttempt, nextRetryDate);
        ((ClockMock)clock).setDeltaFromReality(Days.days(numberOfDays).toStandardSeconds().getSeconds() * 1000);
        Thread.sleep(2000);

        List<Notification> pendingNotifications = mockNotificationQueue.getPendingEvents();
        assertEquals(pendingNotifications.size(), 0);

        List<PaymentInfo> paymentInfos = paymentApi.getPaymentInfo(Arrays.asList(invoice.getId().toString()));
        assertEquals(paymentInfos.size(), 1);

        PaymentInfo paymentInfo = paymentInfos.get(0);
        assertEquals(paymentInfo.getStatus(), PaymentStatus.Processed.toString());

        PaymentAttempt updatedAttempt = paymentApi.getPaymentAttemptForInvoiceId(invoice.getId().toString());
        assertEquals(paymentInfo.getPaymentId(), updatedAttempt.getPaymentId());

    }
}
