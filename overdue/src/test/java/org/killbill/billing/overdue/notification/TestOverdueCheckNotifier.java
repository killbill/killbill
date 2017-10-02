/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.overdue.notification;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import org.killbill.billing.overdue.listener.OverdueDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestOverdueCheckNotifier extends OverdueTestSuiteWithEmbeddedDB {

    private OverdueDispatcherMock mockDispatcher;
    private OverdueNotifier notifierForMock;

    private static final class OverdueDispatcherMock extends OverdueDispatcher {

        int eventCount = 0;
        UUID latestAccountId = null;

        public OverdueDispatcherMock(final InternalCallContextFactory internalCallContextFactory) {
            super(null);
        }

        @Override
        public void processOverdueForAccount(final UUID accountId, final DateTime effectiveDate, final InternalCallContext context) {
            eventCount++;
            latestAccountId = accountId;
        }

        public int getEventCount() {
            return eventCount;
        }

        public UUID getLatestAccountId() {
            return latestAccountId;
        }
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        // We override the parent method on purpose, because we want to register a different OverdueCheckNotifier

        cleanupAllTables();

        mockDispatcher = new OverdueDispatcherMock(internalCallContextFactory);
        notifierForMock = new OverdueCheckNotifier(notificationQueueService, overdueProperties, internalCallContextFactory, mockDispatcher);

        notifierForMock.initialize();
        notifierForMock.start();
    }

    @Override
    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        notifierForMock.stop();
        super.afterMethod();
    }

    @Test(groups = "slow")
    public void test() throws Exception {
        final UUID accountId = new UUID(0L, 1L);
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(accountApi.getImmutableAccountDataByRecordId(Mockito.<UUID>eq(internalCallContext.getAccountRecordId()), Mockito.<InternalTenantContext>any())).thenReturn(account);
        final DateTime now = clock.getUTCNow();
        final DateTime readyTime = now.plusMillis(2000);

        final OverdueCheckNotificationKey notificationKey = new OverdueCheckNotificationKey(accountId);
        checkPoster.insertOverdueNotification(accountId, readyTime, OverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE, notificationKey, internalCallContext);

        // Move time in the future after the notification effectiveDate
        clock.setDeltaFromReality(3000);

        await().atMost(5, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return mockDispatcher.getEventCount() == 1;
            }
        });

        Assert.assertEquals(mockDispatcher.getEventCount(), 1);
        Assert.assertEquals(mockDispatcher.getLatestAccountId(), accountId);
    }
}
