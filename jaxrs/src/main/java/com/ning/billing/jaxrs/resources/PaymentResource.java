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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.clock.Clock;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.jaxrs.json.ChargebackCollectionJson;
import com.ning.billing.jaxrs.json.ChargebackJson;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.InvoiceItemJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.json.PaymentJsonWithBundleKeys;
import com.ning.billing.jaxrs.json.RefundJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.PAYMENTS_PATH)
public class PaymentResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "paymentId";

    private final PaymentApi paymentApi;
    private final InvoicePaymentApi invoicePaymentApi;

    @Inject
    public PaymentResource(final AccountUserApi accountUserApi,
                           final PaymentApi paymentApi,
                           final InvoicePaymentApi invoicePaymentApi,
                           final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final Clock clock,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.paymentApi = paymentApi;
        this.invoicePaymentApi = invoicePaymentApi;
    }

    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getPayment(@PathParam(ID_PARAM_NAME) final String paymentIdString,
                               @QueryParam(QUERY_PAYMENT_WITH_REFUNDS_AND_CHARGEBACKS) @DefaultValue("false") final Boolean withRefundsAndChargebacks,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final TenantContext tenantContext = context.createContext(request);

        final UUID paymentId = UUID.fromString(paymentIdString);
        final Payment payment = paymentApi.getPayment(paymentId, false, tenantContext);

        final PaymentJsonSimple paymentJsonSimple;
        if (withRefundsAndChargebacks) {
            final List<RefundJson> refunds = new ArrayList<RefundJson>();
            for (final Refund refund : paymentApi.getPaymentRefunds(paymentId, tenantContext)) {
                refunds.add(new RefundJson(refund));
            }

            final List<ChargebackJson> chargebacks = new ArrayList<ChargebackJson>();
            for (final InvoicePayment chargeback : invoicePaymentApi.getChargebacksByPaymentId(paymentId, tenantContext)) {
                chargebacks.add(new ChargebackJson(chargeback));
            }

            final int nbOfPaymentAttempts = payment.getAttempts().size();
            final String status = payment.getPaymentStatus().toString();
            paymentJsonSimple = new PaymentJsonWithBundleKeys(payment,
                                                              status,
                                                              nbOfPaymentAttempts,
                                                              null, // TODO - the keys are really only used for the timeline
                                                              payment.getAccountId(),
                                                              refunds,
                                                              chargebacks);
        } else {
            paymentJsonSimple = new PaymentJsonSimple(payment);
        }

        return Response.status(Status.OK).entity(paymentJsonSimple).build();
    }

    @PUT
    @Path("/{paymentId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response retryFailedPayment(@PathParam(ID_PARAM_NAME) final String paymentIdString,
                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                   @HeaderParam(HDR_REASON) final String reason,
                                   @HeaderParam(HDR_COMMENT) final String comment,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID paymentId = UUID.fromString(paymentIdString);
        final Payment payment = paymentApi.getPayment(paymentId, false, callContext);
        final Account account = accountUserApi.getAccountById(payment.getAccountId(), callContext);
        final Payment newPayment = paymentApi.retryPayment(account, paymentId, callContext);

        return Response.status(Status.OK).entity(new PaymentJsonSimple(newPayment)).build();
    }



    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CHARGEBACKS)
    @Produces(APPLICATION_JSON)
    public Response getChargebacksForPayment(@PathParam("paymentId") final String paymentId,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createContext(request);

        final List<InvoicePayment> chargebacks = invoicePaymentApi.getChargebacksByPaymentId(UUID.fromString(paymentId), tenantContext);
        if (chargebacks.size() == 0) {
            return Response.status(Response.Status.NO_CONTENT).build();
        }

        final UUID invoicePaymentId = chargebacks.get(0).getId();
        final String accountId = invoicePaymentApi.getAccountIdFromInvoicePaymentId(invoicePaymentId, tenantContext).toString();
        final List<ChargebackJson> chargebacksJson = new ArrayList<ChargebackJson>();
        for (final InvoicePayment chargeback : chargebacks) {
            chargebacksJson.add(new ChargebackJson(chargeback));
        }
        final ChargebackCollectionJson json = new ChargebackCollectionJson(accountId, chargebacksJson);

        return Response.status(Response.Status.OK).entity(json).build();
    }


    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Produces(APPLICATION_JSON)
    public Response getRefunds(@PathParam("paymentId") final String paymentId,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final List<Refund> refunds = paymentApi.getPaymentRefunds(UUID.fromString(paymentId), context.createContext(request));
        final List<RefundJson> result = new ArrayList<RefundJson>(Collections2.transform(refunds, new Function<Refund, RefundJson>() {
            @Override
            public RefundJson apply(final Refund input) {
                // TODO Return adjusted items and audits
                return new RefundJson(input, null, null);
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
                                 @javax.ws.rs.core.Context final UriInfo uriInfo,
                                 @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID paymentUuid = UUID.fromString(paymentId);
        final Payment payment = paymentApi.getPayment(paymentUuid, false, callContext);
        final Account account = accountUserApi.getAccountById(payment.getAccountId(), callContext);

        final Refund result;
        if (json.isAdjusted()) {
            if (json.getAdjustments() != null && json.getAdjustments().size() > 0) {
                final Map<UUID, BigDecimal> adjustments = new HashMap<UUID, BigDecimal>();
                for (final InvoiceItemJsonSimple item : json.getAdjustments()) {
                    adjustments.put(UUID.fromString(item.getInvoiceItemId()), item.getAmount());
                }
                result = paymentApi.createRefundWithItemsAdjustments(account, paymentUuid, adjustments, callContext);
            } else {
                // Invoice adjustment
                result = paymentApi.createRefundWithAdjustment(account, paymentUuid, json.getAmount(), callContext);
            }
        } else {
            // Refund without adjustment
            result = paymentApi.createRefund(account, paymentUuid, json.getAmount(), callContext);
        }

        return uriBuilder.buildResponse(RefundResource.class, "getRefund", result.getId(), uriInfo.getBaseUri().toString());
    }

    @GET
    @Path("/{paymentId:"  + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), auditMode, context.createContext(request));
    }

    @POST
    @Path("/{paymentId:" + UUID_PATTERN  + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       final List<CustomFieldJson> customFields,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.createCustomFields(UUID.fromString(id), customFields,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @DELETE
    @Path("/{paymentId:" + UUID_PATTERN  + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) final String id,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        return super.getTags(UUID.fromString(id), auditMode, context.createContext(request));
    }

    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + TAGS)
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
    @Path("/{paymentId:" + UUID_PATTERN + "}/"+ TAGS)
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
        return ObjectType.PAYMENT;
    }
}
