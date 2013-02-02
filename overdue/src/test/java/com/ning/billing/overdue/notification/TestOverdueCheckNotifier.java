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
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.ovedue.notification.DefaultOverdueCheckNotifier;
import com.ning.billing.ovedue.notification.OverdueCheckNotificationKey;
import com.ning.billing.ovedue.notification.OverdueCheckNotifier;
import com.ning.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import com.ning.billing.overdue.listener.OverdueListener;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestOverdueCheckNotifier extends OverdueTestSuiteWithEmbeddedDB {

    private OverdueListenerMock mockListener;
    private OverdueCheckNotifier notifierForMock;

    private static final class OverdueListenerMock extends OverdueListener {

        int eventCount = 0;
        UUID latestSubscriptionId = null;

        public OverdueListenerMock(final InternalCallContextFactory internalCallContextFactory) {
            super(null, internalCallContextFactory);
        }

        @Override
        public void handleNextOverdueCheck(final OverdueCheckNotificationKey key, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
            eventCount++;
            latestSubscriptionId = key.getUuidKey();
        }

        public int getEventCount() {
            return eventCount;
        }

        public UUID getLatestSubscriptionId() {
            return latestSubscriptionId;
        }
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void setupTest() throws Exception {
        // We override the parent method on purpose, because we want to register a different DefaultOverdueCheckNotifier

        final Account account = Mockito.mock(Account.class);
        Mockito.when(accountApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);

        final Subscription subscription = Mockito.mock(Subscription.class);
        Mockito.when(entitlementApi.getSubscriptionFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);

        mockListener = new OverdueListenerMock(internalCallContextFactory);
        notifierForMock = new DefaultOverdueCheckNotifier(notificationQueueService, overdueProperties, mockListener);

        bus.start();
        notifierForMock.initialize();
        notifierForMock.start();
    }

    @Override
    @AfterMethod(groups = "slow")
    public void cleanupTest() throws Exception {
        notifierForMock.stop();
        bus.stop();
    }

    @Test(groups = "slow")
    public void test() throws Exception {
        final UUID subscriptionId = new UUID(0L, 1L);
        final Blockable blockable = Mockito.mock(Subscription.class);
        Mockito.when(blockable.getId()).thenReturn(subscriptionId);
        final DateTime now = clock.getUTCNow();
        final DateTime readyTime = now.plusMillis(2000);

        poster.insertOverdueCheckNotification(blockable, readyTime, internalCallContext);

        // Move time in the future after the notification effectiveDate
        clock.setDeltaFromReality(3000);

        await().atMost(5, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return mockListener.getEventCount() == 1;
            }
        });

        Assert.assertEquals(mockListener.getEventCount(), 1);
        Assert.assertEquals(mockListener.getLatestSubscriptionId(), subscriptionId);
    }
}
