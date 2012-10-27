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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.io.CharStreams;

public class TestTenant extends TestJaxrsBase {


    private CallbackServer callbackServer;

    private final static int SERVER_PORT = 8087;
    private final static String CALLBACK_ENDPPOINT = "/callmeback";


    @BeforeMethod(groups = "slow")
    public void startServer() throws Exception {
        callbackServer = new CallbackServer(SERVER_PORT, CALLBACK_ENDPPOINT, null);
        //callbackServer.startServer();
    }

    @AfterMethod(groups = "slow")
    public void stopServer() throws Exception {
        //callbackServer.stopServer();
    }

    @Test(groups = "slow")
    public void testTenant() throws Exception {

        /*
        final String apiKeyTenant = "yoyo";
        final String apiSecretTenant = "yoyoisf3ommars";

        final String location = createTenant(apiKeyTenant, apiSecretTenant);
        Assert.assertNotNull(location);

        final String tenantId = extractTenantIdFromLocation(location);

        // Retrieves by Id based on Location returned
        final Response response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        Assert.assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
*/

        registerCallbackNotificationForTenant("http://127.0.0.1:" + SERVER_PORT + CALLBACK_ENDPPOINT);

        // Excellent, now create an account and check that callback is called
        createAccount();

    }

    private String extractTenantIdFromLocation(final String location) {
        final String [] parts = location.split("/");
        return parts[parts.length - 1];
    }


    public class CallbackServer {

        private final Server server;
        private final String callbackEndpoint;
        private final String expectedTenantId;

        public CallbackServer(final int port, final String callbackEndpoint, final String expectedTenantId) {
            this.callbackEndpoint =  callbackEndpoint;
            this.expectedTenantId = expectedTenantId;
            this.server = new Server(port);
        }

        public void startServer() throws Exception {
            final ServletContextHandler context = new ServletContextHandler();
            context.setContextPath("/");
            server.setHandler(context);
            context.addServlet(new ServletHolder(new CallmebackServlet(server, expectedTenantId, 1)), callbackEndpoint);
            server.start();
            server.join();
        }

        public void stopServer() throws Exception {
            server.stop();
        }
    }


    public static class CallmebackServlet extends HttpServlet {

        private static final long serialVersionUID = -5181211514918217301L;

        private final static Logger log = LoggerFactory.getLogger(CallmebackServlet.class);

        private final Server server;
        private final String expectedTenantId;
        private final int expectedNbCalls;
        private final AtomicInteger receivedCalls;

        public CallmebackServlet(final Server server, final String expectedTenantId, final int expectedNbCalls) {
            this.server = server;
            this.expectedTenantId = expectedTenantId;
            this.expectedNbCalls = expectedNbCalls;
            this.receivedCalls = new AtomicInteger(0);
        }

        @Override
        protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
        {

            final int current = receivedCalls.incrementAndGet();

            final String body = CharStreams.toString( new InputStreamReader(request.getInputStream(), "UTF-8" ));

            log.info("CallmebackServlet received {} calls , current = {}", current, body);

            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);

            stopServerWhenComplete(current);
        }


        private void stopServerWhenComplete(final int current) {
            if (current == expectedNbCalls) {
                try {
                    server.stop();
                    log.info("Callmeback server stopped succesfully");
                } catch (final Exception e) {
                    log.warn("Failed to stop jetty Callmeback server");
                }
            }
        }
    }
}
