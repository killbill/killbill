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

package org.killbill.billing.jaxrs.mappers;

import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.entitlement.api.SubscriptionApiException;

@Singleton
@Provider
public class SubscriptionApiExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<SubscriptionApiException> {

    private final UriInfo uriInfo;

    public SubscriptionApiExceptionMapper(@Context final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @Override
    public Response toResponse(final SubscriptionApiException exception) {
        if (exception.getCode() == ErrorCode.SUB_ACCOUNT_IS_OVERDUE_BLOCKED.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_BUNDLE_IS_OVERDUE_BLOCKED.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CANCEL_BAD_STATE.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CHANGE_DRY_RUN_NOT_BP.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CHANGE_FUTURE_CANCELLED.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CHANGE_NON_ACTIVE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CREATE_AO_ALREADY_INCLUDED.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CREATE_AO_BP_NON_ACTIVE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CREATE_AO_NOT_AVAILABLE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CREATE_BAD_PHASE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CREATE_BP_EXISTS.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CREATE_NO_BP.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_CREATE_NO_BUNDLE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_GET_INVALID_BUNDLE_ID.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_GET_INVALID_BUNDLE_KEY.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_GET_NO_BUNDLE_FOR_SUBSCRIPTION.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_GET_NO_SUCH_BASE_SUBSCRIPTION.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_INVALID_REQUESTED_DATE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_INVALID_REQUESTED_FUTURE_DATE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_INVALID_SUBSCRIPTION_ID.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_RECREATE_BAD_STATE.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_UNCANCEL_BAD_STATE.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else {
            return fallback(exception, uriInfo);
        }
    }
}
