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
package com.ning.billing.jaxrs;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.NotificationJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;

public class TestPushNotification extends TestJaxrsBase {


    private CallbackServer callbackServer;

    private final static int SERVER_PORT = 8087;
    private final static String CALLBACK_ENDPPOINT = "/callmeback";

    private volatile boolean callbackCompleted;
    private volatile boolean callbackCompletedWithError;


    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        callbackServer = new CallbackServer(this, SERVER_PORT, CALLBACK_ENDPPOINT);
        callbackCompleted = false;
        callbackCompletedWithError = false;
        callbackServer.startServer();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        callbackServer.stopServer();
    }

    private boolean waitForCallbacksToComplete() throws InterruptedException {

        long remainingMs = 20000;
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
            /* final AccountJson account = */ getAccountById(accountId);
        } catch(final Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testPushNotification() throws Exception {
        // Register tenant for callback
        registerCallbackNotificationForTenant("http://127.0.0.1:" + SERVER_PORT + CALLBACK_ENDPPOINT);
        // Create account to trigger a push notification
        createAccount();

        final boolean success = waitForCallbacksToComplete();
        if (!success) {
            Assert.fail("Fail to see push notification callbacks after 5 sec");
        }

        if (callbackCompletedWithError) {
            Assert.fail("Assertion during callback failed...");
        }
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
            this.callbackEndpoint =  callbackEndpoint;
            this.test = test;
            this.server = new Server(port);
        }

        public void startServer() throws Exception {
            final ServletContextHandler context = new ServletContextHandler();
            context.setContextPath("/");
            server.setHandler(context);
            context.addServlet(new ServletHolder(new CallmebackServlet(test,  1)), callbackEndpoint);
            server.start();
        }

        public void stopServer() throws Exception {
            server.stop();
        }
    }


    public static class CallmebackServlet extends HttpServlet {

        private static final long serialVersionUID = -5181211514918217301L;

        private final static Logger log = LoggerFactory.getLogger(CallmebackServlet.class);

        private final int expectedNbCalls;
        private final AtomicInteger receivedCalls;
        private final TestPushNotification test;
        private final ObjectMapper objectMapper = new ObjectMapper();

        private boolean withError;

        public CallmebackServlet(final TestPushNotification test, final int expectedNbCalls) {
            this.expectedNbCalls = expectedNbCalls;
            this.test = test;
            this.receivedCalls = new AtomicInteger(0);
            this.withError = false;
        }

        @Override
        protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
            final int current = receivedCalls.incrementAndGet();

            final String body = CharStreams.toString( new InputStreamReader(request.getInputStream(), "UTF-8" ));

            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);

            log.info("Got body {}", body);

            try {
                final NotificationJson notification =  objectMapper.readValue(body, NotificationJson.class);
                Assert.assertEquals(notification.getEventType(), "ACCOUNT_CREATION");
                Assert.assertEquals(notification.getObjectType(), "ACCOUNT");
                Assert.assertNotNull(notification.getObjectId());
                Assert.assertNotNull(notification.getAccountId());
                Assert.assertEquals(notification.getObjectId(), notification.getAccountId());

                test.retrieveAccountWithAsserts(notification.getObjectId());
            } catch (final AssertionError e) {
                withError = true;
            }

            log.info("CallmebackServlet received {} calls , current = {}", current, body);
            stopServerWhenComplete(current, withError);
        }


        private void stopServerWhenComplete(final int current, final boolean withError) {
            if (current == expectedNbCalls) {
                log.info("Excellent, we are done!");
                test.setCompleted(withError);
            }
        }
    }
}
