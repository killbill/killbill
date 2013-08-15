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
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.BillingActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.Entitlement;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.entitlement.api.EntitlementApi;
import com.ning.billing.entitlement.api.EntitlementApiException;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.EntitlementJsonNoEvents;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.jaxrs.util.KillbillEventHandler;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldApiException;
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

@Path(JaxrsResource.ENTITLEMENTS_PATH)
public class EntitlementResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(EntitlementResource.class);
    private static final String ID_PARAM_NAME = "entitlementId";
    private static final String CUSTOM_FIELD_URI = JaxrsResource.CUSTOM_FIELDS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";
    private static final String TAG_URI = JaxrsResource.TAGS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";

    private final KillbillEventHandler killbillHandler;
    private final EntitlementApi entitlementApi;

    @Inject
    public EntitlementResource(final KillbillEventHandler killbillHandler,
                               final JaxrsUriBuilder uriBuilder,
                               final TagUserApi tagUserApi,
                               final CustomFieldUserApi customFieldUserApi,
                               final AuditUserApi auditUserApi,
                               final EntitlementApi entitlementApi,
                               final AccountUserApi accountUserApi,
                               final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, context);
        this.killbillHandler = killbillHandler;
        this.entitlementApi = entitlementApi;
    }

    @GET
    @Path("/{entitlementId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getEntitlement(@PathParam("entitlementId") final String entitlementId,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException {
        final UUID uuid = UUID.fromString(entitlementId);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(uuid, context.createContext(request));
        final EntitlementJsonNoEvents json = new EntitlementJsonNoEvents(entitlement, null);
        return Response.status(Status.OK).entity(json).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createEntitlement(final EntitlementJsonNoEvents entitlement,
                                      @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                      @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                      @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
        final EntitlementCallCompletionCallback<Entitlement> callback = new EntitlementCallCompletionCallback<Entitlement>() {
            @Override
            public Entitlement doOperation(final CallContext ctx) throws InterruptedException, TimeoutException, EntitlementApiException {

                //final DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
                final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(entitlement.getProductName(),
                                                                       ProductCategory.valueOf(entitlement.getProductCategory()),
                                                                       BillingPeriod.valueOf(entitlement.getBillingPeriod()), entitlement.getPriceList(), null);


                final UUID accountId = entitlement.getAccountId() != null ? UUID.fromString(entitlement.getAccountId()) : null;
                final LocalDate inputLocalDate = toLocalDate(accountId, inputDate, callContext);
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
                return uriBuilder.buildResponse(EntitlementResource.class, "getEntitlement", createdEntitlement.getId());
            }
        };

        final EntitlementCallCompletion<Entitlement> callCompletionCreation = new EntitlementCallCompletion<Entitlement>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    @PUT
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{entitlementId:" + UUID_PATTERN + "}")
    public Response changeEntitlementPlan(final EntitlementJsonNoEvents entitlement,
                                          @PathParam("entitlementId") final String entitlementId,
                                          @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                          @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                          @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                          @QueryParam(QUERY_POLICY) final String policyString,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final EntitlementCallCompletionCallback<Response> callback = new EntitlementCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(final CallContext ctx) throws EntitlementApiException, InterruptedException,
                                                                      TimeoutException, AccountApiException {
                final UUID uuid = UUID.fromString(entitlementId);
                final DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;


                final Entitlement current = entitlementApi.getEntitlementForId(uuid, callContext);
                final LocalDate inputLocalDate = toLocalDate(current.getAccountId(), inputDate, callContext);
                final Entitlement newEntitlement;
                if (policyString == null) {
                    newEntitlement = current.changePlan(entitlement.getProductName(), BillingPeriod.valueOf(entitlement.getBillingPeriod()), entitlement.getPriceList(), inputLocalDate, ctx);
                } else {
                    final BillingActionPolicy policy = BillingActionPolicy.valueOf(policyString.toUpperCase());
                    newEntitlement  = current.changePlanOverrideBillingPolicy(entitlement.getProductName(), BillingPeriod.valueOf(entitlement.getBillingPeriod()), entitlement.getPriceList(), inputLocalDate, policy, ctx);
                }
                isImmediateOp = newEntitlement.getProduct().getName().equals(entitlement.getProductName()) &&
                                newEntitlement.getPlan().getBillingPeriod() == BillingPeriod.valueOf(entitlement.getBillingPeriod()) &&
                                newEntitlement.getPriceList().getName().equals(entitlement.getPriceList());
                return Response.status(Status.OK).build();
            }

            @Override
            public boolean isImmOperation() {
                return isImmediateOp;
            }

            @Override
            public Response doResponseOk(final Response operationResponse) throws EntitlementApiException {
                if (operationResponse.getStatus() != Status.OK.getStatusCode()) {
                    return operationResponse;
                }
                return getEntitlement(entitlementId, request);
            }
        };

        final EntitlementCallCompletion<Response> callCompletionCreation = new EntitlementCallCompletion<Response>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    @PUT
    @Path("/{entitlementId:" + UUID_PATTERN + "}/uncancel")
    @Produces(APPLICATION_JSON)
    public Response uncancelEntitlementPlan(@PathParam("entitlementId") final String entitlementId,
                                            @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                            @HeaderParam(HDR_REASON) final String reason,
                                            @HeaderParam(HDR_COMMENT) final String comment,
                                            @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException {
        final UUID uuid = UUID.fromString(entitlementId);
        final Entitlement current = entitlementApi.getEntitlementForId(uuid, context.createContext(createdBy, reason, comment, request));
        current.uncancel(context.createContext(createdBy, reason, comment, request));
        return Response.status(Status.OK).build();
    }

    @DELETE
    @Path("/{entitlementId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response cancelEntitlementPlan(@PathParam("entitlementId") final String entitlementId,
                                          @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                          @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                          @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("5") final long timeoutSec,
                                          @QueryParam(QUERY_POLICY) final String policyString,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final EntitlementCallCompletionCallback<Response> callback = new EntitlementCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(final CallContext ctx)
                    throws EntitlementApiException, InterruptedException,
                           TimeoutException, AccountApiException {
                final UUID uuid = UUID.fromString(entitlementId);

                final Entitlement current = entitlementApi.getEntitlementForId(uuid, ctx);

                final DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
                final LocalDate inputLocalDate = toLocalDate(current.getAccountId(), inputDate, callContext);

                final Entitlement newEntitlement;
                if (policyString == null) {
                    newEntitlement = current.cancelEntitlementWithDate(inputLocalDate, ctx);
                } else {
                    final BillingActionPolicy policy = BillingActionPolicy.valueOf(policyString.toUpperCase());
                    newEntitlement = current.cancelEntitlementWithDateOverrideBillingPolicy(inputLocalDate, policy, ctx);
                }
                isImmediateOp = newEntitlement.getState() == EntitlementState.ACTIVE;
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
    }

    private interface EntitlementCallCompletionCallback<T> {

        public T doOperation(final CallContext ctx) throws EntitlementApiException, InterruptedException, TimeoutException, AccountApiException;

        public boolean isImmOperation();

        public Response doResponseOk(final T operationResponse) throws EntitlementApiException;
    }

    private class EntitlementCallCompletion<T> {

        public Response withSynchronization(final EntitlementCallCompletionCallback<T> callback,
                                            final long timeoutSec,
                                            final boolean callCompletion,
                                            final CallContext callContext) throws EntitlementApiException, AccountApiException {
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
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
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
