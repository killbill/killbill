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

package com.ning.billing.jaxrs.mappers;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.jaxrs.json.BillingExceptionJson;
import com.ning.billing.util.jackson.ObjectMapper;

import com.fasterxml.jackson.core.JsonProcessingException;

public abstract class ExceptionMapperBase {

    private static final Logger log = LoggerFactory.getLogger(ExceptionMapperBase.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    protected Response buildConflictingRequestResponse(final Exception e, final UriInfo uriInfo) {
        // Log the full stacktrace
        log.warn("Conflicting request", e);
        return buildConflictingRequestResponse(exceptionToString(e), uriInfo);
    }

    private Response buildConflictingRequestResponse(final String error, final UriInfo uriInfo) {
        return Response.status(Status.CONFLICT)
                       .entity(error)
                       .type(MediaType.TEXT_PLAIN_TYPE)
                       .build();
    }

    protected Response buildNotFoundResponse(final Exception e, final UriInfo uriInfo) {
        // Log the full stacktrace
        log.info("Not found", e);
        return buildNotFoundResponse(exceptionToString(e), uriInfo);
    }

    private Response buildNotFoundResponse(final String error, final UriInfo uriInfo) {
        return Response.status(Status.NOT_FOUND)
                       .entity(error)
                       .type(MediaType.TEXT_PLAIN_TYPE)
                       .build();
    }

    protected Response buildBadRequestResponse(final Exception e, final UriInfo uriInfo) {
        // Log the full stacktrace
        log.warn("Bad request", e);
        return buildBadRequestResponse(exceptionToString(e), uriInfo);
    }

    private Response buildBadRequestResponse(final String error, final UriInfo uriInfo) {
        return Response.status(Status.BAD_REQUEST)
                       .entity(error)
                       .type(MediaType.TEXT_PLAIN_TYPE)
                       .build();
    }

    protected Response buildInternalErrorResponse(final Exception e, final UriInfo uriInfo) {
        // Log the full stacktrace
        log.warn("Internal error", e);
        return buildInternalErrorResponse(exceptionToString(e), uriInfo);
    }

    private Response buildInternalErrorResponse(final String error, final UriInfo uriInfo) {
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                       .entity(error)
                       .type(MediaType.TEXT_PLAIN_TYPE)
                       .build();
    }

    private String exceptionToString(final Exception e) {
        try {
            return mapper.writeValueAsString(new BillingExceptionJson(e));
        } catch (JsonProcessingException jsonException) {
            log.warn("Unable to serialize exception", jsonException);
        }
        return e.toString();
    }
}
