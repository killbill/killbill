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

package org.killbill.billing.server.modules;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.apache.shiro.mgt.SessionStorageEvaluator;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.util.RequestPairSource;
import org.killbill.billing.jaxrs.resources.JaxrsResource;

public class KillBillSessionStorageEvaluator implements SessionStorageEvaluator {

    @Override
    public boolean isSessionStorageEnabled(final Subject subject) {
        if (subject.getSession(false) != null) {
            // Use what already exists
            return true;
        }

        return isSessionCreationEnabled(subject);
    }

    private boolean isSessionCreationEnabled(final Subject requestPairSource) {
        if (requestPairSource instanceof RequestPairSource) {
            final RequestPairSource source = (RequestPairSource) requestPairSource;
            return isSessionCreationEnabled(source.getServletRequest());
        }
        return false; // By default
    }

    private boolean isSessionCreationEnabled(final ServletRequest request) {
        if (request != null) {
            // Only create new sessions via the /1.0/kb/security/permissions endpoint, as this is what is used today
            // by Kaui to initiate the session.
            // If we have another use-case one day, we could think about introducing a proper 'login' endpoint...
            return isPermissionsLookupCall(request);
        }
        return false; // By default
    }

    private boolean isPermissionsLookupCall(final ServletRequest request) {
        if (request instanceof HttpServletRequest) {
            final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
            final String path = httpServletRequest.getPathInfo();
            return (JaxrsResource.SECURITY_PATH + "/permissions").equals(path);
        }
        return false;
    }
}
