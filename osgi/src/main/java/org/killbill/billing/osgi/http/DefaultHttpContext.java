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
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.http.HttpContext;

public class DefaultHttpContext implements HttpContext {

    @Override
    public boolean handleSecurity(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        // Security should have already been handled by Shiro
        return true;
    }

    @Override
    public URL getResource(final String name) {
        // Maybe it's in our classpath?
        return DefaultHttpContext.class.getClassLoader().getResource(name);
    }

    @Override
    public String getMimeType(final String name) {
        return null;
    }
}
