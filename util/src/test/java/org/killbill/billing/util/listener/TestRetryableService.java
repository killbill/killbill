/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.util.listener;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.api.FlakyRetryAnalyzer;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.events.ControlTagCreationInternalEvent;
import org.killbill.billing.events.ControlTagDeletionInternalEvent;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApiRetryException;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.tag.DefaultTagDefinition;
import org.killbill.billing.util.tag.api.user.DefaultControlTagCreationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.queue.retry.RetryableService;
import org.killbill.queue.retry.RetryableSubscriber;
import org.killbill.queue.retry.RetryableSubscriber.SubscriberAction;
import org.killbill.queue.retry.RetryableSubscriber.SubscriberQueueHandler;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestRetryableService extends UtilTestSuiteWithEmbeddedDB {

    private static final String TEST_LISTENER = "TestListener";
    private static final ImmutableList<Period> RETRY_SCHEDULE = ImmutableList.<Period>of(Period.hours(1), Period.days(1));

    private ControlTagCreationInternalEvent event;
    private TestListener testListener;

    @BeforeClass(groups = "slow")
    public void setUpClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final ImmutableAccountData immutableAccountData = Mockito.mock(ImmutableAccountData.class);
        Mockito.when(immutableAccountInternalApi.getImmutableAccountDataByRecordId(Mockito.<Long>eq(internalCallContext.getAccountRecordId()), Mockito.<InternalTenantContext>any())).thenReturn(immutableAccountData);
    }

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        event = new DefaultControlTagCreationEvent(UUID.randomUUID(),
                                                   UUID.randomUUID(),
                                                   ObjectType.ACCOUNT,
                                                   new DefaultTagDefinition("name", "description", false),
                                                   internalCallContext.getAccountRecordId(),
                                                   internalCallContext.getTenantRecordId(),
                                                   UUID.randomUUID());

        testListener = new TestListener();
        Assert.assertEquals(testListener.eventsSeen.size(), 0);
        Assert.assertEquals(getFutureRetryableEvents().size(), 0);
    }

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        testListener.stop();
    }

    // Flaky, see https://github.com/killbill/killbill/issues/860
    @Test(groups = "slow", retryAnalyzer = FlakyRetryAnalyzer.class)
    public void testFixUp() throws Exception {
        testListener.throwRetryableException = true;

        final DateTime startTime = clock.getUTCNow();
        testListener.handleEvent(event);
        assertListenerStatus();

        Assert.assertEquals(testListener.eventsSeen.size(), 0);
        List<NotificationEventWithMetadata> futureRetryableEvents = getFutureRetryableEvents();
        Assert.assertEquals(futureRetryableEvents.size(), 1);
        Assert.assertEquals(futureRetryableEvents.get(0).getEffectiveDate().compareTo(startTime.plus(RETRY_SCHEDULE.get(0))), 0);

        clock.setTime(futureRetryableEvents.get(0).getEffectiveDate());
        assertListenerStatus();

        Assert.assertEquals(testListener.eventsSeen.size(), 0);
        futureRetryableEvents = getFutureRetryableEvents();
        Assert.assertEquals(futureRetryableEvents.size(), 1);
        Assert.assertEquals(futureRetryableEvents.get(0).getEffectiveDate().compareTo(startTime.plus(RETRY_SCHEDULE.get(1))), 0);

        testListener.throwRetryableException = false;

        clock.setTime(futureRetryableEvents.get(0).getEffectiveDate());
        assertListenerStatus();

        Assert.assertEquals(testListener.eventsSeen.size(), 1);
        Assert.assertEquals(getFutureRetryableEvents().size(), 0);
    }

    // Flaky, see https://github.com/killbill/killbill/issues/860
    @Test(groups = "slow", retryAnalyzer = FlakyRetryAnalyzer.class)
    public void testGiveUp() throws Exception {
        testListener.throwRetryableException = true;

        final DateTime startTime = clock.getUTCNow();
        testListener.handleEvent(event);
        assertListenerStatus();

        Assert.assertEquals(testListener.eventsSeen.size(), 0);
        List<NotificationEventWithMetadata> futureRetryableEvents = getFutureRetryableEvents();
        Assert.assertEquals(futureRetryableEvents.size(), 1);
        Assert.assertEquals(futureRetryableEvents.get(0).getEffectiveDate().compareTo(startTime.plus(RETRY_SCHEDULE.get(0))), 0);

        clock.setTime(futureRetryableEvents.get(0).getEffectiveDate());
        assertListenerStatus();

        Assert.assertEquals(testListener.eventsSeen.size(), 0);
        futureRetryableEvents = getFutureRetryableEvents();
        Assert.assertEquals(futureRetryableEvents.size(), 1);
        Assert.assertEquals(futureRetryableEvents.get(0).getEffectiveDate().compareTo(startTime.plus(RETRY_SCHEDULE.get(1))), 0);

        clock.setTime(futureRetryableEvents.get(0).getEffectiveDate());
        assertListenerStatus();

        Assert.assertEquals(testListener.eventsSeen.size(), 0);
        // Give up
        Assert.assertEquals(getFutureRetryableEvents().size(), 0);
    }

    @Test(groups = "slow")
    public void testNotRetryableException() throws Exception {
        testListener.throwOtherException = true;

        try {
            testListener.handleEvent(event);
            Assert.fail("Expected exception");
        } catch (final IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
        assertListenerStatus();

        Assert.assertEquals(testListener.eventsSeen.size(), 0);
        Assert.assertEquals(getFutureRetryableEvents().size(), 0);
    }

    private List<NotificationEventWithMetadata> getFutureRetryableEvents() throws NoSuchNotificationQueue {
        final NotificationQueue notificationQueue = queueService.getNotificationQueue(RetryableService.RETRYABLE_SERVICE_NAME, TEST_LISTENER);
        return ImmutableList.<NotificationEventWithMetadata>copyOf(notificationQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId()));
    }

    private final class TestListener extends RetryableService {

        private final SubscriberQueueHandler subscriberQueueHandler = new SubscriberQueueHandler();
        private final Collection<BusInternalEvent> eventsSeen = new LinkedList<BusInternalEvent>();

        private final RetryableSubscriber retryableSubscriber;

        private boolean throwRetryableException = false;
        private boolean throwOtherException = false;

        public TestListener() {
            super(queueService);

            subscriberQueueHandler.subscribe(ControlTagDeletionInternalEvent.class,
                                             new SubscriberAction<ControlTagDeletionInternalEvent>() {
                                                 @Override
                                                 public void run(final ControlTagDeletionInternalEvent event) {
                                                     Assert.fail("No handler registered");
                                                 }
                                             });
            subscriberQueueHandler.subscribe(ControlTagCreationInternalEvent.class,
                                             new SubscriberAction<ControlTagCreationInternalEvent>() {
                                                 @Override
                                                 public void run(final ControlTagCreationInternalEvent event) {
                                                     if (throwRetryableException) {
                                                         throw new InvoicePluginApiRetryException(RETRY_SCHEDULE);
                                                     } else if (throwOtherException) {
                                                         throw new IllegalArgumentException("EXPECTED");
                                                     } else {
                                                         eventsSeen.add(event);
                                                     }
                                                 }
                                             });
            this.retryableSubscriber = new RetryableSubscriber(clock, this, subscriberQueueHandler);

            initialize(TEST_LISTENER, subscriberQueueHandler);
            start();
        }

        public void handleEvent(final ControlTagCreationInternalEvent event) {
            retryableSubscriber.handleEvent(event);
        }
    }
}
