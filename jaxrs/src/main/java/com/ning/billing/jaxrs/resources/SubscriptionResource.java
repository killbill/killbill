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
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import javax.swing.text.html.HTMLDocument.HTMLReader.IsindexAction;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.invoice.api.EmptyInvoiceNotification;
import com.ning.billing.invoice.api.InvoiceCreationNotification;
import com.ning.billing.jaxrs.json.SubscriptionJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.jaxrs.util.KillbillEventHandler;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentInfo;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.userrequest.CompletionUserRequestBase;

@Path(BaseJaxrsResource.SUBSCRIPTIONS_PATH)
public class SubscriptionResource implements BaseJaxrsResource{

    private static final Logger log = LoggerFactory.getLogger(SubscriptionResource.class);

    private final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTime();

    private final EntitlementUserApi entitlementApi;
    private final Context context;
    private final JaxrsUriBuilder uriBuilder;	
    private final KillbillEventHandler killbillHandler;

    @Inject
    public SubscriptionResource(final JaxrsUriBuilder uriBuilder, final EntitlementUserApi entitlementApi,
            final Clock clock, final Context context, final KillbillEventHandler killbillHandler) {
        this.uriBuilder = uriBuilder;
        this.entitlementApi = entitlementApi;
        this.context = context;
        this.killbillHandler = killbillHandler;
    }

    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getSubscription(@PathParam("subscriptionId") final String subscriptionId) {


        UUID uuid = UUID.fromString(subscriptionId);
        Subscription subscription = entitlementApi.getSubscriptionFromId(uuid);
        if (subscription == null) {
            return Response.status(Status.NO_CONTENT).build();
        }
        SubscriptionJson json = new SubscriptionJson(subscription, null, null, null);
        return Response.status(Status.OK).entity(json).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createSubscription(final SubscriptionJson subscription,
            final @QueryParam(QUERY_REQUESTED_DT) String requestedDate,
            final @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") Boolean callCompletion,
            final @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") long timeoutSec) {


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
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion);
    }

    @PUT
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    public Response changeSubscriptionPlan(final SubscriptionJson subscription,
            final @PathParam("subscriptionId") String subscriptionId,
            final @QueryParam(QUERY_REQUESTED_DT) String requestedDate,
            final @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") Boolean callCompletion,
            final @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") long timeoutSec) {
        
        SubscriptionCallCompletionCallback<Response> callback = new SubscriptionCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;
            
            @Override
            public Response doOperation(CallContext ctx)
                    throws EntitlementUserApiException, InterruptedException,
                    TimeoutException {
                UUID uuid = UUID.fromString(subscriptionId);
                Subscription current = entitlementApi.getSubscriptionFromId(uuid);
                if (current == null) {
                    return Response.status(Status.NO_CONTENT).build();
                }
                DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
                isImmediateOp = current.changePlan(subscription.getProductName(),  BillingPeriod.valueOf(subscription.getBillingPeriod()), subscription.getPriceList(), inputDate, ctx);
                return Response.status(Status.OK).build();
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
                return getSubscription(subscriptionId);
            }
        };
        SubscriptionCallCompletion<Response> callCompletionCreation = new SubscriptionCallCompletion<Response>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion);
    }
    
    @PUT
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/uncancel")
    @Produces(APPLICATION_JSON)
    public Response uncancelSubscriptionPlan(@PathParam("subscriptionId") String subscriptionId) {
        try {
            UUID uuid = UUID.fromString(subscriptionId);
            Subscription current = entitlementApi.getSubscriptionFromId(uuid);
            if (current == null) {
                return Response.status(Status.NO_CONTENT).build();
            }
            current.uncancel(context.createContext());
            return Response.status(Status.OK).build();
        } catch (EntitlementUserApiException e) {
            log.info(String.format("Failed to uncancel plan for subscription %s", subscriptionId), e);
            return Response.status(Status.BAD_REQUEST).build();
        }
    }

    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response cancelSubscriptionPlan(final @PathParam("subscriptionId") String subscriptionId,
            final @QueryParam(QUERY_REQUESTED_DT) String requestedDate,
            final @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") Boolean callCompletion,
            final @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") long timeoutSec) {
        
        SubscriptionCallCompletionCallback<Response> callback = new SubscriptionCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;
            
            @Override
            public Response doOperation(CallContext ctx)
                    throws EntitlementUserApiException, InterruptedException,
                    TimeoutException {
                UUID uuid = UUID.fromString(subscriptionId);
                Subscription current = entitlementApi.getSubscriptionFromId(uuid);
                if (current == null) {
                    return Response.status(Status.NO_CONTENT).build();
                }
                DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
                isImmediateOp = current.cancel(inputDate, false, ctx);
                return Response.status(Status.OK).build();
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
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion);
    }
    
    private final static class CompletionUserRequestSubscription extends CompletionUserRequestBase {

        public CompletionUserRequestSubscription(final UUID userToken) {
            super(userToken);
        }
        @Override
        public void onSubscriptionTransition(SubscriptionTransition curEvent) {
            log.info(String.format("Got event SubscriptionTransition token = %s, type = %s, remaining = %d ", 
                    curEvent.getUserToken(), curEvent.getTransitionType(),  curEvent.getRemainingEventsForUserOperation())); 
        }
        @Override
        public void onEmptyInvoice(final EmptyInvoiceNotification curEvent) {
            log.info(String.format("Got event EmptyInvoiceNotification token = %s ", curEvent.getUserToken())); 
            notifyForCompletion();
        }
        @Override
        public void onInvoiceCreation(InvoiceCreationNotification curEvent) {
            log.info(String.format("Got event InvoiceCreationNotification token = %s ", curEvent.getUserToken())); 
            if (curEvent.getAmountOwed().compareTo(BigDecimal.ZERO) <= 0) {
                notifyForCompletion();
            }
        }
        @Override
        public void onPaymentInfo(PaymentInfo curEvent) {
            log.info(String.format("Got event PaymentInfo token = %s ", curEvent.getUserToken()));  
            notifyForCompletion();
        }
        @Override
        public void onPaymentError(PaymentError curEvent) {
            log.info(String.format("Got event PaymentError token = %s ", curEvent.getUserToken())); 
            notifyForCompletion();
        }
    }
    
    private interface SubscriptionCallCompletionCallback<T> {
        public T doOperation(final CallContext ctx) throws EntitlementUserApiException, InterruptedException, TimeoutException;
        public boolean isImmOperation();
        public Response doResponseOk(final T operationResponse);
    }

    private class SubscriptionCallCompletion<T> {
        
        public Response withSynchronization(final SubscriptionCallCompletionCallback<T> callback, final long timeoutSec, final boolean callCompletion) {

            CallContext ctx = context.createContext();
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
}
