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

package org.killbill.billing.overdue.applicator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.events.OverdueChangeInternalEvent;
import org.killbill.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.overdue.config.api.OverdueStateSet;
import org.killbill.billing.overdue.notification.OverdueCheckNotifier;
import org.killbill.billing.overdue.service.DefaultOverdueService;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.xmlloader.XMLLoader;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public class TestOverdueStateApplicator extends OverdueTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testApplicator() throws Exception {
        final InputStream is = new ByteArrayInputStream(testOverdueHelper.getConfigXml().getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);

        final ImmutableAccountData account = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());

        final OverdueStateSet overdueStateSet = config.getOverdueStatesAccount();
        final OverdueState clearState = config.getOverdueStatesAccount().findState(OverdueWrapper.CLEAR_STATE_NAME);
        OverdueState state;

        state = config.getOverdueStatesAccount().findState("OD1");
        applicator.apply(clock.getUTCNow(), overdueStateSet, null, account, clearState, state, internalCallContext);
        testOverdueHelper.checkStateApplied(state);
        checkBussEvent("OD1");

        state = config.getOverdueStatesAccount().findState("OD2");
        applicator.apply(clock.getUTCNow(), overdueStateSet, null, account, clearState, state, internalCallContext);
        testOverdueHelper.checkStateApplied(state);
        checkBussEvent("OD2");

        state = config.getOverdueStatesAccount().findState("OD3");
        applicator.apply(clock.getUTCNow(), overdueStateSet, null, account, clearState, state, internalCallContext);
        testOverdueHelper.checkStateApplied(state);
        checkBussEvent("OD3");
    }

    @Test(groups = "slow")
    public void testNextCheckScheduledRelativeToEarliestUnpaidInvoice() throws Exception {
        clock.setDay(new LocalDate(2026, 9, 28));
        final InputStream is = new ByteArrayInputStream(testOverdueHelper.getConfigXml().getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        final OverdueStateSet overdueStateSet = config.getOverdueStatesAccount();
        final OverdueState clearState = overdueStateSet.findState(OverdueWrapper.CLEAR_STATE_NAME);

        final ImmutableAccountData account = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());

        // The account has an unpaid invoice that is 10 days old, and no state matches yet (OD1 needs 30 days)
        final LocalDate today = internalCallContext.toLocalDate(clock.getUTCNow());
        final LocalDate earliestUnpaid = today.minusDays(10); //2026-09-18
        final BillingState billingState = Mockito.mock(BillingState.class);
        Mockito.when(billingState.getDateOfEarliestUnpaidInvoice()).thenReturn(earliestUnpaid);

        // Nothing matched, so the applicator only schedules the next check
        applicator.apply(clock.getUTCNow(), overdueStateSet, billingState, account, clearState, clearState, internalCallContext);

        final NotificationQueue queue = notificationQueueService.getNotificationQueue(DefaultOverdueService.OVERDUE_SERVICE_NAME,
                                                                                      OverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE);
        final List<NotificationEventWithMetadata<NotificationEvent>> scheduled =
                StreamSupport.stream(queue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId()).spliterator(), false).collect(Collectors.toList());
        Assert.assertEquals(scheduled.size(), 1);

        // OD1 (30 days) is the next threshold, so the check must land on earliestUnpaid + 30 days, not on today + 30 days
        final LocalDate nextCheck = internalCallContext.toLocalDate(scheduled.get(0).getEffectiveDate());
        Assert.assertEquals(nextCheck, earliestUnpaid.plusDays(30));
    }

    private void checkBussEvent(final String state) throws Exception {
        await().atMost(10, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final List<OverdueChangeInternalEvent> events = listener.getEventsReceived();
                return events.size() == 1;
            }
        });
        final List<OverdueChangeInternalEvent> events = listener.getEventsReceived();
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(state, events.get(0).getNextOverdueStateName());
        listener.clearEventsReceived();
    }
}
