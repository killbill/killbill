/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.resources;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.events.BlockingTransitionInternalEvent;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.events.InvoiceCreationInternalEvent;
import org.killbill.billing.events.InvoicePaymentErrorInternalEvent;
import org.killbill.billing.events.InvoicePaymentInfoInternalEvent;
import org.killbill.billing.events.NullInvoiceInternalEvent;
import org.killbill.billing.events.PaymentErrorInternalEvent;
import org.killbill.billing.events.PaymentInfoInternalEvent;
import org.killbill.billing.events.PaymentPluginErrorInternalEvent;
import org.killbill.billing.jaxrs.json.BlockingStateJson;
import org.killbill.billing.jaxrs.json.BulkSubscriptionsBundleJson;
import org.killbill.billing.jaxrs.json.BundleJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.SubscriptionJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.jaxrs.util.KillbillEventHandler;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.userrequest.CompletionUserRequestBase;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.killbill.billing.jaxrs.resources.SubscriptionResourceHelpers.buildBaseEntitlementWithAddOnsSpecifier;
import static org.killbill.billing.jaxrs.resources.SubscriptionResourceHelpers.buildEntitlementSpecifier;
import static org.killbill.billing.jaxrs.resources.SubscriptionResourceHelpers.buildPlanPhasePriceOverrides;

