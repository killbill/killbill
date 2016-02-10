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

import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.killbill.billing.util.callcontext.InternalCallContextFactory.ObjectDoesNotExist;

@Singleton
@Provider
public class IllegalStateExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<IllegalStateException> {

    private final UriInfo uriInfo;

    public IllegalStateExceptionMapper(@Context final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @Override
    public Response toResponse(final IllegalStateException exception) {
        if (exception instanceof ObjectDoesNotExist) {
            // Likely object for wrong tenant
            return buildNotFoundResponse(exception, uriInfo);
        } else {
            return buildInternalErrorResponse(exception, uriInfo);
        }
    }
}
