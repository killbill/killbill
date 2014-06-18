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

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.BillingExceptionBase;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.entitlement.api.BlockingApiException;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.jaxrs.json.BillingExceptionJson;
import org.killbill.billing.overdue.OverdueApiException;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.subscription.api.SubscriptionBillingApiException;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseRepairException;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.email.EmailApiException;
import org.killbill.billing.util.jackson.ObjectMapper;

import com.fasterxml.jackson.core.JsonProcessingException;

public abstract class ExceptionMapperBase {

    private static final Logger log = LoggerFactory.getLogger(ExceptionMapperBase.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    protected Response fallback(final Exception exception, final UriInfo uriInfo) {
        if (exception.getCause() == null) {
            return buildBadRequestResponse(exception, uriInfo);
        } else {
            return doFallback(exception, uriInfo);
        }
    }

    private Response doFallback(final Exception exception, final UriInfo uriInfo) {
        if (exception.getCause() == null || !(exception.getCause() instanceof BillingExceptionBase)) {
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
        } else if (cause instanceof EmailApiException) {
            final EmailApiExceptionMapper mapper = new EmailApiExceptionMapper(uriInfo);
            return mapper.toResponse((EmailApiException) cause);
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

    protected Response buildAuthorizationErrorResponse(final Exception e, final UriInfo uriInfo) {
        // Log the full stacktrace
        log.warn("Authorization error", e);
        return buildAuthorizationErrorResponse(exceptionToString(e), uriInfo);
    }

    private Response buildAuthorizationErrorResponse(final String error, final UriInfo uriInfo) {
        return Response.status(Status.UNAUTHORIZED) // TODO Forbidden?
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
        } catch (final JsonProcessingException jsonException) {
            log.warn("Unable to serialize exception", jsonException);
        }
        return e.toString();
    }
}
