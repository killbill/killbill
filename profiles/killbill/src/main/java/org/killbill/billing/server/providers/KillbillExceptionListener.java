/*
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.billing.server.providers;

import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEvent.Type;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class KillbillExceptionListener implements ApplicationEventListener, RequestEventListener {

    private static final Logger log = LoggerFactory.getLogger(KillbillExceptionListener.class);

    @Override
    public void onEvent(final ApplicationEvent event) {
    }

    @Override
    public RequestEventListener onRequest(final RequestEvent requestEvent) {
        return this;
    }

    @Override
    public void onEvent(final RequestEvent event) {
        if (event.getType() != Type.EXCEPTION_MAPPING_FINISHED) {
            return;
        }
        final ContainerResponse containerResponse = event.getContainerResponse();
        if (containerResponse == null) {
            return;
        }

        final int statusCode = containerResponse.getStatus();
        final Throwable e = event.getException();
        if (statusCode == Status.CONFLICT.getStatusCode()) {
            log.warn("Conflicting request", e);
        } else if (statusCode == Status.NOT_FOUND.getStatusCode()) {
            log.debug("Not found", e);
        } else if (statusCode == Status.BAD_REQUEST.getStatusCode()) {
            log.warn("Bad request", e);
        } else if (statusCode == Status.UNAUTHORIZED.getStatusCode()) {
            log.debug("Unauthorized", e);
        } else if (statusCode == Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
            log.warn("Internal error", e);
        } else if (statusCode == 504) {
            log.debug("Plugin Timeout", e);
        } else {
            log.debug("Exception during request", e);
        }
    }
}
