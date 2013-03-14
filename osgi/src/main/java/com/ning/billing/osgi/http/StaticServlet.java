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
import java.net.URL;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.http.HttpContext;

import com.google.common.io.Resources;

// Simple servlet to serve OSGI resources
public class StaticServlet extends HttpServlet {

    private final HttpContext httpContext;

    public StaticServlet(final HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final URL url = findResourceURL(req);
        if (url != null) {
            Resources.copy(url, resp.getOutputStream());
            resp.setStatus(200);
            return;
        }

        // If we can't find it, the container might
        final RequestDispatcher rd = getServletContext().getNamedDispatcher("default");
        final HttpServletRequest wrapped = new HttpServletRequestWrapper(req) {
            public String getServletPath() { return ""; }
        };
        rd.forward(wrapped, resp);
    }

    // TODO PIERRE HUGE HACK
    // We don't really know at this point the resource path to look for
    // e.g. if the request is for /plugins/foo/bar/baz/qux.css, should
    // we look for /qux.css? /baz/qux.css? /bar/baz/qux.css? /foo/bar/baz/qux.css?
    private URL findResourceURL(final HttpServletRequest request) {
        final String url = request.getRequestURI();
        for (int i = 0; i < url.lastIndexOf('/'); i++) {
            final int idx = url.indexOf('/', i);
            if (idx > -1) {
                final String resourceName = url.substring(idx);
                final URL match = findResourceURL(resourceName);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private URL findResourceURL(final String resourceName) {
        URL url = httpContext.getResource(resourceName);
        if (url == null) {
            // Look into the OSGI bundle JAR
            url = httpContext.getClass().getResource(resourceName);
        }
        return url;
    }
}
