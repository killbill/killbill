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

package com.ning.billing.osgi.http;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ning.billing.osgi.api.http.ServletRouter;

@Singleton
public class OSGIServlet extends HttpServlet {

    @Inject
    private ServletRouter servletRouter;

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
        final HttpServlet pluginServlet = getPluginServlet(req);
        if (pluginServlet != null) {
            pluginServlet.service(req, resp);
        } else {
            resp.sendError(404);
        }
    }

    private HttpServlet getPluginServlet(final HttpServletRequest req) {
        final String pluginName = (String) req.getAttribute("killbill.osgi.pluginName");
        if (pluginName != null) {
            return servletRouter.getServletForPlugin(pluginName);
        } else {
            return null;
        }
    }
}
