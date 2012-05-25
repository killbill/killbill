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

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.jaxrs.json.BundleJsonNoSubscriptions;
import com.ning.billing.jaxrs.json.SubscriptionJsonNoEvents;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;

@Path(JaxrsResource.BUNDLES_PATH)
public class BundleResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(BundleResource.class);
    private static final String ID_PARAM_NAME = "bundleId";
    private static final String CUSTOM_FIELD_URI = JaxrsResource.CUSTOM_FIELDS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";
    private static final String TAG_URI = JaxrsResource.TAGS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";

    private final EntitlementUserApi entitlementApi;
    private final Context context;
    private final JaxrsUriBuilder uriBuilder;

    @Inject
    public BundleResource(final JaxrsUriBuilder uriBuilder, final EntitlementUserApi entitlementApi,
                          TagUserApi tagUserApi, TagHelper tagHelper, CustomFieldUserApi customFieldUserApi,
                          Context context) {
        super(uriBuilder, tagUserApi, tagHelper, customFieldUserApi);
        this.uriBuilder = uriBuilder;
        this.entitlementApi = entitlementApi;
        this.context = context;
    }

    @GET
    @Path("/{bundleId:"  + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getBundle(@PathParam("bundleId") final String bundleId) throws EntitlementUserApiException {
        try {
            SubscriptionBundle bundle = entitlementApi.getBundleFromId(UUID.fromString(bundleId));
            BundleJsonNoSubscriptions json = new BundleJsonNoSubscriptions(bundle);
            return Response.status(Status.OK).entity(json).build();
        } catch (EntitlementUserApiException e) {
            if (e.getCode() == ErrorCode.ENT_GET_INVALID_BUNDLE_ID.getCode()) {
                return Response.status(Status.NO_CONTENT).build();
            } else {
                throw e;
            }

        }
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getBundleByKey(@QueryParam(QUERY_EXTERNAL_KEY) final String externalKey) throws EntitlementUserApiException {
        try {
            SubscriptionBundle bundle = entitlementApi.getBundleForKey(externalKey);
            BundleJsonNoSubscriptions json = new BundleJsonNoSubscriptions(bundle);
            return Response.status(Status.OK).entity(json).build();
        } catch (EntitlementUserApiException e) {
            if (e.getCode() == ErrorCode.ENT_GET_INVALID_BUNDLE_KEY.getCode()) {
                return Response.status(Status.NO_CONTENT).build();
            } else {
                throw e;
            }

        }
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createBundle(final BundleJsonNoSubscriptions json,
            @HeaderParam(HDR_CREATED_BY) final String createdBy,
            @HeaderParam(HDR_REASON) final String reason,
            @HeaderParam(HDR_COMMENT) final String comment) {
        try {
            UUID accountId = UUID.fromString(json.getAccountId());
            final SubscriptionBundle bundle = entitlementApi.createBundleForAccount(accountId, json.getExternalKey(),
                    context.createContext(createdBy, reason, comment));
            return uriBuilder.buildResponse(BundleResource.class, "getBundle", bundle.getId());
        } catch (EntitlementUserApiException e) {
            log.info(String.format("Failed to create bundle %s", json), e);
            return Response.status(Status.BAD_REQUEST).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{bundleId:" + UUID_PATTERN + "}/" + SUBSCRIPTIONS)
    @Produces(APPLICATION_JSON)
    public Response getBundleSubscriptions(@PathParam("bundleId") final String bundleId) throws EntitlementUserApiException {
        try {
            UUID uuid = UUID.fromString(bundleId);
            SubscriptionBundle bundle = entitlementApi.getBundleFromId(uuid);
            if (bundle == null) {
                return Response.status(Status.NO_CONTENT).build();
            }
            List<Subscription> bundles = entitlementApi.getSubscriptionsForBundle(uuid);
            Collection<SubscriptionJsonNoEvents> result =  Collections2.transform(bundles, new Function<Subscription, SubscriptionJsonNoEvents>() {
                @Override
                public SubscriptionJsonNoEvents apply(Subscription input) {
                    return new SubscriptionJsonNoEvents(input);
                }
             });
            return Response.status(Status.OK).entity(result).build();
        } catch (EntitlementUserApiException e) {
            if (e.getCode() == ErrorCode.ENT_GET_INVALID_BUNDLE_ID.getCode()) {
                return Response.status(Status.NO_CONTENT).build();
            } else {
                throw e;
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
        return ObjectType.BUNDLE;
    }
}
