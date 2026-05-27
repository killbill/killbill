/*
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.invoice;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.optimizer.InvoiceOptimizer;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.config.TimeSpan;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Regression coverage for issue #2207: when {@link InvoiceDispatcher#processAccount}
 * fails to acquire the account lock and the {@code rescheduleIntervalOnLock} configuration
 * is empty (so the notification path cannot reschedule a retry), the account must be parked
 * rather than silently abandoned.
 */
public class TestInvoiceDispatcherLockHandling {

    private InvoiceGenerator generator;
    private AccountInternalApi accountApi;
    private BillingInternalApi billingApi;
    private SubscriptionBaseInternalApi subscriptionApi;
    private InvoiceDao invoiceDao;
    private InternalCallContextFactory internalCallContextFactory;
    private InvoicePluginDispatcher invoicePluginDispatcher;
    private GlobalLocker locker;
    private BusOptimizer busOptimizer;
    private NotificationQueueService notificationQueueService;
    private InvoiceConfig invoiceConfig;
    private Clock clock;
    private InvoiceOptimizer invoiceOptimizer;
    private ParkedAccountsManager parkedAccountsManager;

    private InvoiceDispatcher dispatcher;
    private InternalCallContext context;
    private UUID accountId;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        generator = Mockito.mock(InvoiceGenerator.class);
        accountApi = Mockito.mock(AccountInternalApi.class);
        billingApi = Mockito.mock(BillingInternalApi.class);
        subscriptionApi = Mockito.mock(SubscriptionBaseInternalApi.class);
        invoiceDao = Mockito.mock(InvoiceDao.class);
        internalCallContextFactory = Mockito.mock(InternalCallContextFactory.class);
        invoicePluginDispatcher = Mockito.mock(InvoicePluginDispatcher.class);
        locker = Mockito.mock(GlobalLocker.class);
        busOptimizer = Mockito.mock(BusOptimizer.class);
        notificationQueueService = Mockito.mock(NotificationQueueService.class);
        invoiceConfig = Mockito.mock(InvoiceConfig.class);
        clock = Mockito.mock(Clock.class);
        invoiceOptimizer = Mockito.mock(InvoiceOptimizer.class);
        parkedAccountsManager = Mockito.mock(ParkedAccountsManager.class);
        context = Mockito.mock(InternalCallContext.class);

        accountId = UUID.randomUUID();

        Mockito.when(clock.getUTCNow()).thenReturn(new DateTime(2024, 1, 1, 0, 0));
        Mockito.when(invoiceConfig.getMaxGlobalLockRetries()).thenReturn(1);
        Mockito.when(invoiceConfig.isInvoicingSystemEnabled(ArgumentMatchers.any(InternalCallContext.class))).thenReturn(true);
        Mockito.when(parkedAccountsManager.isParked(ArgumentMatchers.any(InternalCallContext.class))).thenReturn(false);

        Mockito.when(locker.lockWithNumberOfTries(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
               .thenThrow(new LockFailedException());

        dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi, invoiceDao,
                                           internalCallContextFactory, invoicePluginDispatcher, locker, busOptimizer,
                                           notificationQueueService, invoiceConfig, clock, invoiceOptimizer, parkedAccountsManager);
    }

    /**
     * When the lock cannot be acquired and no {@code rescheduleIntervalOnLock} is configured,
     * the account should be parked so the failure is surfaced for manual handling.
     */
    @Test(groups = "fast")
    public void testLockFailureWithoutRescheduleConfiguredParksAccount() throws Exception {
        Mockito.when(invoiceConfig.getRescheduleIntervalOnLock(ArgumentMatchers.any(InternalCallContext.class)))
               .thenReturn(Collections.<TimeSpan>emptyList());

        final LocalDate targetDate = new LocalDate(2024, 1, 1);
        final List<Invoice> invoices = dispatcher.processAccount(false, accountId, targetDate, null, false, true, Collections.emptyList(), context);

        Assert.assertTrue(invoices.isEmpty(), "Expected an empty invoice list when the lock cannot be acquired");

        // The account must be parked so the lock failure is not silently lost.
        Mockito.verify(parkedAccountsManager, Mockito.times(1)).parkAccount(accountId, context);
        // And no reschedule should have been attempted, because there is no schedule to use.
        Mockito.verify(invoiceDao, Mockito.never()).rescheduleInvoiceNotification(ArgumentMatchers.any(UUID.class),
                                                                                  ArgumentMatchers.any(DateTime.class),
                                                                                  ArgumentMatchers.any(InternalCallContext.class));
    }

    /**
     * When the lock cannot be acquired but a reschedule interval is configured, the dispatcher
     * should reschedule the notification and must <strong>not</strong> park the account.
     */
    @Test(groups = "fast")
    public void testLockFailureWithRescheduleConfiguredDoesNotParkAccount() throws Exception {
        Mockito.when(invoiceConfig.getRescheduleIntervalOnLock(ArgumentMatchers.any(InternalCallContext.class)))
               .thenReturn(List.of(new TimeSpan("15s")));

        final LocalDate targetDate = new LocalDate(2024, 1, 1);
        final List<Invoice> invoices = dispatcher.processAccount(false, accountId, targetDate, null, false, true, Collections.emptyList(), context);

        Assert.assertTrue(invoices.isEmpty(), "Expected an empty invoice list when the lock cannot be acquired");

        Mockito.verify(parkedAccountsManager, Mockito.never()).parkAccount(ArgumentMatchers.any(UUID.class), ArgumentMatchers.any(InternalCallContext.class));
        Mockito.verify(invoiceDao, Mockito.times(1)).rescheduleInvoiceNotification(ArgumentMatchers.eq(accountId),
                                                                                    ArgumentMatchers.any(DateTime.class),
                                                                                    ArgumentMatchers.eq(context));
    }

    /**
     * Lock failure on an API call should propagate as {@link InvoiceApiException} and must
     * not park the account (parking is only the right move for the notification-driven path).
     */
    @Test(groups = "fast")
    public void testLockFailureOnApiCallDoesNotPark() throws Exception {
        Mockito.when(invoiceConfig.getRescheduleIntervalOnLock(ArgumentMatchers.any(InternalCallContext.class)))
               .thenReturn(Collections.<TimeSpan>emptyList());

        final LocalDate targetDate = new LocalDate(2024, 1, 1);
        try {
            dispatcher.processAccount(true, accountId, targetDate, null, false, true, Collections.emptyList(), context);
            Assert.fail("Expected InvoiceApiException when lock fails on an API call");
        } catch (final InvoiceApiException expected) {
            // Expected
        }

        Mockito.verify(parkedAccountsManager, Mockito.never()).parkAccount(ArgumentMatchers.any(UUID.class), ArgumentMatchers.any(InternalCallContext.class));
        Mockito.verify(invoiceDao, Mockito.never()).rescheduleInvoiceNotification(ArgumentMatchers.any(UUID.class),
                                                                                   ArgumentMatchers.any(DateTime.class),
                                                                                   ArgumentMatchers.any(InternalCallContext.class));
    }

}
