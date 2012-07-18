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

import java.util.ArrayList;
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
import javax.ws.rs.core.UriInfo;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.RefundJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.PAYMENTS_PATH)
public class PaymentResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "paymentId";
    private static final String CUSTOM_FIELD_URI = JaxrsResource.CUSTOM_FIELDS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";
    private static final String TAG_URI = JaxrsResource.TAGS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";

    private final Context context;
    private final PaymentApi paymentApi;
    private final AccountUserApi accountApi;

    @Inject
    public PaymentResource(final JaxrsUriBuilder uriBuilder,
                           final AccountUserApi accountApi,
                           final PaymentApi paymentApi,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi);
        this.context = context;
        this.paymentApi = paymentApi;
        this.accountApi = accountApi;
    }

    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Produces(APPLICATION_JSON)
    public Response getRefunds(@PathParam("paymentId") final String paymentId) throws PaymentApiException {
        final List<Refund> refunds = paymentApi.getPaymentRefunds(UUID.fromString(paymentId));
        final List<RefundJson> result = new ArrayList<RefundJson>(Collections2.transform(refunds, new Function<Refund, RefundJson>() {
            @Override
            public RefundJson apply(final Refund input) {
                return new RefundJson(input);
            }
        }));

        return Response.status(Status.OK).entity(result).build();
    }

    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createRefund(final RefundJson json,
                                 @PathParam("paymentId") final String paymentId,
                                 @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                 @HeaderParam(HDR_REASON) final String reason,
                                 @HeaderParam(HDR_COMMENT) final String comment,
                                 @javax.ws.rs.core.Context final UriInfo uriInfo) throws PaymentApiException, AccountApiException {
        final UUID paymentUuid = UUID.fromString(paymentId);
        final Payment payment = paymentApi.getPayment(paymentUuid);
        final Account account = accountApi.getAccountById(payment.getAccountId());

        final Refund result = paymentApi.createRefund(account, paymentUuid, json.getRefundAmount(), json.isAdjusted(), context.createContext(createdBy, reason, comment));

        return uriBuilder.buildResponse(RefundResource.class, "getRefund", result.getId(), uriInfo.getBaseUri().toString());
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
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) {
        return super.createCustomFields(UUID.fromString(id), customFields, uriInfo,
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
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList, uriInfo,
                                        context.createContext(createdBy, reason, comment));
    }

    @GET
    @Path(TAG_URI)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) final String id) {
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
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final UriInfo uriInfo) {
        return super.createTags(UUID.fromString(id), tagList, uriInfo,
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
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final UriInfo uriInfo) {
        return super.deleteTags(UUID.fromString(id), tagList, uriInfo,
                                context.createContext(createdBy, reason, comment));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.PAYMENT;
    }
}
