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

import java.net.URI;
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
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.clock.Clock;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.jaxrs.json.BundleJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.BUNDLES_PATH)
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
                          final Clock clock,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.entitlementApi = entitlementApi;
        this.subscriptionApi = subscriptionApi;
    }

    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getBundle(@PathParam("bundleId") final String bundleId,
                              @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        final UUID id = UUID.fromString(bundleId);
        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(id, context.createContext(request));
        final BundleJson json = new BundleJson(bundle, null);
        return Response.status(Status.OK).entity(json).build();
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getBundleByKey(@QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        final SubscriptionBundle bundle = subscriptionApi.getActiveSubscriptionBundleForExternalKey(externalKey, context.createContext(request));
        final BundleJson json = new BundleJson(bundle, null);
        return Response.status(Status.OK).entity(json).build();
    }

    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    public Response getBundles(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                               @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        final TenantContext tenantContext = context.createContext(request);
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
                                                        return new BundleJson(bundle, accountsAuditLogs.get().get(bundle.getAccountId()));
                                                    }
                                                },
                                                nextPageUri);
    }

    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response searchBundles(@PathParam("searchKey") final String searchKey,
                                  @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                  @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                  @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException {
        final TenantContext tenantContext = context.createContext(request);
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
                                                        return new BundleJson(bundle, accountsAuditLogs.get().get(bundle.getAccountId()));
                                                    }
                                                },
                                                nextPageUri);
    }

    @PUT
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + PAUSE)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response pauseBundle(@PathParam(ID_PARAM_NAME) final String id,
                                @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, EntitlementApiException {

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID bundleId = UUID.fromString(id);
        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(bundleId, callContext);
        final LocalDate inputLocalDate = toLocalDate(bundle.getAccountId(), requestedDate, callContext);
        entitlementApi.pause(bundleId, inputLocalDate, callContext);
        return Response.status(Status.OK).build();
    }

    @PUT
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + RESUME)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response resumeBundle(@PathParam(ID_PARAM_NAME) final String id,
                                 @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final HttpServletRequest request) throws SubscriptionApiException, EntitlementApiException {

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID bundleId = UUID.fromString(id);
        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(bundleId, callContext);
        final LocalDate inputLocalDate = toLocalDate(bundle.getAccountId(), requestedDate, callContext);
        entitlementApi.resume(bundleId, inputLocalDate, callContext);
        return Response.status(Status.OK).build();
    }

    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), auditMode, context.createContext(request));
    }

    @POST
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
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
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) final String bundleIdString,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, SubscriptionApiException {
        final UUID bundleId = UUID.fromString(bundleIdString);
        final TenantContext tenantContext = context.createContext(request);
        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(bundleId, context.createContext(request));
        return super.getTags(bundle.getAccountId(), bundleId, auditMode, includedDeleted, tenantContext);
    }

    @PUT
    @Path("/{bundleId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response transferBundle(final BundleJson json,
                                   @PathParam(ID_PARAM_NAME) final String id,
                                   @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                   @QueryParam(QUERY_BILLING_POLICY) @DefaultValue("END_OF_TERM") final String policyString,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @javax.ws.rs.core.Context final UriInfo uriInfo,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws EntitlementApiException, SubscriptionApiException, AccountApiException {

        final BillingActionPolicy policy = BillingActionPolicy.valueOf(policyString.toUpperCase());

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID bundleId = UUID.fromString(id);

        final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(bundleId, callContext);
        final LocalDate inputLocalDate = toLocalDate(bundle.getAccountId(), requestedDate, callContext);

        final UUID newBundleId = entitlementApi.transferEntitlementsOverrideBillingPolicy(bundle.getAccountId(), UUID.fromString(json.getAccountId()), bundle.getExternalKey(), inputLocalDate, policy, callContext);
        return uriBuilder.buildResponse(BundleResource.class, "getBundle", newBundleId, uriInfo.getBaseUri().toString());
    }

    @POST
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + TAGS)
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
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + TAGS)
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
        return ObjectType.BUNDLE;
    }
}