@Path(JaxrsResource.SUBSCRIPTIONS_PATH)
@Api(value = JaxrsResource.SUBSCRIPTIONS_PATH, description = "Operations on subscriptions", tags = "Subscription")
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
                                final PaymentApi paymentApi,
                                final InvoicePaymentApi invoicePaymentApi,
                                final Clock clock,
                                final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, subscriptionApi, clock, context);
        this.killbillHandler = killbillHandler;
        this.entitlementApi = entitlementApi;
        this.subscriptionApi = subscriptionApi;
    }

    @TimedResource
    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a subscription by id", response = SubscriptionJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Subscription not found")})
    public Response getSubscription(@PathParam("subscriptionId") final UUID subscriptionId,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, AccountApiException, CatalogApiException {
        final TenantContext context = this.context.createTenantContextNoAccountId(request);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(subscriptionId, context);
        final Account account = accountUserApi.getAccountById(subscription.getAccountId(), context);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(subscription.getAccountId(), auditMode.getLevel(), context);
        final SubscriptionJson json = new SubscriptionJson(subscription, account.getCurrency(), accountAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)

    @ApiOperation(value = "Create an subscription", response = SubscriptionJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Subscription created successfully")})
    public Response createSubscription(final SubscriptionJson subscription,
                                       @ApiParam(hidden = true) @Deprecated @QueryParam(QUERY_REQUESTED_DT) final String requestedDate, /* This is deprecated, only used for backward compatibility */
                                       @QueryParam(QUERY_ENTITLEMENT_REQUESTED_DT) final String entitlementDate,
                                       @QueryParam(QUERY_BILLING_REQUESTED_DT) final String billingDate,
                                       @QueryParam(QUERY_BUNDLES_RENAME_KEY_IF_EXIST_UNUSED) @DefaultValue("true") final Boolean renameKeyIfExistsAndUnused,
                                       @QueryParam(QUERY_MIGRATED) @DefaultValue("false") final Boolean isMigrated,
                                       @QueryParam(QUERY_BCD) final Integer newBCD,
                                       @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                       @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                       @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        final List<BulkSubscriptionsBundleJson> entitlementsWithAddOns = ImmutableList.of(new BulkSubscriptionsBundleJson(ImmutableList.<SubscriptionJson>of(subscription)));
        return createSubscriptionsWithAddOnsInternal(entitlementsWithAddOns, requestedDate, entitlementDate, billingDate, isMigrated, renameKeyIfExistsAndUnused, callCompletion, timeoutSec, pluginPropertiesString, createdBy, reason, comment, request, uriInfo, ObjectType.SUBSCRIPTION);
    }

    @TimedResource
    @POST
    @Path("/createSubscriptionWithAddOns")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create an entitlement with addOn products", response = BundleJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Subscriptions created successfully")})
    public Response createSubscriptionWithAddOns(final List<SubscriptionJson> entitlements,
                                                 @ApiParam(hidden = true) @Deprecated  @QueryParam(QUERY_REQUESTED_DT) final String requestedDate, /* This is deprecated, only used for backward compatibility */
                                                 @QueryParam(QUERY_ENTITLEMENT_REQUESTED_DT) final String entitlementDate,
                                                 @QueryParam(QUERY_BILLING_REQUESTED_DT) final String billingDate,
                                                 @QueryParam(QUERY_MIGRATED) @DefaultValue("false") final Boolean isMigrated,
                                                 @QueryParam(QUERY_BUNDLES_RENAME_KEY_IF_EXIST_UNUSED) @DefaultValue("true") final Boolean renameKeyIfExistsAndUnused,
                                                 @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                                 @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                                 @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                 @HeaderParam(HDR_REASON) final String reason,
                                                 @HeaderParam(HDR_COMMENT) final String comment,
                                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        final List<BulkSubscriptionsBundleJson> entitlementsWithAddOns = ImmutableList.of(new BulkSubscriptionsBundleJson(entitlements));
        return createSubscriptionsWithAddOnsInternal(entitlementsWithAddOns, requestedDate, entitlementDate, billingDate, isMigrated, renameKeyIfExistsAndUnused, callCompletion, timeoutSec, pluginPropertiesString, createdBy, reason, comment, request, uriInfo, ObjectType.BUNDLE);
    }

    @TimedResource
    @POST
    @Path("/createSubscriptionsWithAddOns")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create multiple entitlements with addOn products", response = BundleJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Subscriptions created successfully")})
    public Response createSubscriptionsWithAddOns(final List<BulkSubscriptionsBundleJson> entitlementsWithAddOns,
                                                  @ApiParam(hidden = true) @Deprecated  @QueryParam(QUERY_REQUESTED_DT) final String requestedDate, /* This is deprecated, only used for backward compatibility */
                                                  @QueryParam(QUERY_ENTITLEMENT_REQUESTED_DT) final String entitlementDate,
                                                  @QueryParam(QUERY_BILLING_REQUESTED_DT) final String billingDate,
                                                  @QueryParam(QUERY_BUNDLES_RENAME_KEY_IF_EXIST_UNUSED) @DefaultValue("true") final Boolean renameKeyIfExistsAndUnused,
                                                  @QueryParam(QUERY_MIGRATED) @DefaultValue("false") final Boolean isMigrated,
                                                  @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                                  @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                                  @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @javax.ws.rs.core.Context final HttpServletRequest request,
                                                  @javax.ws.rs.core.Context final UriInfo uriInfo) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        return createSubscriptionsWithAddOnsInternal(entitlementsWithAddOns, requestedDate, entitlementDate, billingDate, isMigrated, renameKeyIfExistsAndUnused, callCompletion, timeoutSec, pluginPropertiesString, createdBy, reason, comment, request, uriInfo, ObjectType.ACCOUNT);
    }

    public Response createSubscriptionsWithAddOnsInternal(final List<BulkSubscriptionsBundleJson> entitlementsWithAddOns,
                                                          final String requestedDate,
                                                          final String entitlementDate,
                                                          final String billingDate,
                                                          final Boolean isMigrated,
                                                          final Boolean renameKeyIfExistsAndUnused,
                                                          final Boolean callCompletion,
                                                          final long timeoutSec,
                                                          final List<String> pluginPropertiesString,
                                                          final String createdBy,
                                                          final String reason,
                                                          final String comment,
                                                          final HttpServletRequest request,
                                                          final UriInfo uriInfo,
                                                          final ObjectType responseObject) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        Preconditions.checkArgument(Iterables.size(entitlementsWithAddOns) > 0, "No subscription specified to create");

        logDeprecationParameterWarningIfNeeded(QUERY_REQUESTED_DT, QUERY_ENTITLEMENT_REQUESTED_DT, QUERY_BILLING_REQUESTED_DT);

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        Preconditions.checkArgument(Iterables.size(entitlementsWithAddOns.get(0).getBaseEntitlementAndAddOns()) > 0, "SubscriptionJson body should be specified");
        final Account account = accountUserApi.getAccountById(entitlementsWithAddOns.get(0).getBaseEntitlementAndAddOns().get(0).getAccountId(), callContext);

        final Collection<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();

        for (final BulkSubscriptionsBundleJson subscriptionsBundleJson : entitlementsWithAddOns) {
            UUID bundleId = null;
            String bundleExternalKey = null;
            final Collection<EntitlementSpecifier> entitlementSpecifierList = new ArrayList<EntitlementSpecifier>();
            for (final SubscriptionJson entitlement : subscriptionsBundleJson.getBaseEntitlementAndAddOns()) {
                // verifications
                verifyNonNullOrEmpty(entitlement, "SubscriptionJson body should be specified for each element");
                if (entitlement.getPlanName() == null) {
                    verifyNonNullOrEmpty(entitlement.getProductName(), "SubscriptionJson productName needs to be set when no planName is specified",
                                         entitlement.getProductCategory(), "SubscriptionJson productCategory needs to be set when no planName is specified",
                                         entitlement.getBillingPeriod(), "SubscriptionJson billingPeriod needs to be set when no planName is specified",
                                         entitlement.getPriceList(), "SubscriptionJson priceList needs to be set when no planName is specified");
                } else {
                    Preconditions.checkArgument(entitlement.getProductName() == null, "SubscriptionJson productName should not be set when planName is specified");
                    Preconditions.checkArgument(entitlement.getProductCategory() == null, "SubscriptionJson productCategory should not be set when planName is specified");
                    Preconditions.checkArgument(entitlement.getBillingPeriod() == null, "SubscriptionJson billingPeriod should not be set when planName is specified");
                    Preconditions.checkArgument(entitlement.getPriceList() == null, "SubscriptionJson priceList should not be set when planName is specified");
                }
                Preconditions.checkArgument(account.getId().equals(entitlement.getAccountId()), "SubscriptionJson accountId should be the same for each element");
                // If set on one element, it should be set on all elements
                Preconditions.checkArgument(bundleId == null || bundleId.equals(entitlement.getBundleId()), "SubscriptionJson bundleId should be the same for each element");
                if (bundleId == null) {
                    bundleId = entitlement.getBundleId();
                }
                // Can be set on a single element (e.g. BASE + ADD_ON for a new bundle)
                Preconditions.checkArgument(bundleExternalKey == null || entitlement.getExternalKey() == null || bundleExternalKey.equals(entitlement.getExternalKey()), "SubscriptionJson externalKey should be the same for each element");
                if (bundleExternalKey == null) {
                    bundleExternalKey = entitlement.getExternalKey();
                }
                // create the entitlementSpecifier
                buildEntitlementSpecifier(entitlement, account.getCurrency(), entitlementSpecifierList);
            }

            final LocalDate resolvedEntitlementDate = requestedDate != null ? toLocalDate(requestedDate) : toLocalDate(entitlementDate);
            final LocalDate resolvedBillingDate = requestedDate != null ? toLocalDate(requestedDate) : toLocalDate(billingDate);

            final BaseEntitlementWithAddOnsSpecifier baseEntitlementSpecifierWithAddOns = buildBaseEntitlementWithAddOnsSpecifier(entitlementSpecifierList,
                                                                                                                                  resolvedEntitlementDate,
                                                                                                                                  resolvedBillingDate,
                                                                                                                                  bundleId,
                                                                                                                                  bundleExternalKey,
                                                                                                                                  isMigrated);
            baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementSpecifierWithAddOns);
        }

        final EntitlementCallCompletionCallback<List<UUID>> callback = new EntitlementCallCompletionCallback<List<UUID>>() {
            @Override
            public List<UUID> doOperation(final CallContext ctx) throws EntitlementApiException {
                return entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifierList, renameKeyIfExistsAndUnused, pluginProperties, callContext);
            }

            @Override
            public boolean isImmOperation() {
                return true;
            }

            @Override
            public Response doResponseOk(final List<UUID> entitlementIds) {
                if (responseObject == ObjectType.SUBSCRIPTION) {
                    return uriBuilder.buildResponse(uriInfo, SubscriptionResource.class, "getSubscription", Iterables.getFirst(entitlementIds, null), request);
                }

                final Collection<String> bundleIds = new LinkedHashSet<String>();
                try {
                    for (final Entitlement entitlement : entitlementApi.getAllEntitlementsForAccountId(account.getId(), callContext)) {
                        if (entitlementIds.contains(entitlement.getId())) {
                            bundleIds.add(entitlement.getBundleId().toString());
                        }
                    }
                } catch (final EntitlementApiException e) {
                    return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                }

                if (responseObject == ObjectType.ACCOUNT) {
                    return uriBuilder.buildResponse(uriInfo, AccountResource.class, "getAccountBundles", account.getId(), buildQueryParams(bundleIds), request);
                } else if (responseObject == ObjectType.BUNDLE) {
                    return uriBuilder.buildResponse(uriInfo, BundleResource.class, "getBundle", Iterables.getFirst(bundleIds, null), request);
                } else {
                    throw new IllegalStateException("Unexpected input responseObject " + responseObject);
                }
            }
        };
        final EntitlementCallCompletion<List<UUID>> callCompletionCreation = new EntitlementCallCompletion<List<UUID>>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    private Map<String, String> buildQueryParams(final Iterable<String> bundleIdList) {
        Map<String, String> queryParams = new HashMap<String, String>();
        String value = "";
        for (String bundleId : bundleIdList) {
            if (value.equals("")) {
                value += bundleId;
            } else {
                value += "," + bundleId;
            }
        }
        queryParams.put(QUERY_BUNDLES_FILTER, value);
        return queryParams;
    }

    @TimedResource
    @PUT
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + UNDO_CANCEL)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Un-cancel an entitlement")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Entitlement not found")})
    public Response uncancelSubscriptionPlan(@PathParam("subscriptionId") final UUID subscriptionId,
                                             @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final Entitlement current = entitlementApi.getEntitlementForId(subscriptionId, context.createCallContextNoAccountId(createdBy, reason, comment, request));
        current.uncancelEntitlement(pluginProperties, context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @PUT
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + UNDO_CHANGE_PLAN)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Undo a pending change plan on an entitlement")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Entitlement not found")})
    public Response undoChangeSubscriptionPlan(@PathParam("subscriptionId") final UUID subscriptionId,
                                               @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                               @HeaderParam(HDR_REASON) final String reason,
                                               @HeaderParam(HDR_COMMENT) final String comment,
                                               @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final Entitlement current = entitlementApi.getEntitlementForId(subscriptionId, context.createCallContextNoAccountId(createdBy, reason, comment, request));
        current.undoChangePlan(pluginProperties, context.createCallContextNoAccountId(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @PUT
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Change entitlement plan")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Entitlement not found")})
    public Response changeSubscriptionPlan(@PathParam("subscriptionId") final UUID subscriptionId,
                                           final SubscriptionJson entitlement,
                                           @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                           @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                           @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                           @QueryParam(QUERY_BILLING_POLICY) final BillingActionPolicy billingPolicy,
                                           @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                           @HeaderParam(HDR_REASON) final String reason,
                                           @HeaderParam(HDR_COMMENT) final String comment,
                                           @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        verifyNonNullOrEmpty(entitlement, "SubscriptionJson body should be specified");
        if (entitlement.getPlanName() == null) {
            verifyNonNullOrEmpty(entitlement.getProductName(), "SubscriptionJson productName needs to be set",
                                 entitlement.getBillingPeriod(), "SubscriptionJson billingPeriod needs to be set",
                                 entitlement.getPriceList(), "SubscriptionJson priceList needs to be set");
        }

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        final EntitlementCallCompletionCallback<Response> callback = new EntitlementCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(final CallContext ctx) throws EntitlementApiException,
                                                                      AccountApiException {

                final Entitlement current = entitlementApi.getEntitlementForId(subscriptionId, callContext);
                final LocalDate inputLocalDate = toLocalDate(requestedDate);
                final Entitlement newEntitlement;

                final Account account = accountUserApi.getAccountById(current.getAccountId(), callContext);
                final PhaseType phaseType = entitlement.getPhaseType();
                final PlanPhaseSpecifier planSpec = entitlement.getPlanName() != null ?
                                                    new PlanPhaseSpecifier(entitlement.getPlanName(), phaseType) :
                                                    new PlanPhaseSpecifier(entitlement.getProductName(),
                                                                           entitlement.getBillingPeriod(), entitlement.getPriceList(), phaseType);
                final List<PlanPhasePriceOverride> overrides = buildPlanPhasePriceOverrides(entitlement.getPriceOverrides(), account.getCurrency(), planSpec);

                if (requestedDate == null && billingPolicy == null) {
                    newEntitlement = current.changePlan(new DefaultEntitlementSpecifier(planSpec, null, overrides), pluginProperties, ctx);
                } else if (billingPolicy == null) {
                    newEntitlement = current.changePlanWithDate(new DefaultEntitlementSpecifier(planSpec, null, overrides), inputLocalDate, pluginProperties, ctx);
                } else {
                    newEntitlement = current.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(planSpec, null, overrides), null, billingPolicy, pluginProperties, ctx);
                }
                isImmediateOp = newEntitlement.getLastActiveProduct().getName().equals(entitlement.getProductName()) &&
                                newEntitlement.getLastActivePlan().getRecurringBillingPeriod() == entitlement.getBillingPeriod() &&
                                newEntitlement.getLastActivePriceList().getName().equals(entitlement.getPriceList());
                return Response.status(Status.NO_CONTENT).build();
            }

            @Override
            public boolean isImmOperation() {
                return isImmediateOp;
            }

            @Override
            public Response doResponseOk(final Response operationResponse) throws SubscriptionApiException, AccountApiException, CatalogApiException {
                if (operationResponse.getStatus() != Status.OK.getStatusCode()) {
                    return operationResponse;
                }
                return Response.status(Status.NO_CONTENT).build();
            }
        };

        final EntitlementCallCompletion<Response> callCompletionCreation = new EntitlementCallCompletion<Response>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    @TimedResource
    @POST
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + BLOCK)
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Block a subscription", response = BlockingStateJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Blocking state created successfully"),
                           @ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Subscription not found")})
    public Response addSubscriptionBlockingState(@PathParam(ID_PARAM_NAME) final UUID id,
                                                 final BlockingStateJson json,
                                                 @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                                 @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                 @HeaderParam(HDR_REASON) final String reason,
                                                 @HeaderParam(HDR_COMMENT) final String comment,
                                                 @javax.ws.rs.core.Context final HttpServletRequest request,
                                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws SubscriptionApiException, EntitlementApiException, AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(id, tenantContext);
        return addBlockingState(json, entitlement.getAccountId(), id, BlockingStateType.SUBSCRIPTION, requestedDate, pluginPropertiesString, createdBy, reason, comment, request, uriInfo);
    }

    @TimedResource
    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Cancel an entitlement plan")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Entitlement not found")})
    public Response cancelSubscriptionPlan(@PathParam("subscriptionId") final UUID subscriptionId,
                                           @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                           @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                           @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("5") final long timeoutSec,
                                           @QueryParam(QUERY_ENTITLEMENT_POLICY) final EntitlementActionPolicy entitlementPolicy,
                                           @QueryParam(QUERY_BILLING_POLICY) final BillingActionPolicy billingPolicy,
                                           @QueryParam(QUERY_USE_REQUESTED_DATE_FOR_BILLING) @DefaultValue("false") final Boolean useRequestedDateForBilling,
                                           @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                           @HeaderParam(HDR_REASON) final String reason,
                                           @HeaderParam(HDR_COMMENT) final String comment,
                                           @javax.ws.rs.core.Context final UriInfo uriInfo,
                                           @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);

        final EntitlementCallCompletionCallback<Response> callback = new EntitlementCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(final CallContext ctx)
                    throws EntitlementApiException,
                           SubscriptionApiException {
                final Entitlement current = entitlementApi.getEntitlementForId(subscriptionId, ctx);
                final LocalDate inputLocalDate = toLocalDate(requestedDate);
                final Entitlement newEntitlement;
                if (billingPolicy == null && entitlementPolicy == null) {
                    newEntitlement = current.cancelEntitlementWithDate(inputLocalDate, useRequestedDateForBilling, pluginProperties, ctx);
                } else if (billingPolicy == null && entitlementPolicy != null) {
                    newEntitlement = current.cancelEntitlementWithPolicy(entitlementPolicy, pluginProperties, ctx);
                } else if (billingPolicy != null && entitlementPolicy == null) {
                    newEntitlement = current.cancelEntitlementWithDateOverrideBillingPolicy(inputLocalDate, billingPolicy, pluginProperties, ctx);
                } else {
                    newEntitlement = current.cancelEntitlementWithPolicyOverrideBillingPolicy(entitlementPolicy, billingPolicy, pluginProperties, ctx);
                }

                final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(newEntitlement.getId(), ctx);

                final LocalDate nowInAccountTimeZone = new LocalDate(callContext.getCreatedDate(), subscription.getBillingEndDate().getChronology().getZone());
                isImmediateOp = subscription.getBillingEndDate() != null &&
                                !subscription.getBillingEndDate().isAfter(nowInAccountTimeZone);
                return Response.status(Status.NO_CONTENT).build();
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

    @TimedResource
    @PUT
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + BCD)
    @ApiOperation(value = "Update the BCD associated to a subscription")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid entitlement supplied")})
    public Response updateSubscriptionBCD(@PathParam(ID_PARAM_NAME) final UUID subscriptionId,
                                          final SubscriptionJson json,
                                          @QueryParam(QUERY_ENTITLEMENT_EFFECTIVE_FROM_DT) final String effectiveFromDateStr,
                                          @QueryParam(QUERY_FORCE_NEW_BCD_WITH_PAST_EFFECTIVE_DATE) @DefaultValue("false") final Boolean forceNewBcdWithPastEffectiveDate,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, AccountApiException {

        verifyNonNullOrEmpty(json, "SubscriptionJson body should be specified");
        verifyNonNullOrEmpty(json.getBillCycleDayLocal(), "SubscriptionJson new BCD should be specified");

        LocalDate effectiveFromDate = toLocalDate(effectiveFromDateStr);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        final Entitlement entitlement = entitlementApi.getEntitlementForId(subscriptionId, callContext);
        if (effectiveFromDateStr != null) {
            final Account account = accountUserApi.getAccountById(entitlement.getAccountId(), callContext);
            final LocalDate accountToday = new LocalDate(callContext.getCreatedDate(), account.getTimeZone());
            int comp = effectiveFromDate.compareTo(accountToday);
            switch (comp) {
                case -1:
                    if (!forceNewBcdWithPastEffectiveDate) {
                        throw new IllegalArgumentException("Changing a subscription BCD in the past may have consequences on previous invoice generated. Use flag forceNewBcdWithPastEffectiveDate to force this behavior");
                    }
                    break;
                case 0:
                    // Ensure system will use curremt time for the event so it happens immediately
                    effectiveFromDate = null;
                    break;
                case 1:
                    // Future date, normal case where such effectiveFromDateStr is being passed
                    break;
            }
        }
        entitlement.updateBCD(json.getBillCycleDayLocal(), effectiveFromDate, callContext);
        return Response.status(Status.NO_CONTENT).build();
    }

    private static final class CompletionUserRequestEntitlement extends CompletionUserRequestBase {

        public CompletionUserRequestEntitlement(final UUID userToken) {
            super(userToken);
        }

        @Override
        public void onSubscriptionBaseTransition(final EffectiveSubscriptionInternalEvent event) {

            log.info("Got event SubscriptionBaseTransition token='{}', type='{}', remaining='{}'", event.getUserToken(), event.getTransitionType(), event.getRemainingEventsForUserOperation());
        }

        @Override
        public void onBlockingState(final BlockingTransitionInternalEvent event) {
            log.info(String.format("Got event BlockingTransitionInternalEvent token = %s", event.getUserToken()));
        }

        @Override
        public void onEmptyInvoice(final NullInvoiceInternalEvent event) {
            log.info("Got event EmptyInvoiceNotification token='{}'", event.getUserToken());
            notifyForCompletion();
        }

        @Override
        public void onInvoiceCreation(final InvoiceCreationInternalEvent event) {

            log.info("Got event InvoiceCreationNotification token='{}'", event.getUserToken());
            if (event.getAmountOwed().compareTo(BigDecimal.ZERO) <= 0) {
                notifyForCompletion();
            }
        }

        @Override
        public void onPaymentInfo(final PaymentInfoInternalEvent event) {
            log.info("Got event PaymentInfo token='{}'", event.getUserToken());
            notifyForCompletion();
        }

        @Override
        public void onPaymentError(final PaymentErrorInternalEvent event) {
            log.info("Got event PaymentError token='{}'", event.getUserToken());
            notifyForCompletion();
        }

        @Override
        public void onPaymentPluginError(final PaymentPluginErrorInternalEvent event) {
            log.info("Got event PaymentPluginError token='{}'", event.getUserToken());
            notifyForCompletion();
        }

        @Override
        public void onInvoicePaymentInfo(final InvoicePaymentInfoInternalEvent event) {
            log.info("Got event InvoicePaymentInfo token='{}'", event.getUserToken());
            notifyForCompletion();
        }

        @Override
        public void onInvoicePaymentError(final InvoicePaymentErrorInternalEvent event) {
            log.info("Got event InvoicePaymentError token='{}'", event.getUserToken());
            notifyForCompletion();
        }
    }

    private interface EntitlementCallCompletionCallback<T> {

        T doOperation(final CallContext ctx) throws EntitlementApiException, InterruptedException, TimeoutException, AccountApiException, SubscriptionApiException;

        boolean isImmOperation();

        Response doResponseOk(final T operationResponse) throws SubscriptionApiException, AccountApiException, CatalogApiException;
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
            } catch (final CatalogApiException e) {
                throw new EntitlementApiException(e);
            } catch (final TimeoutException e) {
                return Response.status(408).build();
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
    @ApiOperation(value = "Retrieve subscription custom fields", response = CustomFieldJson.class, responseContainer = "List", nickname = "getSubscriptionCustomFields")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(id, auditMode, context.createTenantContextNoAccountId(request));
    }

    @POST
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to subscription")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Custom field created successfully"),
                           @ApiResponse(code = 400, message = "Invalid subscription id supplied")})
    public Response createSubscriptionCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                   final List<CustomFieldJson> customFields,
                                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                   @HeaderParam(HDR_REASON) final String reason,
                                                   @HeaderParam(HDR_COMMENT) final String comment,
                                                   @javax.ws.rs.core.Context final HttpServletRequest request,
                                                   @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(id, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request), uriInfo, request);
    }

    @PUT
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Modify custom fields to subscription")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid subscription id supplied")})
    public Response modifySubscriptionCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                   final List<CustomFieldJson> customFields,
                                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                   @HeaderParam(HDR_REASON) final String reason,
                                                   @HeaderParam(HDR_COMMENT) final String comment,
                                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.modifyCustomFields(id, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from subscription")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid subscription id supplied")})
    public Response deleteSubscriptionCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                   @QueryParam(QUERY_CUSTOM_FIELD) final List<UUID> customFieldList,
                                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                   @HeaderParam(HDR_REASON) final String reason,
                                                   @HeaderParam(HDR_COMMENT) final String comment,
                                                   @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(id, customFieldList,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve subscription tags", response = TagJson.class, responseContainer = "List", nickname = "getSubscriptionTags")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Subscription not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final UUID subscriptionId,
                            @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, SubscriptionApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(subscriptionId, tenantContext);
        return super.getTags(subscription.getAccountId(), subscriptionId, auditMode, includedDeleted, tenantContext);
    }

    @POST
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Tag created successfully"),
                           @ApiResponse(code = 400, message = "Invalid subscription id supplied")})
    public Response createSubscriptionTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                           final List<UUID> tagList,
                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                           @HeaderParam(HDR_REASON) final String reason,
                                           @HeaderParam(HDR_COMMENT) final String comment,
                                           @javax.ws.rs.core.Context final UriInfo uriInfo,
                                           @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(id, tagList, uriInfo,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request), request);
    }

    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from subscription")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid subscription id supplied")})
    public Response deleteSubscriptionTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                           @QueryParam(QUERY_TAG) final List<UUID> tagList,
                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                           @HeaderParam(HDR_REASON) final String reason,
                                           @HeaderParam(HDR_COMMENT) final String comment,
                                           @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(id, tagList,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.SUBSCRIPTION;
    }

    private Account getAccountFromSubscriptionJson(final SubscriptionJson entitlementJson, final CallContext callContext) throws SubscriptionApiException, AccountApiException, EntitlementApiException {
        final UUID accountId;
        if (entitlementJson.getAccountId() != null) {
            accountId = entitlementJson.getAccountId();
        } else if (entitlementJson.getSubscriptionId() != null) {
            final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementJson.getSubscriptionId(), callContext);
            accountId = entitlement.getAccountId();
        } else {
            final SubscriptionBundle subscriptionBundle = subscriptionApi.getSubscriptionBundle(entitlementJson.getBundleId(), callContext);
            accountId = subscriptionBundle.getAccountId();
        }
        return accountUserApi.getAccountById(accountId, callContext);
    }
}
