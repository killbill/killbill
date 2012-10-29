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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import javax.servlet.http.HttpServletRequest;
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
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ObjectType;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.jaxrs.util.KillbillEventHandler;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.InvoiceCreationInternalEvent;
import com.ning.billing.util.events.NullInvoiceInternalEvent;
import com.ning.billing.util.events.PaymentErrorInternalEvent;
import com.ning.billing.util.events.PaymentInfoInternalEvent;
import com.ning.billing.util.userrequest.CompletionUserRequestBase;

import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.SUBSCRIPTIONS_PATH)
public class SubscriptionResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionResource.class);
    private static final String ID_PARAM_NAME = "subscriptionId";
    private static final String CUSTOM_FIELD_URI = JaxrsResource.CUSTOM_FIELDS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";
    private static final String TAG_URI = JaxrsResource.TAGS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";

    private final EntitlementUserApi entitlementApi;
    private final KillbillEventHandler killbillHandler;

    @Inject
    public SubscriptionResource(final EntitlementUserApi entitlementApi,
                                final KillbillEventHandler killbillHandler,
                                final JaxrsUriBuilder uriBuilder,
                                final TagUserApi tagUserApi,
                                final CustomFieldUserApi customFieldUserApi,
                                final AuditUserApi auditUserApi,
                                final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, context);
        this.entitlementApi = entitlementApi;
        this.killbillHandler = killbillHandler;
    }

    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getSubscription(@PathParam("subscriptionId") final String subscriptionId,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementUserApiException {
        final UUID uuid = UUID.fromString(subscriptionId);
        final Subscription subscription = entitlementApi.getSubscriptionFromId(uuid, context.createContext(request));
        final SubscriptionJsonNoEvents json = new SubscriptionJsonNoEvents(subscription, null);
        return Response.status(Status.OK).entity(json).build();
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
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementUserApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final SubscriptionCallCompletionCallback<Subscription> callback = new SubscriptionCallCompletionCallback<Subscription>() {
            @Override
            public Subscription doOperation(final CallContext ctx) throws EntitlementUserApiException, InterruptedException, TimeoutException {

                final DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
                final UUID uuid = UUID.fromString(subscription.getBundleId());

                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(subscription.getProductName(),
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

        final SubscriptionCallCompletion<Subscription> callCompletionCreation = new SubscriptionCallCompletion<Subscription>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
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
                                           @QueryParam(QUERY_POLICY) final String policyString,
                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                           @HeaderParam(HDR_REASON) final String reason,
                                           @HeaderParam(HDR_COMMENT) final String comment,
                                           @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementUserApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final SubscriptionCallCompletionCallback<Response> callback = new SubscriptionCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(final CallContext ctx) throws EntitlementUserApiException, InterruptedException,
                                                                      TimeoutException {
                final UUID uuid = UUID.fromString(subscriptionId);
                final Subscription current = entitlementApi.getSubscriptionFromId(uuid, callContext);
                final DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;

                if (policyString == null) {
                    isImmediateOp = current.changePlan(subscription.getProductName(), BillingPeriod.valueOf(subscription.getBillingPeriod()),
                                                       subscription.getPriceList(), inputDate, ctx);
                } else {
                    final ActionPolicy policy = ActionPolicy.valueOf(policyString.toUpperCase());
                    isImmediateOp = current.changePlanWithPolicy(subscription.getProductName(), BillingPeriod.valueOf(subscription.getBillingPeriod()),
                                                                 subscription.getPriceList(), inputDate, policy, ctx);
                }

                return Response.status(Status.OK).build();
            }

            @Override
            public boolean isImmOperation() {
                return isImmediateOp;
            }

            @Override
            public Response doResponseOk(final Response operationResponse) throws EntitlementUserApiException {
                if (operationResponse.getStatus() != Status.OK.getStatusCode()) {
                    return operationResponse;
                }

                return getSubscription(subscriptionId, request);
            }
        };

        final SubscriptionCallCompletion<Response> callCompletionCreation = new SubscriptionCallCompletion<Response>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    @PUT
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/uncancel")
    @Produces(APPLICATION_JSON)
    public Response uncancelSubscriptionPlan(@PathParam("subscriptionId") final String subscriptionId,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementUserApiException {
        final UUID uuid = UUID.fromString(subscriptionId);
        final Subscription current = entitlementApi.getSubscriptionFromId(uuid, context.createContext(createdBy, reason, comment, request));

        current.uncancel(context.createContext(createdBy, reason, comment, request));
        return Response.status(Status.OK).build();
    }

    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response cancelSubscriptionPlan(@PathParam("subscriptionId") final String subscriptionId,
                                           @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                           @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                           @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("5") final long timeoutSec,
                                           @QueryParam(QUERY_POLICY) final String policyString,
                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                           @HeaderParam(HDR_REASON) final String reason,
                                           @HeaderParam(HDR_COMMENT) final String comment,
                                           @javax.ws.rs.core.Context final UriInfo uriInfo,
                                           @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementUserApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final SubscriptionCallCompletionCallback<Response> callback = new SubscriptionCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(final CallContext ctx)
                    throws EntitlementUserApiException, InterruptedException,
                           TimeoutException {
                final UUID uuid = UUID.fromString(subscriptionId);

                final Subscription current = entitlementApi.getSubscriptionFromId(uuid, callContext);

                final DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
                if (policyString == null) {
                    isImmediateOp = current.cancel(inputDate, ctx);
                } else {
                    final ActionPolicy policy = ActionPolicy.valueOf(policyString.toUpperCase());
                    isImmediateOp = current.cancelWithPolicy(inputDate, policy, ctx);
                }
                return Response.status(Status.OK).build();
            }

            @Override
            public boolean isImmOperation() {
                return isImmediateOp;
            }

            @Override
            public Response doResponseOk(final Response operationResponse) {
                return operationResponse;
            }
        };

        final SubscriptionCallCompletion<Response> callCompletionCreation = new SubscriptionCallCompletion<Response>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    private static final class CompletionUserRequestSubscription extends CompletionUserRequestBase {

        public CompletionUserRequestSubscription(final UUID userToken) {
            super(userToken);
        }

        @Override
        public void onSubscriptionTransition(final EffectiveSubscriptionInternalEvent curEventEffective) {
            log.info(String.format("Got event SubscriptionTransition token = %s, type = %s, remaining = %d ",
                                   curEventEffective.getUserToken(), curEventEffective.getTransitionType(), curEventEffective.getRemainingEventsForUserOperation()));
        }

        @Override
        public void onEmptyInvoice(final NullInvoiceInternalEvent curEvent) {
            log.info(String.format("Got event EmptyInvoiceNotification token = %s ", curEvent.getUserToken()));
            notifyForCompletion();
        }

        @Override
        public void onInvoiceCreation(final InvoiceCreationInternalEvent curEvent) {
            log.info(String.format("Got event InvoiceCreationNotification token = %s ", curEvent.getUserToken()));
            if (curEvent.getAmountOwed().compareTo(BigDecimal.ZERO) <= 0) {
                notifyForCompletion();
            }
        }

        @Override
        public void onPaymentInfo(final PaymentInfoInternalEvent curEvent) {
            log.info(String.format("Got event PaymentInfo token = %s ", curEvent.getUserToken()));
            notifyForCompletion();
        }

        @Override
        public void onPaymentError(final PaymentErrorInternalEvent curEvent) {
            log.info(String.format("Got event PaymentError token = %s ", curEvent.getUserToken()));
            notifyForCompletion();
        }
    }

    private interface SubscriptionCallCompletionCallback<T> {

        public T doOperation(final CallContext ctx) throws EntitlementUserApiException, InterruptedException, TimeoutException;

        public boolean isImmOperation();

        public Response doResponseOk(final T operationResponse) throws EntitlementUserApiException;
    }

    private class SubscriptionCallCompletion<T> {

        public Response withSynchronization(final SubscriptionCallCompletionCallback<T> callback,
                                            final long timeoutSec,
                                            final boolean callCompletion,
                                            final CallContext callContext) throws EntitlementUserApiException {
            final CompletionUserRequestSubscription waiter = callCompletion ? new CompletionUserRequestSubscription(callContext.getUserToken()) : null;
            try {
                if (waiter != null) {
                    killbillHandler.registerCompletionUserRequestWaiter(waiter);
                }
                final T operationValue = callback.doOperation(callContext);
                if (waiter != null && callback.isImmOperation()) {
                    waiter.waitForCompletion(timeoutSec * 1000);
                }
                return callback.doResponseOk(operationValue);
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
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), context.createContext(request));
    }

    @POST
    @Path(CUSTOM_FIELD_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       final List<CustomFieldJson> customFields,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.createCustomFields(UUID.fromString(id), customFields,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @DELETE
    @Path(CUSTOM_FIELD_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @GET
    @Path(TAG_URI)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) final String id,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("false") final Boolean withAudit,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        return super.getTags(UUID.fromString(id), withAudit, context.createContext(request));
    }

    @POST
    @Path(TAG_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final UriInfo uriInfo,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(UUID.fromString(id), tagList, uriInfo,
                                context.createContext(createdBy, reason, comment, request));
    }

    @DELETE
    @Path(TAG_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(UUID.fromString(id), tagList,
                                context.createContext(createdBy, reason, comment, request));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.SUBSCRIPTION;
    }
}
