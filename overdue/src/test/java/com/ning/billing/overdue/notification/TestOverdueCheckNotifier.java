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

package com.ning.billing.overdue.notification;

import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import com.ning.billing.overdue.listener.OverdueListener;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.callcontext.InternalTenantContext;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestOverdueCheckNotifier extends OverdueTestSuiteWithEmbeddedDB {

    private OverdueListenerMock mockListener;
    private OverdueNotifier notifierForMock;

    private static final class OverdueListenerMock extends OverdueListener {

        int eventCount = 0;
        UUID latestAccountId = null;

        public OverdueListenerMock(final InternalCallContextFactory internalCallContextFactory) {
            super(null, getClock(), null,internalCallContextFactory);
        }

        @Override
        public void handleProcessOverdueForAccount(final UUID accountId, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
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
        //super.beforeMethod();
        // We override the parent method on purpose, because we want to register a different OverdueCheckNotifier

        final Account account = Mockito.mock(Account.class);
        Mockito.when(accountApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);

        mockListener = new OverdueListenerMock(internalCallContextFactory);
        notifierForMock = new OverdueCheckNotifier(notificationQueueService, overdueProperties, mockListener);

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
        final DateTime now = clock.getUTCNow();
        final DateTime readyTime = now.plusMillis(2000);

        final OverdueCheckNotificationKey notificationKey = new OverdueCheckNotificationKey(accountId);
        checkPoster.insertOverdueNotification(accountId, readyTime, OverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE, notificationKey, internalCallContext);

        // Move time in the future after the notification effectiveDate
        clock.setDeltaFromReality(3000);

        await().atMost(5, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return mockListener.getEventCount() == 1;
            }
        });

        Assert.assertEquals(mockListener.getEventCount(), 1);
        Assert.assertEquals(mockListener.getLatestAccountId(), accountId);
    }
}
