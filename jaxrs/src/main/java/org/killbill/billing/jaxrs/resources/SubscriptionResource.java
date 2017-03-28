/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import java.util.HashMap;
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
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.BlockingStateType;
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
import org.killbill.billing.events.NullInvoiceInternalEvent;
import org.killbill.billing.events.PaymentErrorInternalEvent;
import org.killbill.billing.events.PaymentInfoInternalEvent;
import org.killbill.billing.events.PaymentPluginErrorInternalEvent;
import org.killbill.billing.jaxrs.json.BlockingStateJson;
import org.killbill.billing.jaxrs.json.BulkBaseSubscriptionAndAddOnsJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.PhasePriceOverrideJson;
import org.killbill.billing.jaxrs.json.SubscriptionJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.jaxrs.util.KillbillEventHandler;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.api.AuditLevel;
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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.SUBSCRIPTIONS_PATH)
@Api(value = JaxrsResource.SUBSCRIPTIONS_PATH, description = "Operations on subscriptions")
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
                                final Clock clock,
                                final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, subscriptionApi, clock, context);
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
    public Response getEntitlement(@PathParam("subscriptionId") final String subscriptionId,
                                   @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, AccountApiException, CatalogApiException {
        final UUID uuid = UUID.fromString(subscriptionId);
        final TenantContext context = this.context.createContext(request);
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(uuid, context);
        final Account account = accountUserApi.getAccountById(subscription.getAccountId(), context);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(subscription.getAccountId(), auditMode.getLevel(), context);
        final SubscriptionJson json = new SubscriptionJson(subscription, account.getCurrency(), accountAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create an entitlement")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid entitlement supplied")})
    public Response createEntitlement(final SubscriptionJson entitlement,
                                      @QueryParam(QUERY_REQUESTED_DT) final String requestedDate, /* This is deprecated, only used for backward compatibility */
                                      @QueryParam(QUERY_ENTITLEMENT_REQUESTED_DT) final String entitlementDate,
                                      @QueryParam(QUERY_BILLING_REQUESTED_DT) final String billingDate,
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
        verifyNonNullOrEmpty(entitlement, "SubscriptionJson body should be specified");
        if (entitlement.getPlanName() == null) {
            verifyNonNullOrEmpty(entitlement.getProductName(), "SubscriptionJson productName needs to be set",
                                 entitlement.getProductCategory(), "SubscriptionJson productCategory needs to be set",
                                 entitlement.getBillingPeriod(), "SubscriptionJson billingPeriod needs to be set",
                                 entitlement.getPriceList(), "SubscriptionJson priceList needs to be set");
        }

        logDeprecationParameterWarningIfNeeded(QUERY_REQUESTED_DT, QUERY_ENTITLEMENT_REQUESTED_DT, QUERY_BILLING_REQUESTED_DT);

        // For ADD_ON we can provide externalKey or the bundleId
        final boolean createAddOnEntitlement = ProductCategory.ADD_ON.toString().equals(entitlement.getProductCategory());
        if (createAddOnEntitlement) {
            Preconditions.checkArgument(entitlement.getExternalKey() != null || entitlement.getBundleId() != null, "SubscriptionJson bundleId or externalKey should be specified for ADD_ON");
        }

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final EntitlementCallCompletionCallback<Entitlement> callback = new EntitlementCallCompletionCallback<Entitlement>() {
            @Override
            public Entitlement doOperation(final CallContext ctx) throws InterruptedException, TimeoutException, EntitlementApiException, SubscriptionApiException, AccountApiException {

                final Account account = getAccountFromSubscriptionJson(entitlement, callContext);
                final PhaseType phaseType = entitlement.getPhaseType() != null ? PhaseType.valueOf(entitlement.getPhaseType()) : null;
                final PlanPhaseSpecifier spec = entitlement.getPlanName() != null ?
                                                new PlanPhaseSpecifier(entitlement.getPlanName(), phaseType) :
                                                new PlanPhaseSpecifier(entitlement.getProductName(),
                                                                       BillingPeriod.valueOf(entitlement.getBillingPeriod()), entitlement.getPriceList(), phaseType);

                final LocalDate resolvedEntitlementDate = requestedDate != null ? toLocalDate(requestedDate) : toLocalDate(entitlementDate);
                final LocalDate resolvedBillingDate = requestedDate != null ? toLocalDate(requestedDate) : toLocalDate(billingDate);
                final List<PlanPhasePriceOverride> overrides = PhasePriceOverrideJson.toPlanPhasePriceOverrides(entitlement.getPriceOverrides(), spec, account.getCurrency());
                final Entitlement result = createAddOnEntitlement ?
                                           entitlementApi.addEntitlement(getBundleIdForAddOnCreation(entitlement), spec, overrides, resolvedEntitlementDate, resolvedBillingDate, isMigrated, pluginProperties, callContext) :
                                           entitlementApi.createBaseEntitlement(account.getId(), spec, entitlement.getExternalKey(), overrides, resolvedEntitlementDate, resolvedBillingDate, isMigrated, pluginProperties, callContext);
                if (newBCD != null) {
                    result.updateBCD(newBCD, null, callContext);
                }
                return result;
            }

            private UUID getBundleIdForAddOnCreation(final SubscriptionJson entitlement) throws SubscriptionApiException {

                if (entitlement.getBundleId() != null) {
                    return UUID.fromString(entitlement.getBundleId());
                }
                // If user only specified the externalKey we need to fech the bundle (expensive operation) to extract the bundleId
                final SubscriptionBundle bundle = subscriptionApi.getActiveSubscriptionBundleForExternalKey(entitlement.getExternalKey(), callContext);
                return bundle.getId();
            }

            @Override
            public boolean isImmOperation() {
                return true;
            }

            @Override
            public Response doResponseOk(final Entitlement createdEntitlement) {
                return uriBuilder.buildResponse(uriInfo, SubscriptionResource.class, "getEntitlement", createdEntitlement.getId(), request);
            }
        };

        final EntitlementCallCompletion<Entitlement> callCompletionCreation = new EntitlementCallCompletion<Entitlement>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    @TimedResource
    @POST
    @Path("/createEntitlementWithAddOns")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create an entitlement with addOn products")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid entitlement supplied")})
    public Response createEntitlementWithAddOns(final List<SubscriptionJson> entitlements,
                                                @QueryParam(QUERY_REQUESTED_DT) final String requestedDate, /* This is deprecated, only used for backward compatibility */
                                                @QueryParam(QUERY_ENTITLEMENT_REQUESTED_DT) final String entitlementDate,
                                                @QueryParam(QUERY_BILLING_REQUESTED_DT) final String billingDate,
                                                @QueryParam(QUERY_MIGRATED) @DefaultValue("false") final Boolean isMigrated,
                                                @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                                @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                @HeaderParam(HDR_REASON) final String reason,
                                                @HeaderParam(HDR_COMMENT) final String comment,
                                                @javax.ws.rs.core.Context final HttpServletRequest request,
                                                @javax.ws.rs.core.Context final UriInfo uriInfo) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        final List<BulkBaseSubscriptionAndAddOnsJson> entitlementsWithAddOns = ImmutableList.of(new BulkBaseSubscriptionAndAddOnsJson(entitlements));
        return createEntitlementsWithAddOnsInternal(entitlementsWithAddOns, requestedDate, entitlementDate, billingDate, isMigrated, callCompletion, timeoutSec, pluginPropertiesString, createdBy, reason, comment, request, uriInfo, ObjectType.BUNDLE);
    }

    @TimedResource
    @POST
    @Path("/createEntitlementsWithAddOns")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create multiple entitlements with addOn products")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid entitlements supplied")})
    public Response createEntitlementsWithAddOns(final List<BulkBaseSubscriptionAndAddOnsJson> entitlementsWithAddOns,
                                                @QueryParam(QUERY_REQUESTED_DT) final String requestedDate, /* This is deprecated, only used for backward compatibility */
                                                @QueryParam(QUERY_ENTITLEMENT_REQUESTED_DT) final String entitlementDate,
                                                @QueryParam(QUERY_BILLING_REQUESTED_DT) final String billingDate,
                                                @QueryParam(QUERY_MIGRATED) @DefaultValue("false") final Boolean isMigrated,
                                                @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                                @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                @HeaderParam(HDR_REASON) final String reason,
                                                @HeaderParam(HDR_COMMENT) final String comment,
                                                @javax.ws.rs.core.Context final HttpServletRequest request,
                                                @javax.ws.rs.core.Context final UriInfo uriInfo) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        return createEntitlementsWithAddOnsInternal(entitlementsWithAddOns, requestedDate, entitlementDate, billingDate, isMigrated, callCompletion, timeoutSec, pluginPropertiesString, createdBy, reason, comment, request, uriInfo, ObjectType.ACCOUNT);
    }


    public Response createEntitlementsWithAddOnsInternal(final List<BulkBaseSubscriptionAndAddOnsJson> entitlementsWithAddOns,
                                                 final String requestedDate,
                                                 final String entitlementDate,
                                                 final String billingDate,
                                                 final Boolean isMigrated, final Boolean callCompletion,
                                                 final long timeoutSec,
                                                 final List<String> pluginPropertiesString,
                                                 final String createdBy,
                                                 final String reason,
                                                 final String comment,
                                                 final HttpServletRequest request,
                                                 final UriInfo uriInfo, final ObjectType responseObject) throws EntitlementApiException, AccountApiException, SubscriptionApiException {

        Preconditions.checkArgument(Iterables.size(entitlementsWithAddOns) > 0, "Subscription bulk list mustn't be null or empty.");

        logDeprecationParameterWarningIfNeeded(QUERY_REQUESTED_DT, QUERY_ENTITLEMENT_REQUESTED_DT, QUERY_BILLING_REQUESTED_DT);

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(entitlementsWithAddOns.get(0).getBaseEntitlementAndAddOns().get(0).getAccountId()), callContext);

        final List<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        for (BulkBaseSubscriptionAndAddOnsJson bulkBaseEntitlementWithAddOns : entitlementsWithAddOns) {
            final Iterable<SubscriptionJson> baseEntitlements = Iterables.filter(
                    bulkBaseEntitlementWithAddOns.getBaseEntitlementAndAddOns(), new Predicate<SubscriptionJson>() {
                        @Override
                        public boolean apply(final SubscriptionJson subscription) {
                            return ProductCategory.BASE.toString().equalsIgnoreCase(subscription.getProductCategory());
                        }
                    });
            Preconditions.checkArgument(Iterables.size(baseEntitlements) > 0, "SubscriptionJson Base Entitlement needs to be provided");
            verifyNumberOfElements(Iterables.size(baseEntitlements), 1, "Only one BASE product is allowed per bundle.");
            final SubscriptionJson baseEntitlement = baseEntitlements.iterator().next();


            final Iterable<SubscriptionJson> addonEntitlements = Iterables.filter(
                    bulkBaseEntitlementWithAddOns.getBaseEntitlementAndAddOns(), new Predicate<SubscriptionJson>() {
                        @Override
                        public boolean apply(final SubscriptionJson subscription) {
                            return ProductCategory.ADD_ON.toString().equalsIgnoreCase(subscription.getProductCategory());
                        }
                    }
                                                                                 );


            final List<EntitlementSpecifier> entitlementSpecifierList = buildEntitlementSpecifierList(baseEntitlement, addonEntitlements, account.getCurrency());

            // create the baseEntitlementSpecifierWithAddOns
            final LocalDate resolvedEntitlementDate = requestedDate != null ? toLocalDate(requestedDate) : toLocalDate(entitlementDate);
            final LocalDate resolvedBillingDate = requestedDate != null ? toLocalDate(requestedDate) : toLocalDate(billingDate);

            BaseEntitlementWithAddOnsSpecifier baseEntitlementSpecifierWithAddOns = buildBaseEntitlementWithAddOnsSpecifier(entitlementSpecifierList, resolvedEntitlementDate, resolvedBillingDate, null, baseEntitlement, isMigrated);
            baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementSpecifierWithAddOns);
        }

        final EntitlementCallCompletionCallback<List<Entitlement>> callback = new EntitlementCallCompletionCallback<List<Entitlement>>() {
            @Override
            public List<Entitlement> doOperation(final CallContext ctx) throws InterruptedException, TimeoutException, EntitlementApiException, SubscriptionApiException, AccountApiException {
                return entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), baseEntitlementWithAddOnsSpecifierList, pluginProperties, callContext);
            }
            @Override
            public boolean isImmOperation() {
                return true;
            }
            @Override
            public Response doResponseOk(final List<Entitlement> entitlements) {
                if (responseObject == ObjectType.ACCOUNT) {
                    return uriBuilder.buildResponse(uriInfo, AccountResource.class, "getAccountBundles", entitlements.get(0).getAccountId(), buildQueryParams(buildBundleIdList(entitlements)), request);
                } else if (responseObject == ObjectType.BUNDLE) {
                    return uriBuilder.buildResponse(uriInfo, BundleResource.class, "getBundle", entitlements.get(0).getBundleId(), request);
                } else {
                    throw new IllegalStateException("Unexpected input responseObject " + responseObject);
                }
            }
        };
        final EntitlementCallCompletion<List<Entitlement>> callCompletionCreation = new EntitlementCallCompletion<List<Entitlement>>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }



    private List<EntitlementSpecifier> buildEntitlementSpecifierList(final SubscriptionJson baseEntitlement, final Iterable<SubscriptionJson> addonEntitlements, final Currency currency) {
        final List<EntitlementSpecifier> entitlementSpecifierList = new ArrayList<EntitlementSpecifier>();

        //
        // BASE is fully specified we can add it
        //
        if (baseEntitlement.getPlanName() != null ||
            (baseEntitlement.getProductName() != null &&
             baseEntitlement.getProductCategory() != null &&
            baseEntitlement.getBillingPeriod() != null &&
            baseEntitlement.getPriceList() != null)) {
            final PlanPhaseSpecifier planPhaseSpecifier = baseEntitlement.getPlanName() != null ?
                                                          new PlanPhaseSpecifier(baseEntitlement.getPlanName(), null) :
                                                          new PlanPhaseSpecifier(baseEntitlement.getProductName(),
                                                                                 BillingPeriod.valueOf(baseEntitlement.getBillingPeriod()), baseEntitlement.getPriceList(), null);
            final List<PlanPhasePriceOverride> overrides = PhasePriceOverrideJson.toPlanPhasePriceOverrides(baseEntitlement.getPriceOverrides(), planPhaseSpecifier, currency);

            EntitlementSpecifier specifier = new EntitlementSpecifier() {
                @Override
                public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                    return planPhaseSpecifier;
                }
                @Override
                public List<PlanPhasePriceOverride> getOverrides() {
                    return overrides;
                }
            };
            entitlementSpecifierList.add(specifier);
        }

        for (final SubscriptionJson entitlement : addonEntitlements) {
            // verifications
            verifyNonNullOrEmpty(entitlement, "SubscriptionJson body should be specified for each element");
            if (entitlement.getPlanName() == null) {
                verifyNonNullOrEmpty(entitlement.getProductName(), "SubscriptionJson productName needs to be set for each element",
                                     entitlement.getProductCategory(), "SubscriptionJson productCategory needs to be set for each element",
                                     entitlement.getBillingPeriod(), "SubscriptionJson billingPeriod needs to be set for each element",
                                     entitlement.getPriceList(), "SubscriptionJson priceList needs to be set for each element");
            }
            // create the entitlementSpecifier
            final PlanPhaseSpecifier planPhaseSpecifier = entitlement.getPlanName() != null ?
                                                          new PlanPhaseSpecifier(entitlement.getPlanName(), null) :
                                                          new PlanPhaseSpecifier(entitlement.getProductName(),
                                                                                 BillingPeriod.valueOf(entitlement.getBillingPeriod()), entitlement.getPriceList(), null);
            final List<PlanPhasePriceOverride> overrides = PhasePriceOverrideJson.toPlanPhasePriceOverrides(entitlement.getPriceOverrides(), planPhaseSpecifier, currency);

            EntitlementSpecifier specifier = new EntitlementSpecifier() {
                @Override
                public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                    return planPhaseSpecifier;
                }
                @Override
                public List<PlanPhasePriceOverride> getOverrides() {
                    return overrides;
                }
            };
            entitlementSpecifierList.add(specifier);
        }
        return entitlementSpecifierList;
    }

    private BaseEntitlementWithAddOnsSpecifier buildBaseEntitlementWithAddOnsSpecifier(final List<EntitlementSpecifier> entitlementSpecifierList, final LocalDate resolvedEntitlementDate, final LocalDate resolvedBillingDate, final UUID bundleId, final SubscriptionJson baseEntitlement, final @QueryParam(QUERY_MIGRATED) @DefaultValue("false") Boolean isMigrated) {
        return new BaseEntitlementWithAddOnsSpecifier() {
            @Override
            public UUID getBundleId() {
                return bundleId;
            }
            @Override
            public String getExternalKey() {
                return baseEntitlement.getExternalKey();
            }
            @Override
            public Iterable<EntitlementSpecifier> getEntitlementSpecifier() {
                return entitlementSpecifierList;
            }
            @Override
            public LocalDate getEntitlementEffectiveDate() {
                return resolvedEntitlementDate;
            }
            @Override
            public LocalDate getBillingEffectiveDate() {
                return resolvedBillingDate;
            }
            @Override
            public boolean isMigrated() {
                return isMigrated;
            }
        };
    }

    private List<String> buildBundleIdList(final List<Entitlement> entitlements) {
        List<String> result = new ArrayList<String>();
        for (Entitlement entitlement : entitlements) {
            if (!result.contains(entitlement.getBundleId().toString())) {
                result.add(entitlement.getBundleId().toString());
            }
        }
        return result;
    }

    private Map<String, String> buildQueryParams(final List<String> bundleIdList) {
        Map<String, String> queryParams = new HashMap<String, String>();
        String value = "";
        for (String bundleId : bundleIdList) {
            if (value.equals("")) {
                value += bundleId;
            } else value+="," + bundleId;
        }
        queryParams.put(QUERY_BUNDLES_FILTER, value);
        return queryParams;
    }

    @TimedResource
    @PUT
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/uncancel")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Un-cancel an entitlement")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Entitlement not found")})
    public Response uncancelEntitlementPlan(@PathParam("subscriptionId") final String subscriptionId,
                                            @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                            @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                            @HeaderParam(HDR_REASON) final String reason,
                                            @HeaderParam(HDR_COMMENT) final String comment,
                                            @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final UUID uuid = UUID.fromString(subscriptionId);
        final Entitlement current = entitlementApi.getEntitlementForId(uuid, context.createContext(createdBy, reason, comment, request));
        current.uncancelEntitlement(pluginProperties, context.createContext(createdBy, reason, comment, request));
        return Response.status(Status.OK).build();
    }

    @TimedResource
    @PUT
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Change entitlement plan")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Entitlement not found")})
    public Response changeEntitlementPlan(final SubscriptionJson entitlement,
                                          @PathParam("subscriptionId") final String subscriptionId,
                                          @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                          @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                          @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("3") final long timeoutSec,
                                          @QueryParam(QUERY_BILLING_POLICY) final String policyString,
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
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final EntitlementCallCompletionCallback<Response> callback = new EntitlementCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(final CallContext ctx) throws EntitlementApiException, InterruptedException,
                                                                      TimeoutException, AccountApiException {
                final UUID uuid = UUID.fromString(subscriptionId);

                final Entitlement current = entitlementApi.getEntitlementForId(uuid, callContext);
                final LocalDate inputLocalDate = toLocalDate(requestedDate);
                final Entitlement newEntitlement;

                final Account account = accountUserApi.getAccountById(current.getAccountId(), callContext);
                final PlanSpecifier planSpec = entitlement.getPlanName() != null ?
                                               new PlanSpecifier(entitlement.getPlanName()) :
                                               new PlanSpecifier(entitlement.getProductName(),
                                                                 BillingPeriod.valueOf(entitlement.getBillingPeriod()), entitlement.getPriceList());
                final List<PlanPhasePriceOverride> overrides = PhasePriceOverrideJson.toPlanPhasePriceOverrides(entitlement.getPriceOverrides(), planSpec, account.getCurrency());

                if (requestedDate == null && policyString == null) {
                    newEntitlement = current.changePlan(planSpec, overrides, pluginProperties, ctx);
                } else if (policyString == null) {
                    newEntitlement = current.changePlanWithDate(planSpec, overrides, inputLocalDate, pluginProperties, ctx);
                } else {
                    final BillingActionPolicy policy = BillingActionPolicy.valueOf(policyString.toUpperCase());
                    newEntitlement = current.changePlanOverrideBillingPolicy(planSpec, overrides, inputLocalDate, policy, pluginProperties, ctx);
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
            public Response doResponseOk(final Response operationResponse) throws SubscriptionApiException, AccountApiException, CatalogApiException {
                if (operationResponse.getStatus() != Status.OK.getStatusCode()) {
                    return operationResponse;
                }
                return getEntitlement(subscriptionId, new AuditMode(AuditLevel.NONE.toString()), request);
            }
        };

        final EntitlementCallCompletion<Response> callCompletionCreation = new EntitlementCallCompletion<Response>();
        return callCompletionCreation.withSynchronization(callback, timeoutSec, callCompletion, callContext);
    }

    @TimedResource
    @PUT
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + BLOCK)
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Block a subscription")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Subscription not found")})
    public Response addSubscriptionBlockingState(final BlockingStateJson json,
                                                 @PathParam(ID_PARAM_NAME) final String id,
                                                 @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                                 @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                 @HeaderParam(HDR_REASON) final String reason,
                                                 @HeaderParam(HDR_COMMENT) final String comment,
                                                 @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, EntitlementApiException, AccountApiException {

        return addBlockingState(json, id, BlockingStateType.SUBSCRIPTION, requestedDate, pluginPropertiesString, createdBy, reason, comment, request);
    }

    @TimedResource
    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Cancel an entitlement plan")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Entitlement not found")})
    public Response cancelEntitlementPlan(@PathParam("subscriptionId") final String subscriptionId,
                                          @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                          @QueryParam(QUERY_CALL_COMPLETION) @DefaultValue("false") final Boolean callCompletion,
                                          @QueryParam(QUERY_CALL_TIMEOUT) @DefaultValue("5") final long timeoutSec,
                                          @QueryParam(QUERY_ENTITLEMENT_POLICY) final String entitlementPolicyString,
                                          @QueryParam(QUERY_BILLING_POLICY) final String billingPolicyString,
                                          @QueryParam(QUERY_USE_REQUESTED_DATE_FOR_BILLING) @DefaultValue("false") final Boolean useRequestedDateForBilling,
                                          @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, AccountApiException, SubscriptionApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);

        final EntitlementCallCompletionCallback<Response> callback = new EntitlementCallCompletionCallback<Response>() {

            private boolean isImmediateOp = true;

            @Override
            public Response doOperation(final CallContext ctx)
                    throws EntitlementApiException, InterruptedException,
                           TimeoutException, AccountApiException, SubscriptionApiException {
                final UUID uuid = UUID.fromString(subscriptionId);

                final Entitlement current = entitlementApi.getEntitlementForId(uuid, ctx);
                final LocalDate inputLocalDate = toLocalDate(requestedDate);
                final Entitlement newEntitlement;
                if (billingPolicyString == null && entitlementPolicyString == null) {
                    newEntitlement = current.cancelEntitlementWithDate(inputLocalDate, useRequestedDateForBilling, pluginProperties, ctx);
                } else if (billingPolicyString == null && entitlementPolicyString != null) {
                    final EntitlementActionPolicy entitlementPolicy = EntitlementActionPolicy.valueOf(entitlementPolicyString);
                    newEntitlement = current.cancelEntitlementWithPolicy(entitlementPolicy, pluginProperties, ctx);
                } else if (billingPolicyString != null && entitlementPolicyString == null) {
                    final BillingActionPolicy billingPolicy = BillingActionPolicy.valueOf(billingPolicyString.toUpperCase());
                    newEntitlement = current.cancelEntitlementWithDateOverrideBillingPolicy(inputLocalDate, billingPolicy, pluginProperties, ctx);
                } else {
                    final EntitlementActionPolicy entitlementPolicy = EntitlementActionPolicy.valueOf(entitlementPolicyString);
                    final BillingActionPolicy billingPolicy = BillingActionPolicy.valueOf(billingPolicyString.toUpperCase());
                    newEntitlement = current.cancelEntitlementWithPolicyOverrideBillingPolicy(entitlementPolicy, billingPolicy, pluginProperties, ctx);
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

    @TimedResource
    @PUT
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + BCD)
    @ApiOperation(value = "Update the BCD associated to a subscription")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid entitlement supplied")})
    public Response updateSubscriptionBCD(final SubscriptionJson json,
                                          @PathParam(ID_PARAM_NAME) final String id,
                                          @QueryParam(QUERY_ENTITLEMENT_EFFECTIVE_FROM_DT) final String effectiveFromDateStr,
                                          @QueryParam(QUERY_FORCE_NEW_BCD_WITH_PAST_EFFECTIVE_DATE) @DefaultValue("false") final Boolean forceNewBcdWithPastEffectiveDate,
                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                          @HeaderParam(HDR_REASON) final String reason,
                                          @HeaderParam(HDR_COMMENT) final String comment,
                                          @javax.ws.rs.core.Context final UriInfo uriInfo,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, EntitlementApiException, AccountApiException {

        verifyNonNullOrEmpty(json, "SubscriptionJson body should be specified");
        verifyNonNullOrEmpty(json.getBillCycleDayLocal(), "SubscriptionJson new BCD should be specified");

        LocalDate effectiveFromDate = toLocalDate(effectiveFromDateStr);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID subscriptionId = UUID.fromString(id);

        final Entitlement entitlement = entitlementApi.getEntitlementForId(subscriptionId, callContext);
        if (effectiveFromDateStr != null) {
            final Account account = accountUserApi.getAccountById(entitlement.getAccountId(), callContext);
            final LocalDate accountToday  =  new LocalDate(clock.getUTCNow(), account.getTimeZone());
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
        return Response.status(Status.OK).build();
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
        public void onInvoicePaymentError(final InvoicePaymentErrorInternalEvent event) {
            log.info("Got event InvoicePaymentError token='{}'", event.getUserToken());
            notifyForCompletion();
        }
    }

    private interface EntitlementCallCompletionCallback<T> {

        public T doOperation(final CallContext ctx) throws EntitlementApiException, InterruptedException, TimeoutException, AccountApiException, SubscriptionApiException;

        public boolean isImmOperation();

        public Response doResponseOk(final T operationResponse) throws SubscriptionApiException, AccountApiException, CatalogApiException;
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
    @ApiOperation(value = "Retrieve subscription custom fields", response = CustomFieldJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), auditMode, context.createContext(request));
    }

    @POST
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to subscription")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied")})
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       final List<CustomFieldJson> customFields,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(UUID.fromString(id), customFields,
                                        context.createContext(createdBy, reason, comment, request), uriInfo, request);
    }

    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from subscription")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied")})
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
    @ApiOperation(value = "Retrieve subscription tags", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied"),
                           @ApiResponse(code = 404, message = "Subscription not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final String subscriptionIdString,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
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
    @ApiOperation(value = "Add tags to subscription")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied")})
    public Response createTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final UriInfo uriInfo,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(UUID.fromString(id), tagList, uriInfo,
                                context.createContext(createdBy, reason, comment, request), request);
    }

    @DELETE
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from subscription")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid subscription id supplied")})
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

    private Account getAccountFromSubscriptionJson(final SubscriptionJson entitlementJson, final CallContext callContext) throws SubscriptionApiException, AccountApiException, EntitlementApiException {
        final UUID accountId;
        if (entitlementJson.getAccountId() != null) {
            accountId = UUID.fromString(entitlementJson.getAccountId());
        } else if (entitlementJson.getSubscriptionId() != null) {
            final Entitlement entitlement = entitlementApi.getEntitlementForId(UUID.fromString(entitlementJson.getSubscriptionId()), callContext);
            accountId = entitlement.getAccountId();
        } else {
            final SubscriptionBundle subscriptionBundle = subscriptionApi.getSubscriptionBundle(UUID.fromString(entitlementJson.getBundleId()), callContext);
            accountId = subscriptionBundle.getAccountId();
        }
        return accountUserApi.getAccountById(accountId, callContext);
    }
}
