/*
 * Copyright 2014-2026 The Billing Project, LLC
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
import org.joda.time.DateTimeZone;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.tag.dao.SystemTags;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Unit test for {@link InvoiceDispatcher} lock-failure handling — verifies the behaviour
 * described in issue #2207:
 *
 *   - When the lock fails on a non-API (notification-queue) call and
 *     {@code getRescheduleIntervalOnLock} yields a non-empty schedule, the dispatcher
 *     reschedules the invoice notification.
 *   - When the schedule is empty, the dispatcher parks the account so the failure is
 *     not silently dropped.
 *   - When the call originates from an API request, the dispatcher still rethrows as
 *     {@link InvoiceApiException} regardless of the reschedule configuration.
 *
 * Uses pure Mockito mocks (no embedded DB / Guice wiring) so it runs on machines where
 * the embedded MySQL ARM build is unavailable.
 */
public class TestInvoiceDispatcherLockHandling {

    private InvoiceDao invoiceDao;
    private GlobalLocker locker;
    private InvoiceConfig invoiceConfig;
    private Clock clock;
    private TagInternalApi tagApi;
    private ParkedAccountsManager parkedAccountsManager;
    private InternalCallContext context;
    private InvoiceDispatcher dispatcher;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        invoiceDao = Mockito.mock(InvoiceDao.class);
        locker = Mockito.mock(GlobalLocker.class);
        invoiceConfig = Mockito.mock(InvoiceConfig.class);
        clock = Mockito.mock(Clock.class);
        // Use a real ParkedAccountsManager driven by a mocked TagInternalApi — observing
        // the dispatcher's "park the account" decision boils down to observing
        // tagApi.addTag(PARK_TAG_DEFINITION_ID).
        tagApi = Mockito.mock(TagInternalApi.class);
        Mockito.when(tagApi.getTagsForAccountType(any(ObjectType.class),
                                                  Mockito.anyBoolean(),
                                                  any(InternalTenantContext.class)))
               .thenReturn(Collections.emptyList());
        parkedAccountsManager = new ParkedAccountsManager(tagApi);
        // Mockito cannot inline-mock InternalCallContext on Java 25, so construct a real one.
        context = new InternalCallContext(1L, 1L, null, null, null,
                                          UUID.randomUUID(), "test", CallOrigin.INTERNAL, UserType.TEST,
                                          null, null,
                                          new DateTime(2026, 1, 1, 0, 0, DateTimeZone.UTC),
                                          new DateTime(2026, 1, 1, 0, 0, DateTimeZone.UTC));

        // Always say the invoicing system is enabled, otherwise processAccountFromNotificationOrBusEvent
        // short-circuits before reaching the lock acquisition path.
        Mockito.when(invoiceConfig.isInvoicingSystemEnabled(any(InternalTenantContext.class))).thenReturn(true);
        Mockito.when(invoiceConfig.getMaxGlobalLockRetries()).thenReturn(1);

        Mockito.when(clock.getUTCNow()).thenReturn(new DateTime(2026, 1, 1, 0, 0, DateTimeZone.UTC));

        // Force every lock acquisition to fail to exercise the catch (LockFailedException) branch.
        Mockito.when(locker.lockWithNumberOfTries(anyString(), anyString(), anyInt()))
               .thenThrow(new LockFailedException());

