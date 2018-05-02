/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.server.DefaultServerService;
import org.killbill.billing.server.notifications.PushNotificationKey;
import org.killbill.notificationq.NotificationQueueDispatcher;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.queue.QueueObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TestPushNotification extends TestJaxrsBase {

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/726")
    public void testVerify726Backport() throws Exception {
        // Record an event without the metadata field
        final PushNotificationKeyPre726 key = new PushNotificationKeyPre726(UUID.randomUUID(),
                                                                            UUID.randomUUID(),
                                                                            UUID.randomUUID().toString(),
                                                                            UUID.randomUUID().toString(),
                                                                            UUID.randomUUID(),
                                                                            1,
                                                                            UUID.randomUUID().toString());
        final String eventJson = QueueObjectMapper.get().writeValueAsString(key);
        // Need to serialize it manually, to reflect the correct class name
        final NotificationEventModelDao notificationEventModelDao = new NotificationEventModelDao(CreatorName.get(),
                                                                                                  clock.getUTCNow(),
                                                                                                  PushNotificationKey.class.getName(),
                                                                                                  eventJson,
                                                                                                  UUID.randomUUID(),
                                                                                                  internalCallContext.getAccountRecordId(),
                                                                                                  internalCallContext.getTenantRecordId(),
                                                                                                  UUID.randomUUID(),
                                                                                                  clock.getUTCNow(),
                                                                                                  DefaultServerService.SERVER_SERVICE + ":testVerify726Backport");

        final AtomicReference<PushNotificationKey> notification = new AtomicReference<PushNotificationKey>();
        // Need to create a custom queue to extract the deserialized event
        final NotificationQueue notificationQueue = notificationQueueService.createNotificationQueue(DefaultServerService.SERVER_SERVICE,
                                                                                                     "testVerify726Backport",
                                                                                                     new NotificationQueueHandler() {
                                                                                                         @Override
                                                                                                         public void handleReadyNotification(final NotificationEvent notificationKey, final DateTime eventDateTime, final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
                                                                                                             if (!(notificationKey instanceof PushNotificationKey)) {
                                                                                                                 Assert.fail();
                                                                                                                 return;
                                                                                                             }
                                                                                                             final PushNotificationKey key = (PushNotificationKey) notificationKey;
                                                                                                             notification.set(key);
                                                                                                         }
                                                                                                     }
                                                                                                    );
        try {
            notificationQueue.startQueue();
            ((NotificationQueueDispatcher) notificationQueueService).getDao().insertEntry(notificationEventModelDao);
            Awaitility.await()
                      .atMost(10, TimeUnit.SECONDS)
                      .until(new Callable<Boolean>() {
                          @Override
                          public Boolean call() {
                              return notification.get() != null;
                          }
                      });
        } finally {
            notificationQueue.stopQueue();
        }

        final PushNotificationKey retrievedKey = notification.get();
        Assert.assertEquals(retrievedKey.getTenantId(), key.tenantId);
        Assert.assertEquals(retrievedKey.getAccountId(), key.accountId);
        Assert.assertEquals(retrievedKey.getEventType(), key.eventType);
        Assert.assertEquals(retrievedKey.getObjectType(), key.objectType);
        Assert.assertEquals(retrievedKey.getObjectId(), key.objectId);
        Assert.assertEquals(retrievedKey.getAttemptNumber(), (Integer) key.attemptNumber);
        Assert.assertEquals(retrievedKey.getUrl(), key.url);
        // New NULL field
        Assert.assertNull(retrievedKey.getMetaData());
    }

    @Test(groups = "slow")
    public void testPushNotificationRetries() throws Exception {
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 1);

        // force server to fail
        // Notifications retries are set to:
        // org.killbill.billing.server.notifications.retries=15m,1h,1d,2d
        callbackServlet.forceToFail.set(true);

        // 1st: was "eventType":"TENANT_CONFIG_CHANGE"
        // 2nd: is original "eventType":"ACCOUNT_CREATION" call [force error]
        // 3rd: is 1st notification retry (+ 15m) [force error]
        // 4th: is 1st notification retry (+ 1h) [force error]
        // 5th: is 1st notification retry (+ 1d) [success]

        // Create account to trigger a push notification
        createAccountNoEvent(null);
        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callbackServlet.receivedCalls.get() == 2;
                      }
                  });
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 2);

        // move clock 15 minutes and get 1st retry
        clock.addDeltaFromReality(900000);

        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callbackServlet.receivedCalls.get() == 3;
                      }
                  });
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 3);

        // move clock an hour and get 2nd retry
        clock.addDeltaFromReality(3600000);

        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callbackServlet.receivedCalls.get() == 4;
                      }
                  });
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 4);

        // make call success
        callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CREATION);
        callbackServlet.forceToFail.set(false);

        // move clock a day, get 3rd retry and wait for a success push notification
        clock.addDays(1);

        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callbackServlet.receivedCalls.get() == 5;
                      }
                  });
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 5);
    }

    @Test(groups = "slow")
    public void testPushNotificationRetriesMaxAttemptNumber() throws Exception {
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 1);

        // force server to fail
        // Notifications retries are set to:
        // org.killbill.billing.server.notifications.retries=15m,1h,1d,2d
        callbackServlet.forceToFail.set(true);

        // 1st: was "eventType":"TENANT_CONFIG_CHANGE"
        // 2nd: is original "eventType":"ACCOUNT_CREATION" call [force error]
        // 3rd: is 1st notification retry (+ 15m) [force error]
        // 4th: is 2nd notification retry (+ 1h) [force error]
        // 5th: is 3rd notification retry (+ 1d) [force error]
        // 6th: is 4th notification retry (+ 2d) [force error]

        // Create account to trigger a push notification
        createAccountNoEvent(null);
        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callbackServlet.receivedCalls.get() == 2;
                      }
                  });
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 2);

        // move clock 15 minutes (+10s for flakiness) and get 1st retry
        clock.addDeltaFromReality(910000);

        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callbackServlet.receivedCalls.get() == 3;
                      }
                  });
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 3);

        // move clock an hour (+10s for flakiness) and get 2nd retry
        clock.addDeltaFromReality(3610000);

        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callbackServlet.receivedCalls.get() == 4;
                      }
                  });
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 4);

        // move clock a day and get 3rd retry
        clock.addDays(1);

        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callbackServlet.receivedCalls.get() == 5;
                      }
                  });
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 5);

        // move clock a day and get 4th retry
        clock.addDays(2);

        Awaitility.await()
                  .atMost(10, TimeUnit.SECONDS)
                  .until(new Callable<Boolean>() {
                      @Override
                      public Boolean call() throws Exception {
                          return callbackServlet.receivedCalls.get() == 6;
                      }
                  });
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 6);

        clock.addDays(4);

        callbackServlet.assertListenerStatus();
        Assert.assertEquals(callbackServlet.receivedCalls.get(), 6);
    }

    public static final class PushNotificationKeyPre726 implements NotificationEvent {

        public UUID tenantId;
        public UUID accountId;
        public String eventType;
        public String objectType;
        public UUID objectId;
        public int attemptNumber;
        public String url;

        @JsonCreator
        public PushNotificationKeyPre726(@JsonProperty("tenantId") final UUID tenantId,
                                         @JsonProperty("accountId") final UUID accountId,
                                         @JsonProperty("eventType") final String eventType,
                                         @JsonProperty("objectType") final String objectType,
                                         @JsonProperty("objectId") final UUID objectId,
                                         @JsonProperty("attemptNumber") final int attemptNumber,
                                         @JsonProperty("url") final String url) {
            this.tenantId = tenantId;
            this.accountId = accountId;
            this.eventType = eventType;
            this.objectType = objectType;
            this.objectId = objectId;
            this.attemptNumber = attemptNumber;
            this.url = url;
        }
    }
}
