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

import com.google.inject.Inject;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.jaxrs.util.TagHelper;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;

import java.util.List;
import java.util.UUID;

@Path("/1.0/payment")
public class PaymentResource extends JaxRsResourceBase {
    private static final String ID_PARAM_NAME = "paymentId";
    private static final String CUSTOM_FIELD_URI = JaxrsResource.CUSTOM_FIELDS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";
    private static final String TAG_URI = JaxrsResource.TAGS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";

    private final Context context;

    @Inject
    public PaymentResource(final JaxrsUriBuilder uriBuilder, final TagUserApi tagUserApi,
                           final TagHelper tagHelper, final CustomFieldUserApi customFieldUserApi,
                           final Context context) {
        super(uriBuilder, tagUserApi, tagHelper, customFieldUserApi);
        this.context = context;
    }

    @GET
    @Path("/{invoiceId:\\w+-\\w+-\\w+-\\w+-\\w+}")
    @Produces(APPLICATION_JSON)
    public Response getPayments(@PathParam("invoiceId") String invoiceId) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @GET
    @Path("/account/{accountId:\\w+-\\w+-\\w+-\\w+-\\w+}")
    @Produces(APPLICATION_JSON)
    public Response getAllPayments(@PathParam("accountId") String accountId) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{invoiceId:\\w+-\\w+-\\w+-\\w+-\\w+}")
    public Response createInstantPayment(PaymentJsonSimple payment,
            @PathParam("invoiceId") String invoiceId,
            @QueryParam("last4CC") String last4CC,
            @QueryParam("nameOnCC") String nameOnCC) {
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
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
        return ObjectType.PAYMENT;
    }
}