        // The constructor only assigns fields. None of the LockFailedException-handling code
        // paths exercised below touch the generator, account/billing/subscription APIs,
        // plugin dispatcher, bus, notification queue, optimizer, or context factory — so we
        // pass null for those. (Mockito on Java 25 cannot inline-mock several of these
        // concrete classes.)
        dispatcher = new InvoiceDispatcher(null,
                                           null,
                                           null,
                                           null,
                                           invoiceDao,
                                           null,
                                           null,
                                           locker,
                                           null,
                                           null,
                                           invoiceConfig,
                                           clock,
                                           null,
                                           parkedAccountsManager);
    }

    @Test(groups = "fast")
    public void testLockFailureWithRescheduleConfiguredCallsRescheduleAndDoesNotPark() throws Exception {
        // Configure a non-empty schedule — a single 5-minute period is enough.
        final List<org.skife.config.TimeSpan> periods = Collections.singletonList(
                new org.skife.config.TimeSpan("5m"));
        Mockito.when(invoiceConfig.getRescheduleIntervalOnLock(any(InternalCallContext.class))).thenReturn(periods);

        final UUID accountId = UUID.randomUUID();

        // Should swallow the LockFailedException and reschedule (non-API code path).
        dispatcher.processAccountFromNotificationOrBusEvent(accountId, null, null, false, context);

        // rescheduleInvoiceNotification was called once with the account id and a future
        // DateTime computed from clock + first period.
        final ArgumentCaptor<DateTime> when = ArgumentCaptor.forClass(DateTime.class);
        Mockito.verify(invoiceDao, Mockito.times(1)).rescheduleInvoiceNotification(eq(accountId),
                                                                                   when.capture(),
                                                                                   eq(context));
        final DateTime expected = new DateTime(2026, 1, 1, 0, 5, DateTimeZone.UTC);
        assertEquals(when.getValue(), expected, "Reschedule time should equal clock + first period");

        // The account must NOT be parked when a reschedule was issued.
        Mockito.verify(tagApi, Mockito.never()).addTag(any(UUID.class),
                                                       any(ObjectType.class),
                                                       any(UUID.class),
                                                       any(InternalCallContext.class));
    }

    @Test(groups = "fast")
    public void testLockFailureWithEmptyRescheduleParksTheAccount() throws Exception {
        // Empty schedule simulates rescheduleIntervalOnLock not being configured.
        Mockito.when(invoiceConfig.getRescheduleIntervalOnLock(any(InternalCallContext.class)))
               .thenReturn(Collections.emptyList());

        final UUID accountId = UUID.randomUUID();

        // Should swallow the LockFailedException and park the account.
        dispatcher.processAccountFromNotificationOrBusEvent(accountId, null, null, false, context);

        // Nothing should be rescheduled.
        Mockito.verify(invoiceDao, Mockito.never()).rescheduleInvoiceNotification(any(UUID.class),
                                                                                  any(DateTime.class),
                                                                                  any(InternalCallContext.class));

        // The account must be parked — this is the behavioural change for #2207.
        // ParkedAccountsManager delegates to tagApi.addTag(accountId, ACCOUNT, PARK_TAG_DEFINITION_ID, ctx).
        Mockito.verify(tagApi, Mockito.times(1)).addTag(eq(accountId),
                                                        eq(ObjectType.ACCOUNT),
                                                        eq(SystemTags.PARK_TAG_DEFINITION_ID),
                                                        eq(context));
    }

    @Test(groups = "fast")
    public void testLockFailureOnApiCallStillThrowsInvoiceApiException() throws Exception {
        // Even when rescheduling is configured, API callers must surface the failure
        // rather than silently rescheduling — this preserves the original Path A
        // semantics for API callers.
        final List<org.skife.config.TimeSpan> periods = Collections.singletonList(
                new org.skife.config.TimeSpan("5m"));
        Mockito.when(invoiceConfig.getRescheduleIntervalOnLock(any(InternalCallContext.class))).thenReturn(periods);

        final UUID accountId = UUID.randomUUID();
        boolean threw = false;
        try {
            dispatcher.processAccount(true, accountId, null, null, false, true, Collections.emptyList(), context);
        } catch (final InvoiceApiException e) {
            threw = true;
        }
        assertTrue(threw, "API call should rethrow as InvoiceApiException on lock failure");
        Mockito.verify(invoiceDao, Mockito.never()).rescheduleInvoiceNotification(any(UUID.class),
                                                                                  any(DateTime.class),
                                                                                  any(InternalCallContext.class));
        Mockito.verify(tagApi, Mockito.never()).addTag(any(UUID.class),
                                                       any(ObjectType.class),
                                                       any(UUID.class),
                                                       any(InternalCallContext.class));
    }
}
