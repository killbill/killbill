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

package org.killbill.billing.osgi.http;

import java.io.IOException;
import java.util.Vector;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class OSGIServlet extends HttpServlet {

    private final Vector<Servlet> initializedServlets = new Vector<Servlet>();
    private final Object servletsMonitor = new Object();

    @Inject
    private DefaultServletRouter servletRouter;

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        serviceViaPlugin(req, resp);
    }

    @Override
    protected void doHead(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        serviceViaPlugin(req, resp);
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        serviceViaPlugin(req, resp);
    }

    @Override
    protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        serviceViaPlugin(req, resp);
    }

    @Override
    protected void doDelete(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        serviceViaPlugin(req, resp);
    }

    @Override
    protected void doOptions(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        serviceViaPlugin(req, resp);
    }

    private void serviceViaPlugin(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        // requestPath is the full path minus the JAX-RS prefix (/plugins)
        final String requestPath = req.getServletPath() + req.getPathInfo();


        final Servlet pluginServlet = getPluginServlet(requestPath);


        if (pluginServlet != null) {
            initializeServletIfNeeded(req, pluginServlet);
            final OSGIServletRequestWrapper requestWrapper = new OSGIServletRequestWrapper(req, servletRouter.getPluginPrefixForPath(requestPath));
            pluginServlet.service(requestWrapper, resp);
        } else {
            resp.sendError(404);
        }
    }

    // Request wrapper to hide the plugin prefix to OSGI servlets (the plugin prefix serves as a servlet path)
    private static final class OSGIServletRequestWrapper extends HttpServletRequestWrapper {

        private final String pluginPrefix;

        public OSGIServletRequestWrapper(final HttpServletRequest request, final String pluginPrefix) {
            super(request);
            this.pluginPrefix = pluginPrefix;
        }

        @Override
        public String getPathInfo() {
            return super.getPathInfo().replace(pluginPrefix, "");
        }

        @Override
        public String getContextPath() {
            return super.getContextPath() + pluginPrefix;
        }
    }

    // Hack to bridge the gap between the web container and the OSGI servlets
    private void initializeServletIfNeeded(final HttpServletRequest req, final Servlet pluginServlet) throws ServletException {
        if (!initializedServlets.contains(pluginServlet)) {
            synchronized (servletsMonitor) {
                if (!initializedServlets.contains(pluginServlet)) {
                    final ServletConfig servletConfig = (ServletConfig) req.getAttribute("killbill.osgi.servletConfig");
                    if (servletConfig != null) {
                        // TODO PIERRE The servlet will never be destroyed!
                        pluginServlet.init(servletConfig);
                        initializedServlets.add(pluginServlet);
                    }
                }
            }
        }
    }

    private Servlet getPluginServlet(final String requestPath) {
        if (requestPath != null) {
            return servletRouter.getServiceForPath(requestPath);
        } else {
            return null;
        }
    }
}
