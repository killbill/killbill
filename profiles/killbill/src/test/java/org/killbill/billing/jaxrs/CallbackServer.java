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

package org.killbill.billing.jaxrs;

import javax.servlet.Servlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class CallbackServer {

    private static final int SERVER_PORT = 8087;
    private static final String CALLBACK_ENDPOINT = "/callmeback";

    private final Server server;
    private final String callbackEndpoint;
    private final Servlet servlet;

    public CallbackServer(final Servlet servlet) {
        this.callbackEndpoint = CALLBACK_ENDPOINT;
        this.servlet = servlet;
        this.server = new Server(SERVER_PORT);
    }

    public void startServer() throws Exception {
        final ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);
        context.addServlet(new ServletHolder(servlet), callbackEndpoint);
        server.start();
    }

    public void stopServer() throws Exception {
        server.stop();
    }

    public static String getServletEndpoint() {
        return "http://127.0.0.1:" + SERVER_PORT + CALLBACK_ENDPOINT;
    }
}
