/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.mappers;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingResponse extends Response {

    private static final Logger log = LoggerFactory.getLogger(LoggingResponse.class);

    private final Exception e;
    private final Response response;

    public LoggingResponse(final Exception e, final Response response) {
        this.e = e;
        this.response = response;
    }

    @Override
    public Object getEntity() {
        // Delay logging until the entity is retrieved: this is to avoid double logging with TimedResourceInterceptor
        // which needs to access exception mappers to get the response status
        if (response.getStatus() == Status.CONFLICT.getStatusCode()) {
            log.warn("Conflicting request", e);
        } else if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
            log.debug("Not found", e);
        } else if (response.getStatus() == Status.BAD_REQUEST.getStatusCode()) {
            log.warn("Bad request", e);
        } else if (response.getStatus() == Status.UNAUTHORIZED.getStatusCode()) {
            log.debug("Authorization error", e);
        } else if (response.getStatus() == Status.INTERNAL_SERVER_ERROR.getStatusCode()) {
            log.warn("Internal error", e);
        }

        return response.getEntity();
    }

    @Override
    public int getStatus() {
        return response.getStatus();
    }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {
        return response.getMetadata();
    }
}
