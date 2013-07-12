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

import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import com.ning.billing.ErrorCode;
import com.ning.billing.subscription.api.timeline.SubscriptionRepairException;

@Singleton
@Provider
public class EntitlementRepairExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<SubscriptionRepairException> {

    private final UriInfo uriInfo;

    public EntitlementRepairExceptionMapper(@Context final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @Override
    public Response toResponse(final SubscriptionRepairException exception) {
        if (exception.getCode() == ErrorCode.SUB_REPAIR_AO_CREATE_BEFORE_BP_START.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_BP_RECREATE_MISSING_AO.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_BP_RECREATE_MISSING_AO_CREATE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_INVALID_DELETE_SET.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_MISSING_AO_DELETE_EVENT.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_NEW_EVENT_BEFORE_LAST_AO_REMAINING.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_NEW_EVENT_BEFORE_LAST_BP_REMAINING.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_NO_ACTIVE_SUBSCRIPTIONS.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_NON_EXISTENT_DELETE_EVENT.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_SUB_EMPTY.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_SUB_RECREATE_NOT_EMPTY.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_UNKNOWN_BUNDLE.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_UNKNOWN_SUBSCRIPTION.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_UNKNOWN_TYPE.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.SUB_REPAIR_VIEW_CHANGED.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else {
            return buildBadRequestResponse(exception, uriInfo);
        }
    }
}
