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

import java.util.Collection;
import java.util.List;
import java.util.UUID;

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

import com.ning.billing.entitlement.api.transfer.EntitlementTransferApi;
import com.ning.billing.entitlement.api.transfer.EntitlementTransferApiException;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.jaxrs.json.BundleJsonNoSubscriptions;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.BUNDLES_PATH)
public class BundleResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "bundleId";
    private static final String CUSTOM_FIELD_URI = JaxrsResource.CUSTOM_FIELDS;
    private static final String TAG_URI = JaxrsResource.TAGS;

    private final EntitlementUserApi entitlementApi;
    private final EntitlementTransferApi transferApi;
    private final Context context;
    private final JaxrsUriBuilder uriBuilder;

    @Inject
    public BundleResource(final JaxrsUriBuilder uriBuilder,
                          final EntitlementUserApi entitlementApi,
                          final EntitlementTransferApi transferApi,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi);
        this.uriBuilder = uriBuilder;
        this.entitlementApi = entitlementApi;
        this.transferApi = transferApi;
        this.context = context;
    }

    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getBundle(@PathParam("bundleId") final String bundleId) throws EntitlementUserApiException {
        final SubscriptionBundle bundle = entitlementApi.getBundleFromId(UUID.fromString(bundleId));
        final BundleJsonNoSubscriptions json = new BundleJsonNoSubscriptions(bundle);
        return Response.status(Status.OK).entity(json).build();
    }


    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createBundle(final BundleJsonNoSubscriptions json,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment) throws EntitlementUserApiException {
        final UUID accountId = UUID.fromString(json.getAccountId());
        final SubscriptionBundle bundle = entitlementApi.createBundleForAccount(accountId, json.getExternalKey(),
                                                                                context.createContext(createdBy, reason, comment));
        return uriBuilder.buildResponse(BundleResource.class, "getBundle", bundle.getId());
    }

    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + SUBSCRIPTIONS)
    @Produces(APPLICATION_JSON)
    public Response getBundleSubscriptions(@PathParam("bundleId") final String bundleId) throws EntitlementUserApiException {
        final UUID uuid = UUID.fromString(bundleId);
        final SubscriptionBundle bundle = entitlementApi.getBundleFromId(uuid);
        if (bundle == null) {
            return Response.status(Status.NO_CONTENT).build();
        }
        final List<Subscription> bundles = entitlementApi.getSubscriptionsForBundle(uuid);
        final Collection<SubscriptionJsonNoEvents> result = Collections2.transform(bundles, new Function<Subscription, SubscriptionJsonNoEvents>() {
            @Override
            public SubscriptionJsonNoEvents apply(final Subscription input) {
                return new SubscriptionJsonNoEvents(input);
            }
        });
        return Response.status(Status.OK).entity(result).build();
    }

    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + CUSTOM_FIELD_URI)
    @Produces(APPLICATION_JSON)
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id) {
        return super.getCustomFields(UUID.fromString(id));
    }

    @POST
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + CUSTOM_FIELD_URI)
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
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + CUSTOM_FIELD_URI)
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
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + TAG_URI)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) final String id) throws TagDefinitionApiException {
        return super.getTags(UUID.fromString(id));
    }

    @PUT
    @Path("/{bundleId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response transferBundle(@PathParam(ID_PARAM_NAME) final String id,
            @QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
            @QueryParam(QUERY_BUNDLE_TRANSFER_ADDON) @DefaultValue("true") final Boolean transferAddOn,
            final BundleJsonNoSubscriptions json,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment,
            @javax.ws.rs.core.Context final UriInfo uriInfo) throws EntitlementUserApiException, EntitlementTransferApiException {

        final SubscriptionBundle bundle = entitlementApi.getBundleFromId(UUID.fromString(id));
        final DateTime inputDate = (requestedDate != null) ? DATE_TIME_FORMATTER.parseDateTime(requestedDate) : null;
        final SubscriptionBundle newBundle = transferApi.transferBundle(bundle.getAccountId(), UUID.fromString(json.getAccountId()), bundle.getKey(), inputDate, transferAddOn,
                context.createContext(createdBy, reason, comment));

        return uriBuilder.buildResponse(BundleResource.class, "getBundle", newBundle.getId(), uriInfo.getBaseUri().toString());
    }

    @POST
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + TAG_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final UriInfo uriInfo) throws TagApiException {
        return super.createTags(UUID.fromString(id), tagList, uriInfo,
                                context.createContext(createdBy, reason, comment));
    }

    @DELETE
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + TAG_URI)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment) throws TagApiException {
        return super.deleteTags(UUID.fromString(id), tagList,
                                context.createContext(createdBy, reason, comment));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.BUNDLE;
    }
}
