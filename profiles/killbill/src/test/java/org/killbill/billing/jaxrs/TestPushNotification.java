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

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.awaitility.Awaitility;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.joda.time.DateTime;
import org.killbill.CreatorName;
import org.killbill.billing.api.FlakyRetryAnalyzer;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.TenantKey;
import org.killbill.billing.jaxrs.json.NotificationJson;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.server.DefaultServerService;
import org.killbill.billing.server.notifications.PushNotificationKey;
import org.killbill.billing.server.notifications.PushNotificationListener;
import org.killbill.billing.server.notifications.PushNotificationRetryService;
import org.killbill.billing.tenant.api.TenantKV;
import org.killbill.notificationq.DefaultNotificationQueueService;
import org.killbill.notificationq.NotificationQueueDispatcher;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService.NotificationQueueHandler;
import org.killbill.notificationq.dao.NotificationEventModelDao;
import org.killbill.queue.QueueObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.google.common.io.CharStreams;

public class TestPushNotification extends TestJaxrsBase {

    private CallbackServer callbackServer;

    private static final int SERVER_PORT = 8087;
    private static final String CALLBACK_ENDPOINT = "/callmeback";

    private volatile boolean callbackCompleted;
    private volatile boolean callbackCompletedWithError;
    private volatile int expectedNbCalls = 1;
    private volatile boolean forceToFail = false;
    private volatile int failedResponseStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        callbackServer = new CallbackServer(this, SERVER_PORT, CALLBACK_ENDPOINT);
        resetCallbackStatusProperties();
        callbackServer.startServer();
        this.expectedNbCalls = 1;
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        callbackServer.stopServer();
    }

    private void assertAllCallbacksCompleted() throws InterruptedException {
        final boolean waitForCallbacksToComplete = waitForCallbacksToComplete();
        if (!waitForCallbacksToComplete) {
            printThreadDump();
        }
        Assert.assertTrue(waitForCallbacksToComplete, "Fail to see push notification callbacks");
    }

    private boolean waitForCallbacksToComplete() throws InterruptedException {
        long remainingMs = DEFAULT_REQUEST_TIMEOUT_SEC * 1000;
        do {
            if (callbackCompleted) {
                break;
            }
            Thread.sleep(100);
            remainingMs -= 100;
        } while (remainingMs > 0);
        return (remainingMs > 0);
    }

    public void retrieveAccountWithAsserts(final String accountId) {
        try {
            // Just check we can retrieve the account with the id from the callback
            killBillClient.getAccount(UUID.fromString(accountId), requestOptions);
        } catch (final Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testPushNotification() throws Exception {
        final String callback = registerTenantForCallback();

        // set expected number of calls
        // 1st: was "eventType":"TENANT_CONFIG_CHANGE"
        // 2nd: is "eventType":"ACCOUNT_CREATION"
        this.expectedNbCalls = 2;

        // Create account to trigger a push notification
        createAccount();

        assertAllCallbacksCompleted();

        if (callbackCompletedWithError) {
            Assert.fail("Assertion during callback failed...");
        }

        unregisterTenantForCallback(callback);
    }

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

    private void unregisterTenantForCallback(final String callback) throws KillBillClientException {
        final TenantKey result = killBillClient.getCallbackNotificationForTenant(requestOptions);
        Assert.assertEquals(result.getKey(), TenantKV.TenantKey.PUSH_NOTIFICATION_CB.toString());
        Assert.assertEquals(result.getValues().size(), 1);
        Assert.assertEquals(result.getValues().get(0), callback);

        killBillClient.unregisterCallbackNotificationForTenant(requestOptions);
        final TenantKey result2 = killBillClient.getCallbackNotificationForTenant(requestOptions);
        Assert.assertEquals(result2.getKey(), TenantKV.TenantKey.PUSH_NOTIFICATION_CB.toString());
        Assert.assertEquals(result2.getValues().size(), 0);
    }

    private String registerTenantForCallback() throws KillBillClientException, InterruptedException {// Register tenant for callback
        final String callback = "http://127.0.0.1:" + SERVER_PORT + CALLBACK_ENDPOINT;
        final TenantKey result0 = killBillClient.registerCallbackNotificationForTenant(callback, requestOptions);

        Assert.assertTrue(waitForCallbacksToComplete());

        Assert.assertEquals(result0.getKey(), TenantKV.TenantKey.PUSH_NOTIFICATION_CB.toString());
        Assert.assertEquals(result0.getValues().size(), 1);
        Assert.assertEquals(result0.getValues().get(0), callback);

        // reset values
        resetCallbackStatusProperties();
        return callback;
    }

    // Flaky, see https://github.com/killbill/killbill/issues/860
    @Test(groups = "slow", retryAnalyzer = FlakyRetryAnalyzer.class)
    public void testPushNotificationRetries() throws Exception {
        final String callback = registerTenantForCallback();

        // force server to fail
        // Notifications retries are set to:
        // org.killbill.billing.server.notifications.retries=15m,1h,1d,2d
        this.forceToFail = true;

        // set expected number of calls
        // 1st: was "eventType":"TENANT_CONFIG_CHANGE"
        // 2nd: is original "eventType":"ACCOUNT_CREATION" call [force error]
        // 3rd: is 1st notification retry (+ 15m) [force error]
        // 4th: is 1st notification retry (+ 1h) [force error]
        // 5th: is 1st notification retry (+ 1d) [success]
        this.expectedNbCalls = 5;

        // Create account to trigger a push notification
        createAccount();

        assertAllCallbacksCompleted();
        Assert.assertTrue(callbackCompletedWithError);

        resetCallbackStatusProperties();

        // move clock 15 minutes and get 1st retry
        clock.addDeltaFromReality(900000);

        assertAllCallbacksCompleted();
        Assert.assertTrue(callbackCompletedWithError);

        resetCallbackStatusProperties();

        // move clock an hour and get 2nd retry
        clock.addDeltaFromReality(3600000);

        assertAllCallbacksCompleted();
        Assert.assertTrue(callbackCompletedWithError);

        resetCallbackStatusProperties();

        // make call success
        this.forceToFail = false;

        // move clock a day, get 3rd retry and wait for a success push notification
        clock.addDays(1);

        assertAllCallbacksCompleted();
        Assert.assertFalse(callbackCompletedWithError);

        unregisterTenantForCallback(callback);
    }

    // Flaky, see https://github.com/killbill/killbill/issues/860
    @Test(groups = "slow", retryAnalyzer = FlakyRetryAnalyzer.class)
    public void testPushNotificationRetriesMaxAttemptNumber() throws Exception {
        final String callback = registerTenantForCallback();

        // force server to fail
        // Notifications retries are set to:
        // org.killbill.billing.server.notifications.retries=15m,1h,1d,2d
        this.forceToFail = true;

        // set expected number of calls
        // 1st: was "eventType":"TENANT_CONFIG_CHANGE"
        // 2nd: is original "eventType":"ACCOUNT_CREATION" call [force error]
        // 3rd: is 1st notification retry (+ 15m) [force error]
        // 4th: is 2nd notification retry (+ 1h) [force error]
        // 5th: is 3rd notification retry (+ 1d) [force error]
        // 6th: is 4th notification retry (+ 2d) [force error]
        this.expectedNbCalls = 6;

        // Create account to trigger a push notification
        createAccount();

        assertAllCallbacksCompleted();
        Assert.assertTrue(callbackCompletedWithError);

        resetCallbackStatusProperties();

        // move clock 15 minutes (+10s for flakiness) and get 1st retry
        clock.addDeltaFromReality(910000);

        assertAllCallbacksCompleted();
        Assert.assertTrue(callbackCompletedWithError);

        resetCallbackStatusProperties();

        // move clock an hour (+10s for flakiness) and get 2nd retry
        clock.addDeltaFromReality(3610000);

        assertAllCallbacksCompleted();
        Assert.assertTrue(callbackCompletedWithError);

        resetCallbackStatusProperties();

        // move clock a day and get 3rd retry
        clock.addDays(1);

        assertAllCallbacksCompleted();
        Assert.assertTrue(callbackCompletedWithError);

        resetCallbackStatusProperties();

        // move clock a day and get 4rd retry
        clock.addDays(2);

        assertAllCallbacksCompleted();
        Assert.assertTrue(callbackCompletedWithError);
        resetCallbackStatusProperties();

        clock.addDays(4);

        Assert.assertFalse(waitForCallbacksToComplete());
        Assert.assertFalse(callbackCompletedWithError);

        unregisterTenantForCallback(callback);
    }

    private void resetCallbackStatusProperties() {
        // reset values
        this.callbackCompleted = false;
        this.callbackCompletedWithError = false;
    }

    public void setCompleted(final boolean withError) {
        callbackCompleted = true;
        callbackCompletedWithError = withError;
    }

    public static class CallbackServer {

        private final Server server;
        private final String callbackEndpoint;
        private final TestPushNotification test;

        public CallbackServer(final TestPushNotification test, final int port, final String callbackEndpoint) {
            this.callbackEndpoint = callbackEndpoint;
            this.test = test;
            this.server = new Server(port);
        }

        public void startServer() throws Exception {
            final ServletContextHandler context = new ServletContextHandler();
            context.setContextPath("/");
            server.setHandler(context);
            context.addServlet(new ServletHolder(new CallmebackServlet(test)), callbackEndpoint);
            server.start();
        }

        public void stopServer() throws Exception {
            server.stop();
        }
    }

    public static class CallmebackServlet extends HttpServlet {

        private static final long serialVersionUID = -5181211514918217301L;

        private static final Logger log = LoggerFactory.getLogger(CallmebackServlet.class);

        private final AtomicInteger receivedCalls;
        private final TestPushNotification test;
        private final ObjectMapper objectMapper = new ObjectMapper();

        private boolean withError;

        public CallmebackServlet(final TestPushNotification test) {
            this.test = test;
            this.receivedCalls = new AtomicInteger(0);
        }

        @Override
        protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
            final int current = receivedCalls.incrementAndGet();
            final String body = CharStreams.toString(new InputStreamReader(request.getInputStream(), "UTF-8"));

            log.info("CallmebackServlet received {} calls , current = {} at {}", current, body, getClock().getUTCNow());

            if (test.forceToFail) {
                response.setStatus(test.failedResponseStatus);
                log.info("CallmebackServlet is force to fail for testing purposes");
                test.setCompleted(true);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);

            log.info("Got body {}", body);

            final NotificationJson notification = objectMapper.readValue(body, NotificationJson.class);

            final ExtBusEventType type = ExtBusEventType.valueOf(notification.getEventType());
            switch (type) {
                case TENANT_CONFIG_CHANGE:
                    Assert.assertEquals(notification.getEventType(), "TENANT_CONFIG_CHANGE");
                    Assert.assertEquals(notification.getObjectType(), "TENANT_KVS");
                    Assert.assertNotNull(notification.getObjectId());
                    Assert.assertNull(notification.getAccountId());
                    Assert.assertNotNull(notification.getMetaData());
                    Assert.assertEquals(notification.getMetaData(), "PUSH_NOTIFICATION_CB");
                    break;
                case ACCOUNT_CREATION:
                    Assert.assertEquals(notification.getEventType(), "ACCOUNT_CREATION");
                    Assert.assertEquals(notification.getObjectType(), "ACCOUNT");
                    Assert.assertNotNull(notification.getObjectId());
                    Assert.assertNotNull(notification.getAccountId());
                    Assert.assertEquals(notification.getObjectId(), notification.getAccountId());
                    break;
            }

            test.retrieveAccountWithAsserts(notification.getObjectId());

            Assert.assertEquals(request.getHeader(PushNotificationListener.HTTP_HEADER_CONTENT_TYPE), PushNotificationListener.CONTENT_TYPE_JSON);
            stopServerWhenComplete(current, false);
        }

        private void stopServerWhenComplete(final int current, final boolean withError) {
            if (current == test.expectedNbCalls) {
                log.info("Excellent, we are done!");
                test.setCompleted(withError);
            }
        }
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
