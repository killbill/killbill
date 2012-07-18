/*
 * Copyright 2010-2012 Ning, Inc.
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

public abstract class ExceptionMapperBase {

    private static final Logger log = LoggerFactory.getLogger(ExceptionMapperBase.class);

    protected Response buildConflictingRequestResponse(final Exception e, final UriInfo uriInfo) {
        log.warn("Conflicting request for {}: {}", uriInfo.getRequestUri(), e);
        return Response.status(Status.CONFLICT)
                       .entity(e)
                       .type(MediaType.TEXT_PLAIN_TYPE)
                       .build();
    }

    protected Response buildNotFoundResponse(final Exception e, final UriInfo uriInfo) {
        log.warn("Not found for {}: {}", uriInfo.getRequestUri(), e);
        return Response.status(Status.NOT_FOUND)
                       .entity(e)
                       .type(MediaType.TEXT_PLAIN_TYPE)
                       .build();
    }

    protected Response buildBadRequestResponse(final Exception e, final UriInfo uriInfo) {
        return buildBadRequestResponse(e.toString(), uriInfo);
    }

    protected Response buildBadRequestResponse(final String error, final UriInfo uriInfo) {
        log.warn("Bad request for {}: {}", uriInfo.getRequestUri(), error);
        return Response.status(Status.BAD_REQUEST)
                       .entity(error)
                       .type(MediaType.TEXT_PLAIN_TYPE)
                       .build();
    }

    protected Response buildInternalErrorResponse(final Exception e, final UriInfo uriInfo) {
        return buildInternalErrorResponse(e.toString(), uriInfo);
    }

    protected Response buildInternalErrorResponse(final String error, final UriInfo uriInfo) {
        log.warn("Internal error for {}: {}", uriInfo.getRequestUri(), error);
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                       .entity(error)
                       .type(MediaType.TEXT_PLAIN_TYPE)
                       .build();
    }
}
