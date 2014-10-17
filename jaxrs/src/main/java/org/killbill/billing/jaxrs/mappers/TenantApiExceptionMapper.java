/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import org.killbill.billing.ErrorCode;
import org.killbill.billing.tenant.api.TenantApiException;

@Singleton
@Provider
public class TenantApiExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<TenantApiException> {

    private final UriInfo uriInfo;

    public TenantApiExceptionMapper(@Context final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @Override
    public Response toResponse(final TenantApiException exception) {
        if (exception.getCode() == ErrorCode.TENANT_ALREADY_EXISTS.getCode()) {
            return buildConflictingRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.TENANT_DOES_NOT_EXIST_FOR_ID.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.TENANT_DOES_NOT_EXIST_FOR_KEY.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.TENANT_DOES_NOT_EXIST_FOR_API_KEY.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.TENANT_CREATION_FAILED.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.TENANT_UPDATE_FAILED.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.TENANT_NO_SUCH_KEY.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else {
            return fallback(exception, uriInfo);
        }
    }
}
