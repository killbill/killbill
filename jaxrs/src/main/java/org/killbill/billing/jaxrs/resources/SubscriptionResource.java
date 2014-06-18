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

package org.killbill.billing.jaxrs.resources;

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

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.clock.Clock;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.NullInvoiceInternalEvent;
import org.killbill.billing.events.PaymentErrorInternalEvent;
import org.killbill.billing.events.PaymentInfoInternalEvent;
import org.killbill.billing.events.PaymentPluginErrorInternalEvent;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.SubscriptionJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.jaxrs.util.KillbillEventHandler;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.userrequest.CompletionUserRequestBase;

import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.SUBSCRIPTIONS_PATH)
public class SubscriptionResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionResource.class);
    private static final String ID_PARAM_NAME = "subscriptionId";

    private final KillbillEventHandler killbillHandler;
    private final EntitlementApi entitlementApi;
    private final SubscriptionApi subscriptionApi;

    @Inject
    public SubscriptionResource(final KillbillEventHandler killbillHandler,
                                final JaxrsUriBuilder uriBuilder,
                                final TagUserApi tagUserApi,
                                final CustomFieldUserApi customFieldUserApi,
                                final AuditUserApi auditUserApi,
                                final EntitlementApi entitlementApi,
                                final SubscriptionApi subscriptionApi,
                                final AccountUserApi accountUserApi,
                                final Clock clock,
                                final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.killbillHandler = killbillHandler;
        this.entitlementApi = entitlementApi;
        this.subscriptionApi = subscriptionApi;
    }

    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getEntitlement(@PathParam("subscriptionId") final String subscriptionId,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        final UUID uuid = UUID.fromString(subscriptionId);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(uuid, context.createContext(request));
        final SubscriptionJson json = new SubscriptionJson(subscription, null, null);
        return Response.status(Status.OK).entity(json).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createEntitlement(final SubscriptionJson entitlement,
                                      @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                      @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                      @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final HttpServletRequest request,
                                      @javax.ws.rs.core.Context final UriInfo uriInfo) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final EntitlementCallCompletionCallback<Entitlement> callback = new EntitlementCallCompletionCallback<Entitlement>() {
            @Override
            public Entitlement doOperation(final CallContext ctx) throws InterruptedException, TimeoutException, EntitlementApiException {

                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(entitlement.getProductName(),
                                                                       ProductCategory.valueOf(entitlement.getProductCategory()),
                                                                       BillingPeriod.valueOf(entitlement.getBillingPeriod()), entitlement.getPriceList(), null);

                final UUID accountId = entitlement.getAccountId() != null ? UUID.fromString(entitlement.getAccountId()) : null;
                final LocalDate inputLocalDate = toLocalDate(accountId, requestedDate, callContext);
                final UUID bundleId = entitlement.getBundleId() != null ? UUID.fromString(entitlement.getBundleId()) : null;
                return (entitlement.getProductCategory().equals(ProductCategory.ADD_ON.toString())) ?
                       entitlementApi.addEntitlement(bundleId, spec, inputLocalDate, callContext) :
                       entitlementApi.createBaseEntitlement(accountId, spec, entitlement.getExternalKey(), inputLocalDate, callContext);
            }

            @Override
            public boolean isImmOperation() {
                return true;
            }

            @Override
            public Response doResponseOk(final Entitlement createdEntitlement) {
                return uriBuilder.buildResponse(uriInfo, SubscriptionResource.class, "getEntitlement", createdEntitlement.getId());
            }
        };

        final EntitlementCallCompletion<Entitlement> callCompletionCreation = new EntitlementCallCompletion<Entitlement>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    @PUT
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/uncancel")
    @Produces(APPLICATION_JSON)
    public Response uncancelEntitlementPlan(@PathParam("subscriptionId") final String subscriptionId,
                                            @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                            @HeaderParam(HDR_REASON) final String reason,
                                            @HeaderParam(HDR_COMMENT) final String comment,
                                            @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException {
        final UUID uuid = UUID.fromString(subscriptionId);
        final Entitlement current = entitlementApi.getEntitlementForId(uuid, context.createContext(createdBy, reason, comment, request));
        current.uncancelEntitlement(context.createContext(createdBy, reason, comment, request));
        return Response.status(Status.OK).build();
    }

    @PUT
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    public Response changeEntitlementPlan(final SubscriptionJson entitlement,
                                          @PathParam("subscriptionId") final String subscriptionId,
                                          @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                          @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                          @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                          @QueryParam(QUERY_BILLING_POLICY) final String policyString,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final EntitlementCallCompletionCallback<Response> callback = new EntitlementCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(final CallContext ctx) throws EntitlementApiException, InterruptedException,
                                                                      TimeoutException, AccountApiException {
                final UUID uuid = UUID.fromString(subscriptionId);

                final Entitlement current = entitlementApi.getEntitlementForId(uuid, callContext);
                final LocalDate inputLocalDate = toLocalDate(current.getAccountId(), requestedDate, callContext);
                final Entitlement newEntitlement;
                if (requestedDate == null && policyString == null) {
                    newEntitlement = current.changePlan(entitlement.getProductName(), BillingPeriod.valueOf(entitlement.getBillingPeriod()), entitlement.getPriceList(), ctx);
                } else if (policyString == null) {
                    newEntitlement = current.changePlanWithDate(entitlement.getProductName(), BillingPeriod.valueOf(entitlement.getBillingPeriod()), entitlement.getPriceList(), inputLocalDate, ctx);
                } else {
                    final BillingActionPolicy policy = BillingActionPolicy.valueOf(policyString.toUpperCase());
                    newEntitlement = current.changePlanOverrideBillingPolicy(entitlement.getProductName(), BillingPeriod.valueOf(entitlement.getBillingPeriod()), entitlement.getPriceList(), inputLocalDate, policy, ctx);
                }
                isImmediateOp = newEntitlement.getLastActiveProduct().getName().equals(entitlement.getProductName()) &&
                                newEntitlement.getLastActivePlan().getRecurringBillingPeriod() == BillingPeriod.valueOf(entitlement.getBillingPeriod()) &&
                                newEntitlement.getLastActivePriceList().getName().equals(entitlement.getPriceList());
                return Response.status(Status.OK).build();
            }

            @Override
            public boolean isImmOperation() {
                return isImmediateOp;
            }

            @Override
            public Response doResponseOk(final Response operationResponse) throws SubscriptionApiException {
                if (operationResponse.getStatus() != Status.OK.getStatusCode()) {
                    return operationResponse;
                }
                return getEntitlement(subscriptionId, request);
            }
        };

        final EntitlementCallCompletion<Response> callCompletionCreation = new EntitlementCallCompletion<Response>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response cancelEntitlementPlan(@PathParam("subscriptionId") final String subscriptionId,
                                          @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                          @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                          @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("5") final long timeoutSec,
                                          @QueryParam(QUERY_ENTITLEMENT_POLICY) final String entitlementPolicyString,
                                          @QueryParam(QUERY_BILLING_POLICY) final String billingPolicyString,
                                          @QueryParam(QUERY_USE_REQUESTED_DATE_FOR_BILLING) @DefaultValue("false") final Boolean useRequestedDateForBilling,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final EntitlementCallCompletionCallback<Response> callback = new EntitlementCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(final CallContext ctx)
                    throws EntitlementApiException, InterruptedException,
                           TimeoutException, AccountApiException, SubscriptionApiException {
                final UUID uuid = UUID.fromString(subscriptionId);

                final Entitlement current = entitlementApi.getEntitlementForId(uuid, ctx);

                final LocalDate inputLocalDate = toLocalDate(current.getAccountId(), requestedDate, callContext);
                final Entitlement newEntitlement;
                if (billingPolicyString == null && entitlementPolicyString == null) {
                    newEntitlement = current.cancelEntitlementWithDate(inputLocalDate, useRequestedDateForBilling, ctx);
                } else if (billingPolicyString == null && entitlementPolicyString != null) {
                    final EntitlementActionPolicy entitlementPolicy = EntitlementActionPolicy.valueOf(entitlementPolicyString);
                    newEntitlement = current.cancelEntitlementWithPolicy(entitlementPolicy, ctx);
                } else if (billingPolicyString != null && entitlementPolicyString == null) {
                    final BillingActionPolicy billingPolicy = BillingActionPolicy.valueOf(billingPolicyString.toUpperCase());
                    newEntitlement = current.cancelEntitlementWithDateOverrideBillingPolicy(inputLocalDate, billingPolicy, ctx);
                } else {
                    final EntitlementActionPolicy entitlementPolicy = EntitlementActionPolicy.valueOf(entitlementPolicyString);
                    final BillingActionPolicy billingPolicy = BillingActionPolicy.valueOf(billingPolicyString.toUpperCase());
                    newEntitlement = current.cancelEntitlementWithPolicyOverrideBillingPolicy(entitlementPolicy, billingPolicy, ctx);
                }

                final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(newEntitlement.getId(), ctx);

                final LocalDate nowInAccountTimeZone = new LocalDate(clock.getUTCNow(), subscription.getBillingEndDate().getChronology().getZone());
                isImmediateOp = subscription.getBillingEndDate() != null &&
                                !subscription.getBillingEndDate().isAfter(nowInAccountTimeZone);
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

        final EntitlementCallCompletion<Response> callCompletionCreation = new EntitlementCallCompletion<Response>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    private static final class CompletionUserRequestEntitlement extends CompletionUserRequestBase {

        public CompletionUserRequestEntitlement(final UUID userToken) {
            super(userToken);
        }

        @Override
        public void onSubscriptionBaseTransition(final EffectiveSubscriptionInternalEvent event) {

            log.info(String.format("Got event SubscriptionBaseTransition token = %s, type = %s, remaining = %d ",
                                   event.getUserToken(), event.getTransitionType(), event.getRemainingEventsForUserOperation()));
        }

        @Override
        public void onEmptyInvoice(final NullInvoiceInternalEvent event) {
            log.info(String.format("Got event EmptyInvoiceNotification token = %s ", event.getUserToken()));
            notifyForCompletion();
        }

        @Override
        public void onInvoiceCreation(final InvoiceCreationInternalEvent event) {

            log.info(String.format("Got event InvoiceCreationNotification token = %s ", event.getUserToken()));
            if (event.getAmountOwed().compareTo(BigDecimal.ZERO) <= 0) {
                notifyForCompletion();
            }
        }

        @Override
        public void onPaymentInfo(final PaymentInfoInternalEvent event) {
            log.info(String.format("Got event PaymentInfo token = %s ", event.getUserToken()));
            notifyForCompletion();
        }

        @Override
        public void onPaymentError(final PaymentErrorInternalEvent event) {
            log.info(String.format("Got event PaymentError token = %s ", event.getUserToken()));
            notifyForCompletion();
        }

        @Override
        public void onPaymentPluginError(final PaymentPluginErrorInternalEvent event) {
            log.info(String.format("Got event PaymentPluginError token = %s ", event.getUserToken()));
            notifyForCompletion();
        }
    }

    private interface EntitlementCallCompletionCallback<T> {

        public T doOperation(final CallContext ctx) throws EntitlementApiException, InterruptedException, TimeoutException, AccountApiException, SubscriptionApiException;

        public boolean isImmOperation();

        public Response doResponseOk(final T operationResponse) throws SubscriptionApiException;
    }

    private class EntitlementCallCompletion<T> {

        public Response withSynchronization(final EntitlementCallCompletionCallback<T> callback,
                                            final long timeoutSec,
                                            final boolean callCompletion,
                                            final CallContext callContext) throws SubscriptionApiException, AccountApiException, EntitlementApiException {
            final CompletionUserRequestEntitlement waiter = callCompletion ? new CompletionUserRequestEntitlement(callContext.getUserToken()) : null;
            try {
                if (waiter != null) {
                    killbillHandler.registerCompletionUserRequestWaiter(waiter);
                }
                final T operationValue = callback.doOperation(callContext);
                if (waiter != null && callback.isImmOperation()) {
                    waiter.waitForCompletion(timeoutSec * 1000);
                }
                return callback.doResponseOk(operationValue);
            } catch (final InterruptedException e) {
                return Response.status(Status.INTERNAL_SERVER_ERROR).build();
            } catch (final TimeoutException e) {
                return Response.status(Status.fromStatusCode(408)).build();
            } finally {
                if (waiter != null) {
                    killbillHandler.unregisterCompletionUserRequestWaiter(waiter);
                }
            }
        }
    }

    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), auditMode, context.createContext(request));
    }

    @POST
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       final List<CustomFieldJson> customFields,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(UUID.fromString(id), customFields,
                                        context.createContext(createdBy, reason, comment, request), uriInfo);
    }

    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) final String subscriptionIdString,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, SubscriptionApiException {
        final UUID subscriptionId = UUID.fromString(subscriptionIdString);
        final TenantContext tenantContext = context.createContext(request);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(subscriptionId, tenantContext);
        return super.getTags(subscription.getAccountId(), subscriptionId, auditMode, includedDeleted, tenantContext);
    }

    @POST
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + TAGS)
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
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + TAGS)
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
