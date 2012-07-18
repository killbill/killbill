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

import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.ning.billing.ErrorCode;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;

@Provider
public class EntitlementUserApiExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<EntitlementUserApiException> {

    private final UriInfo uriInfo;

    public EntitlementUserApiExceptionMapper(@Context final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @Override
    public Response toResponse(final EntitlementUserApiException exception) {
        if (exception.getCode() == ErrorCode.ENT_ACCOUNT_IS_OVERDUE_BLOCKED.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_BUNDLE_IS_OVERDUE_BLOCKED.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CANCEL_BAD_STATE.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CHANGE_DRY_RUN_NOT_BP.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CHANGE_FUTURE_CANCELLED.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CHANGE_NON_ACTIVE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CREATE_AO_ALREADY_INCLUDED.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CREATE_AO_BP_NON_ACTIVE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CREATE_AO_NOT_AVAILABLE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CREATE_BAD_PHASE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CREATE_BP_EXISTS.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CREATE_NO_BP.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_CREATE_NO_BUNDLE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_GET_INVALID_BUNDLE_ID.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_GET_INVALID_BUNDLE_KEY.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_GET_NO_BUNDLE_FOR_SUBSCRIPTION.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_GET_NO_SUCH_BASE_SUBSCRIPTION.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_INVALID_REQUESTED_DATE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_INVALID_REQUESTED_FUTURE_DATE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_INVALID_SUBSCRIPTION_ID.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_RECREATE_BAD_STATE.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_AO_CREATE_BEFORE_BP_START.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_BP_RECREATE_MISSING_AO.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_BP_RECREATE_MISSING_AO_CREATE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_INVALID_DELETE_SET.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_MISSING_AO_DELETE_EVENT.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_NEW_EVENT_BEFORE_LAST_AO_REMAINING.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_NEW_EVENT_BEFORE_LAST_BP_REMAINING.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_NO_ACTIVE_SUBSCRIPTIONS.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_NON_EXISTENT_DELETE_EVENT.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_SUB_EMPTY.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_SUB_RECREATE_NOT_EMPTY.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_UNKNOWN_BUNDLE.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_UNKNOWN_SUBSCRIPTION.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_UNKNOWN_TYPE.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_REPAIR_VIEW_CHANGED.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.ENT_UNCANCEL_BAD_STATE.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else {
            return buildBadRequestResponse(exception, uriInfo);
        }
    }
}
