/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.BillingExceptionBase;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.entitlement.api.BlockingApiException;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.jaxrs.json.BillingExceptionJson;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.subscription.api.SubscriptionBillingApiException;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseRepairException;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

public abstract class ExceptionMapperBase {

    private static final Logger log = LoggerFactory.getLogger(ExceptionMapperBase.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String QUERY_WITH_STACK_TRACE = "withStackTrace";

    protected Response fallback(final Exception exception, final UriInfo uriInfo) {
        if (exception.getCause() == null) {
            return buildBadRequestResponse(exception, uriInfo);
        } else {
            return doFallback(exception, uriInfo);
        }
    }

    private Response doFallback(final Exception exception, final UriInfo uriInfo) {
        if (!(exception.getCause() instanceof BillingExceptionBase)) {
            return buildBadRequestResponse(exception, uriInfo);
        }

        final BillingExceptionBase cause = (BillingExceptionBase) exception.getCause();
        if (cause instanceof AccountApiException) {
            final AccountApiExceptionMapper mapper = new AccountApiExceptionMapper(uriInfo);
            return mapper.toResponse((AccountApiException) cause);
        } else if (cause instanceof BlockingApiException) {
            final BlockingApiExceptionMapper mapper = new BlockingApiExceptionMapper(uriInfo);
            return mapper.toResponse((BlockingApiException) cause);
        } else if (cause instanceof CatalogApiException) {
            final CatalogApiExceptionMapper mapper = new CatalogApiExceptionMapper(uriInfo);
            return mapper.toResponse((CatalogApiException) cause);
        } else if (cause instanceof EntitlementApiException) {
            final EntitlementApiExceptionMapper mapper = new EntitlementApiExceptionMapper(uriInfo);
            return mapper.toResponse((EntitlementApiException) cause);
        } else if (cause instanceof EntityPersistenceException) {
            final EntityPersistenceExceptionMapper mapper = new EntityPersistenceExceptionMapper(uriInfo);
            return mapper.toResponse((EntityPersistenceException) cause);
        } else if (cause instanceof InvoiceApiException) {
            final InvoiceApiExceptionMapper mapper = new InvoiceApiExceptionMapper(uriInfo);
            return mapper.toResponse((InvoiceApiException) cause);
        } else if (cause instanceof OverdueApiException) {
            final OverdueApiExceptionMapper mapper = new OverdueApiExceptionMapper(uriInfo);
            return mapper.toResponse((OverdueApiException) cause);
        } else if (cause instanceof PaymentApiException) {
            final PaymentApiExceptionMapper mapper = new PaymentApiExceptionMapper(uriInfo);
            return mapper.toResponse((PaymentApiException) cause);
        } else if (cause instanceof SubscriptionApiException) {
            final SubscriptionApiExceptionMapper mapper = new SubscriptionApiExceptionMapper(uriInfo);
            return mapper.toResponse((SubscriptionApiException) cause);
        } else if (cause instanceof SubscriptionBillingApiException) {
            final SubscriptionBillingApiExceptionMapper mapper = new SubscriptionBillingApiExceptionMapper(uriInfo);
            return mapper.toResponse((SubscriptionBillingApiException) cause);
        } else if (cause instanceof SubscriptionBaseRepairException) {
            final SubscriptionRepairExceptionMapper mapper = new SubscriptionRepairExceptionMapper(uriInfo);
            return mapper.toResponse((SubscriptionBaseRepairException) cause);
        } else if (cause instanceof TagApiException) {
            final TagApiExceptionMapper mapper = new TagApiExceptionMapper(uriInfo);
            return mapper.toResponse((TagApiException) cause);
        } else if (cause instanceof TagDefinitionApiException) {
            final TagDefinitionApiExceptionMapper mapper = new TagDefinitionApiExceptionMapper(uriInfo);
            return mapper.toResponse((TagDefinitionApiException) cause);
        } else {
            return buildBadRequestResponse(cause, uriInfo);
        }
    }

    protected Response buildConflictingRequestResponse(final Exception e, final UriInfo uriInfo) {
        final Response.ResponseBuilder responseBuilder = Response.status(Status.CONFLICT);
        serializeException(e, uriInfo, responseBuilder);
        return new LoggingResponse(e, responseBuilder.build());
    }

    protected Response buildNotFoundResponse(final Exception e, final UriInfo uriInfo) {
        final Response.ResponseBuilder responseBuilder = Response.status(Status.NOT_FOUND);
        serializeException(e, uriInfo, responseBuilder);
        return new LoggingResponse(e, responseBuilder.build());
    }

    protected Response buildBadRequestResponse(final Exception e, final UriInfo uriInfo) {
        final Response.ResponseBuilder responseBuilder = Response.status(Status.BAD_REQUEST);
        serializeException(e, uriInfo, responseBuilder);
        return new LoggingResponse(e, responseBuilder.build());
    }

    protected Response buildAuthorizationErrorResponse(final Exception e, final UriInfo uriInfo) {
        // TODO Forbidden?
        final Response.ResponseBuilder responseBuilder = Response.status(Status.UNAUTHORIZED);
        serializeException(e, uriInfo, responseBuilder);
        return new LoggingResponse(e, responseBuilder.build());
    }

    protected Response buildInternalErrorResponse(final Exception e, final UriInfo uriInfo) {
        final Response.ResponseBuilder responseBuilder = Response.status(Status.INTERNAL_SERVER_ERROR);
        serializeException(e, uriInfo, responseBuilder);
        return new LoggingResponse(e, responseBuilder.build());
    }

    protected Response buildPluginTimeoutResponse(final Exception e, final UriInfo uriInfo) {
        // 504 - Gateway Timeout
        final Response.ResponseBuilder responseBuilder = Response.status(504);
        serializeException(e, uriInfo, responseBuilder);
        return new LoggingResponse(e, responseBuilder.build());
    }

    protected void serializeException(final Exception e, final UriInfo uriInfo, final Response.ResponseBuilder responseBuilder) {
        final boolean withStackTrace = uriInfo.getQueryParameters() != null && "true".equals(uriInfo.getQueryParameters().getFirst(QUERY_WITH_STACK_TRACE));
        final BillingExceptionJson billingExceptionJson = new BillingExceptionJson(e, withStackTrace);

        try {
            final String billingExceptionJsonAsString = mapper.writeValueAsString(billingExceptionJson);
            responseBuilder.entity(billingExceptionJsonAsString).type(MediaType.APPLICATION_JSON);
        } catch (final JsonProcessingException jsonException) {
            log.warn("Unable to serialize exception", jsonException);
            responseBuilder.entity(e.toString()).type(MediaType.TEXT_PLAIN_TYPE);
        }
    }
}
