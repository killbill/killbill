/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.jaxrs.resources;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.util.TagHelper;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;
import com.ning.billing.invoice.api.EmptyInvoiceEvent;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.jaxrs.util.KillbillEventHandler;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.userrequest.CompletionUserRequestBase;

@Path(JaxrsResource.SUBSCRIPTIONS_PATH)
public class SubscriptionResource extends JaxRsResourceBase {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionResource.class);
    private static final String ID_PARAM_NAME = "subscriptionId";
    private static final String CUSTOM_FIELD_URI = JaxrsResource.CUSTOM_FIELDS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";
    private static final String TAG_URI = JaxrsResource.TAGS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";

    private final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTime();

    private final EntitlementUserApi entitlementApi;
    private final Context context;
    private final JaxrsUriBuilder uriBuilder;	
    private final KillbillEventHandler killbillHandler;
    
    @Inject
    public SubscriptionResource(final JaxrsUriBuilder uriBuilder, final EntitlementUserApi entitlementApi,
            final Context context, final KillbillEventHandler killbillHandler,
            final TagUserApi tagUserApi, final TagHelper tagHelper, final CustomFieldUserApi customFieldUserApi) {
        super(uriBuilder, tagUserApi, tagHelper, customFieldUserApi);
        this.uriBuilder = uriBuilder;
        this.entitlementApi = entitlementApi;
        this.context = context;
        this.killbillHandler = killbillHandler;
    }

    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getSubscription(@PathParam("subscriptionId") final String subscriptionId) throws EntitlementUserApiException {

        try {
            UUID uuid = UUID.fromString(subscriptionId);
            Subscription subscription = entitlementApi.getSubscriptionFromId(uuid);
            SubscriptionJsonNoEvents json = new SubscriptionJsonNoEvents(subscription);
            return Response.status(Status.OK).entity(json).build();
        } catch (EntitlementUserApiException e) {
            if (e.getCode() == ErrorCode.ENT_INVALID_SUBSCRIPTION_ID.getCode()) {
                return Response.status(Status.NO_CONTENT).build();
            } else {
                throw e;
            }
        }
    }
  

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createSubscription(final SubscriptionJsonNoEvents subscription,
            @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
            @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
            @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {


        SubscriptionCallCompletionCallback<Subscription> callback = new SubscriptionCallCompletionCallback<Subscription>() {
            @Override
            public Subscription doOperation(final CallContext ctx) throws EntitlementUserApiException, InterruptedException, TimeoutException {

                DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;        
                UUID uuid = UUID.fromString(subscription.getBundleId());

                PlanPhaseSpecifier spec =  new PlanPhaseSpecifier(subscription.getProductName(),
                        ProductCategory.valueOf(subscription.getProductCategory()),
                        BillingPeriod.valueOf(subscription.getBillingPeriod()), subscription.getPriceList(), null);
                return entitlementApi.createSubscription(uuid, spec, inputDate, ctx);
            }
            @Override
            public boolean isImmOperation() {
                return true;
            }
            @Override
            public Response doResponseOk(final Subscription createdSubscription) {
                return uriBuilder.buildResponse(SubscriptionResource.class, "getSubscription", createdSubscription.getId());
            }
        };
        SubscriptionCallCompletion<Subscription> callCompletionCreation = new SubscriptionCallCompletion<Subscription>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, createdBy, reason, comment);
    }

    @PUT
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    public Response changeSubscriptionPlan(final SubscriptionJsonNoEvents subscription,
            @PathParam("subscriptionId") final String subscriptionId,
            @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
            @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
            @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {

        SubscriptionCallCompletionCallback<Response> callback = new SubscriptionCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(CallContext ctx)
                    throws EntitlementUserApiException, InterruptedException,
                    TimeoutException {
                try {
                    UUID uuid = UUID.fromString(subscriptionId);
                    Subscription current = entitlementApi.getSubscriptionFromId(uuid);
                    DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
                    isImmediateOp = current.changePlan(subscription.getProductName(),  BillingPeriod.valueOf(subscription.getBillingPeriod()), subscription.getPriceList(), inputDate, ctx);
                    return Response.status(Status.OK).build();
                } catch (EntitlementUserApiException e) {
                    log.warn("Subscription not found: " + subscriptionId , e);
                    return Response.status(Status.NO_CONTENT).build();
                }
            }
            @Override
            public boolean isImmOperation() {
                return isImmediateOp;
            }
            @Override
            public Response doResponseOk(Response operationResponse) {
                if (operationResponse.getStatus() != Status.OK.getStatusCode()) {
                    return operationResponse;
                }
                try {
                    return getSubscription(subscriptionId);
                } catch (EntitlementUserApiException e) {
                    if (e.getCode() == ErrorCode.ENT_GET_INVALID_BUNDLE_ID.getCode()) {
                        return Response.status(Status.NO_CONTENT).build();
                    } else {
                        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                    }
                }
            }
        };
        SubscriptionCallCompletion<Response> callCompletionCreation = new SubscriptionCallCompletion<Response>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, createdBy, reason, comment);
    }

    @PUT
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/uncancel")
    @Produces(APPLICATION_JSON)
    public Response uncancelSubscriptionPlan(@PathParam("subscriptionId") final String subscriptionId,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {
        try {
            UUID uuid = UUID.fromString(subscriptionId);
            Subscription current = entitlementApi.getSubscriptionFromId(uuid);
        
            current.uncancel(context.createContext(createdBy, reason, comment));
            return Response.status(Status.OK).build();
        } catch (EntitlementUserApiException e) {
            if(e.getCode() == ErrorCode.ENT_INVALID_SUBSCRIPTION_ID.getCode()) {
                return Response.status(Status.NO_CONTENT).build();
            } else {
                log.info(String.format("Failed to uncancel plan for subscription %s", subscriptionId), e);
                return Response.status(Status.BAD_REQUEST).build();
            }
        }
    }

    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response cancelSubscriptionPlan(final @PathParam("subscriptionId") String subscriptionId,
            @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
            @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
            @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {

        SubscriptionCallCompletionCallback<Response> callback = new SubscriptionCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(CallContext ctx)
                    throws EntitlementUserApiException, InterruptedException,
                    TimeoutException {
                try {
                    UUID uuid = UUID.fromString(subscriptionId);

                    Subscription current = entitlementApi.getSubscriptionFromId(uuid);

                    DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
                    isImmediateOp = current.cancel(inputDate, false, ctx);
                    return Response.status(Status.OK).build();
                } catch (EntitlementUserApiException e) {
                    if(e.getCode() == ErrorCode.ENT_INVALID_SUBSCRIPTION_ID.getCode()) {
                        return Response.status(Status.NO_CONTENT).build();
                    } else {
                        throw e;
                    }
                }
            }
            @Override
            public boolean isImmOperation() {
                return isImmediateOp;
            }
            @Override
            public Response doResponseOk(Response operationResponse) {
                return operationResponse;
            }
        };
        SubscriptionCallCompletion<Response> callCompletionCreation = new SubscriptionCallCompletion<Response>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, createdBy, reason, comment);
    }

    private final static class CompletionUserRequestSubscription extends CompletionUserRequestBase {

        public CompletionUserRequestSubscription(final UUID userToken) {
            super(userToken);
        }
        @Override
        public void onSubscriptionTransition(SubscriptionEvent curEvent) {
            log.debug(String.format("Got event SubscriptionTransition token = %s, type = %s, remaining = %d ", 
                    curEvent.getUserToken(), curEvent.getTransitionType(),  curEvent.getRemainingEventsForUserOperation())); 
        }
        @Override
        public void onEmptyInvoice(final EmptyInvoiceEvent curEvent) {
            log.debug(String.format("Got event EmptyInvoiceNotification token = %s ", curEvent.getUserToken())); 
            notifyForCompletion();
        }
        @Override
        public void onInvoiceCreation(InvoiceCreationEvent curEvent) {
            log.debug(String.format("Got event InvoiceCreationNotification token = %s ", curEvent.getUserToken())); 
            if (curEvent.getAmountOwed().compareTo(BigDecimal.ZERO) <= 0) {
                notifyForCompletion();
            }
        }
        @Override
        public void onPaymentInfo(PaymentInfoEvent curEvent) {
            log.debug(String.format("Got event PaymentInfo token = %s ", curEvent.getUserToken()));  
            notifyForCompletion();
        }
        @Override
        public void onPaymentError(PaymentErrorEvent curEvent) {
            log.debug(String.format("Got event PaymentError token = %s ", curEvent.getUserToken())); 
            notifyForCompletion();
        }
    }

    private interface SubscriptionCallCompletionCallback<T> {
        public T doOperation(final CallContext ctx) throws EntitlementUserApiException, InterruptedException, TimeoutException;
        public boolean isImmOperation();
        public Response doResponseOk(final T operationResponse);
    }

    private class SubscriptionCallCompletion<T> {

        public Response withSynchronization(final SubscriptionCallCompletionCallback<T> callback,
                final long timeoutSec,
                final boolean callCompletion,
                final String createdBy,
                final String reason,
                final String comment) {

            CallContext ctx = context.createContext(createdBy, reason, comment);
            CompletionUserRequestSubscription waiter = callCompletion ? new CompletionUserRequestSubscription(ctx.getUserToken()) : null; 
            try {
                if (waiter != null) {
                    killbillHandler.registerCompletionUserRequestWaiter(waiter);    
                }
                T operationValue = callback.doOperation(ctx);
                if (waiter != null && callback.isImmOperation()) {
                    waiter.waitForCompletion(timeoutSec * 1000);
                }
                return callback.doResponseOk(operationValue);
            } catch (EntitlementUserApiException e) {
                log.info(String.format("Failed to complete operation"), e);
                return Response.status(Status.BAD_REQUEST).build();
            } catch (InterruptedException e) {
                return Response.status(Status.INTERNAL_SERVER_ERROR).build();
            } catch (TimeoutException e) {
                return Response.status(Status.fromStatusCode(408)).build();   
            } finally {
                if (waiter != null) {
                    killbillHandler.unregisterCompletionUserRequestWaiter(waiter);              
                }
            }
        }
    }

    @GET
    @Path(CUSTOM_FIELD_URI)
    @Produces(APPLICATION_JSON)
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id) {
        return super.getCustomFields(UUID.fromString(id));
    }

    @POST
    @Path(CUSTOM_FIELD_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
            final List<CustomFieldJson> customFields,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {
        return super.createCustomFields(UUID.fromString(id), customFields,
                context.createContext(createdBy, reason, comment));
    }

    @DELETE
    @Path(CUSTOM_FIELD_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
            @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                context.createContext(createdBy, reason, comment));
    }

    @GET
    @Path(TAG_URI)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) String id) {
        return super.getTags(UUID.fromString(id));
    }

    @POST
    @Path(TAG_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createTags(@PathParam(ID_PARAM_NAME) final String id,
            @QueryParam(QUERY_TAGS) final String tagList,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {
        return super.createTags(UUID.fromString(id), tagList,
                                context.createContext(createdBy, reason, comment));
    }

    @DELETE
    @Path(TAG_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteTags(@PathParam(ID_PARAM_NAME) final String id,
            @QueryParam(QUERY_TAGS) final String tagList,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {

        return super.deleteTags(UUID.fromString(id), tagList,
                                context.createContext(createdBy, reason, comment));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.SUBSCRIPTION;
    }
}
