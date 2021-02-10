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
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.payment.api.PaymentApiException;

@Singleton
@Provider
public class PaymentApiExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<PaymentApiException> {

    private final UriInfo uriInfo;

    public PaymentApiExceptionMapper(@Context final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @Override
    public Response toResponse(final PaymentApiException exception) {
        if (exception.getCode() == ErrorCode.PAYMENT_ADD_PAYMENT_METHOD.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_CREATE_PAYMENT.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_REFUND_AMOUNT_NEGATIVE_OR_NULL.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_DEL_DEFAULT_PAYMENT_METHOD.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_DEL_PAYMENT_METHOD.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_GET_PAYMENT_METHODS.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_INTERNAL_ERROR.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_NO_DEFAULT_PAYMENT_METHOD.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_NO_SUCH_PAYMENT.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_NO_SUCH_PAYMENT_METHOD.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_NULL_INVOICE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_PLUGIN_TIMEOUT.getCode()) {
            return buildPluginTimeoutResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_PLUGIN_GET_PAYMENT_INFO.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_REFRESH_PAYMENT_METHOD.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_UPD_PAYMENT_METHOD.getCode()) {
            return buildInternalErrorResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_INVALID_PARAMETER.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.PAYMENT_PLUGIN_API_ABORTED.getCode()) {
            return buildPaymentAbortedResponse(exception, uriInfo);
        } else {
            return fallback(exception, uriInfo);
        }
    }

    private Response buildPaymentAbortedResponse(final PaymentApiException exception, final UriInfo uriInfo) {
        final Response.ResponseBuilder responseBuilder = Response.status(422);
        serializeException(exception, uriInfo, responseBuilder);
        return responseBuilder.build();
    }
}
