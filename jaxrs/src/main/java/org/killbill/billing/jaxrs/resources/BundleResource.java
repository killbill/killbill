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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.jaxrs.json.BlockingStateJson;
import org.killbill.billing.jaxrs.json.BundleJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
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
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.BUNDLES_PATH)
@Api(value = JaxrsResource.BUNDLES_PATH, description = "Operations on bundles", tags="Bundle")
public class BundleResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "bundleId";

    private final SubscriptionApi subscriptionApi;
    private final EntitlementApi entitlementApi;

    @Inject
    public BundleResource(final JaxrsUriBuilder uriBuilder,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final AuditUserApi auditUserApi,
                          final AccountUserApi accountUserApi,
                          final SubscriptionApi subscriptionApi,
                          final EntitlementApi entitlementApi,
                          final PaymentApi paymentApi,
                          final InvoicePaymentApi invoicePaymentApi,
                          final Clock clock,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, subscriptionApi, clock, context);
        this.entitlementApi = entitlementApi;
        this.subscriptionApi = subscriptionApi;
    }

    @TimedResource
    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a bundle by id", response = BundleJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid bundle id supplied"),
                           @ApiResponse(code = 404, message = "Bundle not found")})
    public Response getBundle(@PathParam("bundleId") final UUID bundleId,
                              @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                              @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, AccountApiException, CatalogApiException {
        final TenantContext tenantContext = this.context.createTenantContextNoAccountId(request);
        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(bundleId, tenantContext);
        final Account account = accountUserApi.getAccountById(bundle.getAccountId(), tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(bundle.getAccountId(), auditMode.getLevel(), tenantContext);
        final BundleJson json = new BundleJson(bundle, account.getCurrency(), accountAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a bundle by external key", response = BundleJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Bundle not found")})
    public Response getBundleByKey(@ApiParam(required=true) @QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                   @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                                   @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, AccountApiException, CatalogApiException {

        final TenantContext tenantContext = this.context.createTenantContextNoAccountId(request);

        final List<SubscriptionBundle> bundles;
        if (includedDeleted) {
            bundles = subscriptionApi.getSubscriptionBundlesForExternalKey(externalKey, tenantContext);
        } else {
            final SubscriptionBundle activeBundle = subscriptionApi.getActiveSubscriptionBundleForExternalKey(externalKey, tenantContext);
            bundles = ImmutableList.of(activeBundle);
        }
        final List<BundleJson> result = new ArrayList<BundleJson>(bundles.size());
        for (final SubscriptionBundle bundle : bundles) {
            final Account account = accountUserApi.getAccountById(bundle.getAccountId(), tenantContext);
            final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(bundle.getAccountId(), auditMode.getLevel(), tenantContext);
            final BundleJson json = new BundleJson(bundle, account.getCurrency(), accountAuditLogs);
            result.add(json);
        }
        return Response.status(Status.OK).entity(result).build();
    }

    @TimedResource
    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "List bundles", response = BundleJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response getBundles(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                               @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<SubscriptionBundle> bundles = subscriptionApi.getSubscriptionBundles(offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(BundleResource.class, "getBundles", bundles.getNextOffset(), limit, ImmutableMap.<String, String>of(QUERY_AUDIT, auditMode.getLevel().toString()));
        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());
        return buildStreamingPaginationResponse(bundles,
                                                new Function<SubscriptionBundle, BundleJson>() {
                                                    @Override
                                                    public BundleJson apply(final SubscriptionBundle bundle) {
                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(bundle.getAccountId()) == null) {
                                                            accountsAuditLogs.get().put(bundle.getAccountId(), auditUserApi.getAccountAuditLogs(bundle.getAccountId(), auditMode.getLevel(), tenantContext));
                                                        }

                                                        try {
                                                            return new BundleJson(bundle, null, accountsAuditLogs.get().get(bundle.getAccountId()));
                                                        } catch (final CatalogApiException unused) {
                                                            // Does not happen because we pass a null Currency
                                                            throw new RuntimeException(unused);
                                                        }
                                                    }
                                                },
                                                nextPageUri);
    }

    @TimedResource
    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Search bundles", response = BundleJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response searchBundles(@PathParam("searchKey") final String searchKey,
                                  @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                  @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                  @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Pagination<SubscriptionBundle> bundles = subscriptionApi.searchSubscriptionBundles(searchKey, offset, limit, tenantContext);
        final URI nextPageUri = uriBuilder.nextPage(BundleResource.class, "searchBundles", bundles.getNextOffset(), limit, ImmutableMap.<String, String>of("searchKey", searchKey,
                                                                                                                                                           QUERY_AUDIT, auditMode.getLevel().toString()));
        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());
        return buildStreamingPaginationResponse(bundles,
                                                new Function<SubscriptionBundle, BundleJson>() {
                                                    @Override
                                                    public BundleJson apply(final SubscriptionBundle bundle) {
                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(bundle.getAccountId()) == null) {
                                                            accountsAuditLogs.get().put(bundle.getAccountId(), auditUserApi.getAccountAuditLogs(bundle.getAccountId(), auditMode.getLevel(), tenantContext));
                                                        }
                                                        try {
                                                            return new BundleJson(bundle, null, accountsAuditLogs.get().get(bundle.getAccountId()));
                                                        } catch (final CatalogApiException unused) {
                                                            // Does not happen because we pass a null Currency
                                                            throw new RuntimeException(unused);
                                                        }
                                                    }
                                                },
                                                nextPageUri);
    }

    @TimedResource
    @PUT
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + PAUSE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Pause a bundle")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid bundle id supplied"),
                           @ApiResponse(code = 404, message = "Bundle not found")})
    public Response pauseBundle(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                                @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, EntitlementApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        final LocalDate inputLocalDate = toLocalDate(requestedDate);
        entitlementApi.pause(bundleId, inputLocalDate, pluginProperties, callContext);
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @PUT
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + RESUME)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Resume a bundle")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid bundle id supplied"),
                           @ApiResponse(code = 404, message = "Bundle not found")})
    public Response resumeBundle(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                                 @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                 @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, EntitlementApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        final LocalDate inputLocalDate = toLocalDate(requestedDate);
        entitlementApi.resume(bundleId, inputLocalDate, pluginProperties, callContext);
        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @POST
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + BLOCK)
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Block a bundle", response = BlockingStateJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Blocking state created successfully"),
                           @ApiResponse(code = 400, message = "Invalid bundle id supplied"),
                           @ApiResponse(code = 404, message = "Bundle not found")})
    public Response addBundleBlockingState(@PathParam(ID_PARAM_NAME) final UUID id,
                                           final BlockingStateJson json,
                                           @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                           @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                           @HeaderParam(HDR_REASON) final String reason,
                                           @HeaderParam(HDR_COMMENT) final String comment,
                                           @javax.ws.rs.core.Context final HttpServletRequest request,
                                           @javax.ws.rs.core.Context final UriInfo uriInfo) throws SubscriptionApiException, EntitlementApiException, AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(id, tenantContext);
        return addBlockingState(json, bundle.getAccountId(), id, BlockingStateType.SUBSCRIPTION_BUNDLE, requestedDate, pluginPropertiesString, createdBy, reason, comment, request, uriInfo);
    }



    @TimedResource
    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve bundle custom fields", response = CustomFieldJson.class, responseContainer = "List", nickname = "getBundleCustomFields")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid bundle id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(bundleId, auditMode, context.createTenantContextNoAccountId(request));
    }

    @TimedResource
    @POST
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to bundle", response = CustomField.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Custom field created successfully"),
                           @ApiResponse(code = 400, message = "Invalid bundle id supplied")})
    public Response createBundleCustomFields(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                                             final List<CustomFieldJson> customFields,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final HttpServletRequest request,
                                             @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(bundleId, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request), uriInfo, request);
    }

    @TimedResource
    @PUT
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Modify custom fields to bundle")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid bundle id supplied")})
    public Response modifyBundleCustomFields(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                                             final List<CustomFieldJson> customFields,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.modifyCustomFields(bundleId, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }




    @TimedResource
    @DELETE
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from bundle")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid bundle id supplied")})
    public Response deleteBundleCustomFields(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                                             @QueryParam(QUERY_CUSTOM_FIELD) final List<UUID> customFieldList,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(bundleId, customFieldList,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve bundle tags", response = TagJson.class, responseContainer = "List", nickname = "getBundleTags")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid bundle id supplied"),
                           @ApiResponse(code = 404, message = "Bundle not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                            @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, SubscriptionApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(bundleId, tenantContext);
        return super.getTags(bundle.getAccountId(), bundleId, auditMode, includedDeleted, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{bundleId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Transfer a bundle to another account", response = BundleJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Bundle transferred successfully"),
                           @ApiResponse(code = 400, message = "Invalid bundle id, requested date or policy supplied"),
                           @ApiResponse(code = 404, message = "Bundle not found")})
    public Response transferBundle(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                                   final BundleJson json,
                                   @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                   @QueryParam(QUERY_BILLING_POLICY) @DefaultValue("END_OF_TERM") final BillingActionPolicy billingPolicy,
                                   @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @javax.ws.rs.core.Context final UriInfo uriInfo,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, SubscriptionApiException, AccountApiException {
        verifyNonNullOrEmpty(json, "BundleJson body should be specified");
        verifyNonNullOrEmpty(json.getAccountId(), "BundleJson accountId needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);

        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(bundleId, callContext);
        final LocalDate inputLocalDate = toLocalDate(requestedDate);

        final UUID newBundleId = entitlementApi.transferEntitlementsOverrideBillingPolicy(bundle.getAccountId(), json.getAccountId(), bundle.getExternalKey(), inputLocalDate, billingPolicy, pluginProperties, callContext);
        return uriBuilder.buildResponse(uriInfo, BundleResource.class, "getBundle", newBundleId, request);
    }


    @TimedResource
    @PUT
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + RENAME_KEY)
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update a bundle externalKey")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid argumnent supplied"),
                           @ApiResponse(code = 404, message = "Bundle not found")})
    public Response renameExternalKey(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                                      final BundleJson json,
                                      /* @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString, */
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final UriInfo uriInfo,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException {

        verifyNonNullOrEmpty(json, "BundleJson body should be specified");
        verifyNonNullOrEmpty(json.getExternalKey(), "BundleJson externalKey needs to be set");

        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        subscriptionApi.updateExternalKey(bundleId, json.getExternalKey(), callContext);
        return Response.status(Status.NO_CONTENT).build();
    }



    @TimedResource
    @POST
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add tags to bundle", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Tag created successfully"),
                           @ApiResponse(code = 400, message = "Invalid bundle id supplied")})
    public Response createBundleTags(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                                     final List<UUID> tagList,
                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                     @HeaderParam(HDR_REASON) final String reason,
                                     @HeaderParam(HDR_COMMENT) final String comment,
                                     @javax.ws.rs.core.Context final UriInfo uriInfo,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(bundleId, tagList, uriInfo,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request), request);
    }

    @TimedResource
    @DELETE
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from bundle")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid bundle id supplied")})
    public Response deleteBundleTags(@PathParam(ID_PARAM_NAME) final UUID bundleId,
                                     @QueryParam(QUERY_TAG) final List<UUID> tagList,
                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                     @HeaderParam(HDR_REASON) final String reason,
                                     @HeaderParam(HDR_COMMENT) final String comment,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(bundleId, tagList,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.BUNDLE;
    }
}
