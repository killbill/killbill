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
import com.ning.billing.invoice.api.InvoiceApiException;

@Provider
public class InvoiceApiExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<InvoiceApiException> {

    private final UriInfo uriInfo;

    public InvoiceApiExceptionMapper(@Context final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @Override
    public Response toResponse(final InvoiceApiException exception) {
        if (exception.getCode() == ErrorCode.INVOICE_ACCOUNT_ID_INVALID.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.INVOICE_INVALID_DATE_SEQUENCE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.INVOICE_INVALID_TRANSITION.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.INVOICE_NO_SUCH_CREDIT.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.INVOICE_NOT_FOUND.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.INVOICE_NOTHING_TO_DO.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.INVOICE_PAYMENT_BY_ATTEMPT_NOT_FOUND.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.INVOICE_PAYMENT_NOT_FOUND.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.INVOICE_TARGET_DATE_TOO_FAR_IN_THE_FUTURE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else {
            return buildBadRequestResponse(exception, uriInfo);
        }
    }
}
