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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
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

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.clock.Clock;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.InvoiceItemJson;
import com.ning.billing.jaxrs.json.InvoiceJson;
import com.ning.billing.jaxrs.json.PaymentJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.audit.AccountAuditLogs;
import com.ning.billing.util.audit.AccountAuditLogsForObjectType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.base.Objects;
import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_HTML;

@Path(JaxrsResource.INVOICES_PATH)
public class InvoiceResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(InvoiceResource.class);
    private static final String ID_PARAM_NAME = "invoiceId";

    private final InvoiceUserApi invoiceApi;
    private final PaymentApi paymentApi;
    private final InvoiceNotifier invoiceNotifier;

    @Inject
    public InvoiceResource(final AccountUserApi accountUserApi,
                           final InvoiceUserApi invoiceApi,
                           final PaymentApi paymentApi,
                           final InvoiceNotifier invoiceNotifier,
                           final Clock clock,
                           final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.invoiceApi = invoiceApi;
        this.paymentApi = paymentApi;
        this.invoiceNotifier = invoiceNotifier;
    }

    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    public Response getInvoice(@PathParam("invoiceId") final String invoiceId,
                               @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createContext(request);
        final Invoice invoice = invoiceApi.getInvoice(UUID.fromString(invoiceId), tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext);

        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
        } else {
            final InvoiceJson json = new InvoiceJson(invoice, withItems, accountAuditLogs);
            return Response.status(Status.OK).entity(json).build();
        }
    }

    @GET
    @Path("/{invoiceNumber:" + NUMBER_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    public Response getInvoiceByNumber(@PathParam("invoiceNumber") final Integer invoiceNumber,
                                       @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems,
                                       @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException {
        final TenantContext tenantContext = context.createContext(request);
        final Invoice invoice = invoiceApi.getInvoiceByNumber(invoiceNumber, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(invoice.getAccountId(), auditMode.getLevel(), tenantContext);

        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceNumber);
        } else {
            final InvoiceJson json = new InvoiceJson(invoice, withItems, accountAuditLogs);
            return Response.status(Status.OK).entity(json).build();
        }
    }

    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/html")
    @Produces(TEXT_HTML)
    public Response getInvoiceAsHTML(@PathParam("invoiceId") final String invoiceId,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, IOException, AccountApiException {
        return Response.status(Status.OK).entity(invoiceApi.getInvoiceAsHTML(UUID.fromString(invoiceId), context.createContext(request))).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createFutureInvoice(@QueryParam(QUERY_ACCOUNT_ID) final String accountId,
                                        @QueryParam(QUERY_TARGET_DATE) final String targetDateTime,
                                        @QueryParam(QUERY_DRY_RUN) @DefaultValue("false") final Boolean dryRun,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final LocalDate inputDate = toLocalDate(UUID.fromString(accountId), targetDateTime, callContext);

        final Invoice generatedInvoice = invoiceApi.triggerInvoiceGeneration(UUID.fromString(accountId), inputDate, dryRun,
                                                                             callContext);
        if (dryRun) {
            return Response.status(Status.OK).entity(new InvoiceJson(generatedInvoice)).build();
        } else {
            return uriBuilder.buildResponse(InvoiceResource.class, "getInvoice", generatedInvoice.getId());
        }
    }

    @DELETE
    @Path("/{invoiceId:" + UUID_PATTERN + "}" + "/{invoiceItemId:" + UUID_PATTERN + "}/cba")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response deleteCBA(@PathParam("invoiceId") final String invoiceId,
                              @PathParam("invoiceItemId") final String invoiceItemId,
                              @QueryParam(QUERY_ACCOUNT_ID) final String accountId,
                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                              @HeaderParam(HDR_REASON) final String reason,
                              @HeaderParam(HDR_COMMENT) final String comment,
                              @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), callContext);

        invoiceApi.deleteCBA(account.getId(), UUID.fromString(invoiceId), UUID.fromString(invoiceItemId), callContext);

        return Response.status(Status.OK).build();
    }

    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response adjustInvoiceItem(final InvoiceItemJson json,
                                      @PathParam("invoiceId") final String invoiceId,
                                      @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final UUID accountId = UUID.fromString(json.getAccountId());
        final LocalDate requestedDate = toLocalDate(accountId, requestedDateTimeString, callContext);
        final InvoiceItem adjustmentItem;
        if (json.getAmount() == null) {
            adjustmentItem = invoiceApi.insertInvoiceItemAdjustment(accountId,
                                                                    UUID.fromString(invoiceId),
                                                                    UUID.fromString(json.getInvoiceItemId()),
                                                                    requestedDate,
                                                                    callContext);
        } else {
            adjustmentItem = invoiceApi.insertInvoiceItemAdjustment(accountId,
                                                                    UUID.fromString(invoiceId),
                                                                    UUID.fromString(json.getInvoiceItemId()),
                                                                    requestedDate,
                                                                    json.getAmount(),
                                                                    json.getCurrency(),
                                                                    callContext);
        }

        return uriBuilder.buildResponse(InvoiceResource.class, "getInvoice", adjustmentItem.getInvoiceId());
    }

    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/" + CHARGES)
    public Response createExternalCharge(final InvoiceItemJson externalChargeJson,
                                         @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                         @QueryParam(QUERY_PAY_INVOICE) @DefaultValue("false") final Boolean payInvoice,
                                         @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                         @HeaderParam(HDR_REASON) final String reason,
                                         @HeaderParam(HDR_COMMENT) final String comment,
                                         @javax.ws.rs.core.Context final UriInfo uriInfo,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException, PaymentApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(externalChargeJson.getAccountId()), callContext);

        // Get the effective date of the external charge, in the account timezone
        final LocalDate requestedDate = toLocalDate(account, requestedDateTimeString, callContext);

        final Currency currency = Objects.firstNonNull(externalChargeJson.getCurrency(), account.getCurrency());
        final InvoiceItem externalCharge;
        if (externalChargeJson.getBundleId() != null) {
            externalCharge = invoiceApi.insertExternalChargeForBundle(account.getId(), UUID.fromString(externalChargeJson.getBundleId()),
                                                                      externalChargeJson.getAmount(), externalChargeJson.getDescription(),
                                                                      requestedDate, currency, callContext);
        } else {
            externalCharge = invoiceApi.insertExternalCharge(account.getId(), externalChargeJson.getAmount(),
                                                             externalChargeJson.getDescription(), requestedDate,
                                                             currency, callContext);
        }

        if (payInvoice) {
            final Invoice invoice = invoiceApi.getInvoice(externalCharge.getInvoiceId(), callContext);
            paymentApi.createPayment(account, invoice.getId(), invoice.getBalance(), callContext);
        }
        return uriBuilder.buildResponse(InvoiceResource.class, "getInvoice", externalCharge.getInvoiceId(), uriInfo.getBaseUri().toString());
    }

    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CHARGES)
    public Response createExternalChargeForInvoice(final InvoiceItemJson externalChargeJson,
                                                   @PathParam("invoiceId") final String invoiceIdString,
                                                   @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                                   @QueryParam(QUERY_PAY_INVOICE) @DefaultValue("false") final Boolean payInvoice,
                                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                   @HeaderParam(HDR_REASON) final String reason,
                                                   @HeaderParam(HDR_COMMENT) final String comment,
                                                   @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, InvoiceApiException, PaymentApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(externalChargeJson.getAccountId()), callContext);

        // Get the effective date of the external charge, in the account timezone
        final LocalDate requestedDate = toLocalDate(account, requestedDateTimeString, callContext);

        final UUID invoiceId = UUID.fromString(invoiceIdString);
        final Currency currency = Objects.firstNonNull(externalChargeJson.getCurrency(), account.getCurrency());
        final InvoiceItem externalCharge;
        if (externalChargeJson.getBundleId() != null) {
            externalCharge = invoiceApi.insertExternalChargeForInvoiceAndBundle(account.getId(), invoiceId, UUID.fromString(externalChargeJson.getBundleId()),
                                                                                externalChargeJson.getAmount(), externalChargeJson.getDescription(),
                                                                                requestedDate, currency, callContext);
        } else {
            externalCharge = invoiceApi.insertExternalChargeForInvoice(account.getId(), invoiceId,
                                                                       externalChargeJson.getAmount(), externalChargeJson.getDescription(),
                                                                       requestedDate, currency, callContext);
        }

        if (payInvoice) {
            final Invoice invoice = invoiceApi.getInvoice(externalCharge.getInvoiceId(), callContext);
            paymentApi.createPayment(account, invoice.getId(), invoice.getBalance(), callContext);
        }

        return uriBuilder.buildResponse(InvoiceResource.class, "getInvoice", externalCharge.getInvoiceId(), uriInfo.getBaseUri().toString());
    }

    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @Produces(APPLICATION_JSON)
    public Response getPayments(@PathParam("invoiceId") final String invoiceId,
                                @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final TenantContext tenantContext = context.createContext(request);
        final List<Payment> payments = paymentApi.getInvoicePayments(UUID.fromString(invoiceId), tenantContext);
        final List<PaymentJson> result = new ArrayList<PaymentJson>(payments.size());
        if (payments.size() == 0) {
            return Response.status(Status.OK).entity(result).build();
        }

        final AccountAuditLogsForObjectType auditLogsForPayments = auditUserApi.getAccountAuditLogs(payments.get(0).getAccountId(),
                                                                                                    ObjectType.PAYMENT,
                                                                                                    auditMode.getLevel(),
                                                                                                    tenantContext);

        for (final Payment cur : payments) {
            result.add(new PaymentJson(cur, auditLogsForPayments.getAuditLogs(cur.getId())));
        }

        return Response.status(Status.OK).entity(result).build();
    }

    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + PAYMENTS)
    public Response createInstantPayment(final PaymentJson payment,
                                         @QueryParam(QUERY_PAYMENT_EXTERNAL) @DefaultValue("false") final Boolean externalPayment,
                                         @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                         @HeaderParam(HDR_REASON) final String reason,
                                         @HeaderParam(HDR_COMMENT) final String comment,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(payment.getAccountId()), callContext);

        final UUID invoiceId = UUID.fromString(payment.getInvoiceId());
        if (externalPayment) {
            paymentApi.createExternalPayment(account, invoiceId, payment.getAmount(), callContext);
        } else {
            paymentApi.createPayment(account, invoiceId, payment.getAmount(), callContext);
        }

        return uriBuilder.buildResponse(InvoiceResource.class, "getPayments", payment.getInvoiceId());
    }

    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + EMAIL_NOTIFICATIONS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response triggerEmailNotificationForInvoice(@PathParam("invoiceId") final String invoiceId,
                                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                       @HeaderParam(HDR_REASON) final String reason,
                                                       @HeaderParam(HDR_COMMENT) final String comment,
                                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws InvoiceApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Invoice invoice = invoiceApi.getInvoice(UUID.fromString(invoiceId), callContext);
        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
        }

        final Account account = accountUserApi.getAccountById(invoice.getAccountId(), callContext);

        // Send the email (synchronous send)
        invoiceNotifier.notify(account, invoice, callContext);

        return Response.status(Status.OK).build();
    }

    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), auditMode, context.createContext(request));
    }

    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
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
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
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
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    public Response getTags(@PathParam(ID_PARAM_NAME) final String invoiceIdString,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, InvoiceApiException {
        final UUID invoiceId = UUID.fromString(invoiceIdString);
        final TenantContext tenantContext = context.createContext(request);
        final Invoice invoice = invoiceApi.getInvoice(invoiceId, tenantContext);
        return super.getTags(invoice.getAccountId(), invoiceId, auditMode, includedDeleted, tenantContext);
    }

    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
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
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + TAGS)
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
        return ObjectType.INVOICE;
    }
}
