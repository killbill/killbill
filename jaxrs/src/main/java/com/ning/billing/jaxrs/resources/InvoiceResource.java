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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

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

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.jaxrs.json.CustomFieldJson;
import com.ning.billing.jaxrs.json.InvoiceItemJsonSimple;
import com.ning.billing.jaxrs.json.InvoiceJsonSimple;
import com.ning.billing.jaxrs.json.InvoiceJsonWithItems;
import com.ning.billing.jaxrs.json.PaymentJsonSimple;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.ObjectType;

import com.google.common.base.Objects;
import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_HTML;

@Path(JaxrsResource.INVOICES_PATH)
public class InvoiceResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(InvoiceResource.class);
    private static final String ID_PARAM_NAME = "invoiceId";
    private static final String CUSTOM_FIELD_URI = JaxrsResource.CUSTOM_FIELDS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";
    private static final String TAG_URI = JaxrsResource.TAGS + "/{" + ID_PARAM_NAME + ":" + UUID_PATTERN + "}";

    private final AccountUserApi accountApi;
    private final InvoiceUserApi invoiceApi;
    private final PaymentApi paymentApi;
    private final Context context;
    private final JaxrsUriBuilder uriBuilder;
    private final InvoiceNotifier invoiceNotifier;
    private final Clock clock;

    @Inject
    public InvoiceResource(final AccountUserApi accountApi,
                           final InvoiceUserApi invoiceApi,
                           final PaymentApi paymentApi,
                           final Context context,
                           final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final InvoiceNotifier invoiceNotifier,
                           final Clock clock) {
        super(uriBuilder, tagUserApi, customFieldUserApi);
        this.accountApi = accountApi;
        this.invoiceApi = invoiceApi;
        this.paymentApi = paymentApi;
        this.context = context;
        this.uriBuilder = uriBuilder;
        this.invoiceNotifier = invoiceNotifier;
        this.clock = clock;
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getInvoices(@QueryParam(QUERY_ACCOUNT_ID) final String accountId,
                                @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems) throws AccountApiException {
        // Verify the account exists
        accountApi.getAccountById(UUID.fromString(accountId));

        final List<Invoice> invoices = invoiceApi.getInvoicesByAccount(UUID.fromString(accountId));
        if (withItems) {
            final List<InvoiceJsonWithItems> result = new LinkedList<InvoiceJsonWithItems>();
            for (final Invoice invoice : invoices) {
                result.add(new InvoiceJsonWithItems(invoice));
            }

            return Response.status(Status.OK).entity(result).build();
        } else {
            final List<InvoiceJsonSimple> result = new LinkedList<InvoiceJsonSimple>();
            for (final Invoice invoice : invoices) {
                result.add(new InvoiceJsonSimple(invoice));
            }

            return Response.status(Status.OK).entity(result).build();
        }
    }

    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    public Response getInvoice(@PathParam("invoiceId") final String invoiceId,
                               @QueryParam(QUERY_INVOICE_WITH_ITEMS) @DefaultValue("false") final boolean withItems) throws InvoiceApiException {
        final Invoice invoice = invoiceApi.getInvoice(UUID.fromString(invoiceId));
        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND);
        } else {
            final InvoiceJsonSimple json = withItems ? new InvoiceJsonWithItems(invoice) : new InvoiceJsonSimple(invoice);
            return Response.status(Status.OK).entity(json).build();
        }
    }

    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/html")
    @Produces(TEXT_HTML)
    public Response getInvoiceAsHTML(@PathParam("invoiceId") final String invoiceId) throws InvoiceApiException, IOException, AccountApiException {
        return Response.status(Status.OK).entity(invoiceApi.getInvoiceAsHTML(UUID.fromString(invoiceId))).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createFutureInvoice(@QueryParam(QUERY_ACCOUNT_ID) final String accountId,
                                        @QueryParam(QUERY_TARGET_DATE) final String targetDateTime,
                                        @QueryParam(QUERY_DRY_RUN) @DefaultValue("false") final Boolean dryRun,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment) throws AccountApiException, InvoiceApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(accountId));

        final DateTime inputDateTime = DATE_TIME_FORMATTER.parseDateTime(targetDateTime);
        final LocalDate inputDate = inputDateTime.toDateTime(account.getTimeZone()).toLocalDate();

        final Invoice generatedInvoice = invoiceApi.triggerInvoiceGeneration(UUID.fromString(accountId), inputDate, dryRun,
                                                                             context.createContext(createdBy, reason, comment));
        if (dryRun) {
            return Response.status(Status.OK).entity(new InvoiceJsonSimple(generatedInvoice)).build();
        } else {
            return uriBuilder.buildResponse(InvoiceResource.class, "getInvoice", generatedInvoice.getId());
        }
    }

    @POST
    @Path("/{invoiceId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response adjustInvoiceItem(final InvoiceItemJsonSimple json,
                                      @PathParam("invoiceId") final String invoiceId,
                                      @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment) throws AccountApiException, InvoiceApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(json.getAccountId()));

        // Get the effective date of the adjustment, in the account timezone
        final LocalDate requestedDate;
        if (requestedDateTimeString == null) {
            requestedDate = clock.getUTCToday();
        } else {
            final DateTime requestedDateTime = DATE_TIME_FORMATTER.parseDateTime(requestedDateTimeString);
            requestedDate = requestedDateTime.toDateTime(account.getTimeZone()).toLocalDate();
        }

        final InvoiceItem adjustmentItem;
        if (json.getAmount() == null) {
            adjustmentItem = invoiceApi.insertInvoiceItemAdjustment(account.getId(),
                                                                    UUID.fromString(invoiceId),
                                                                    UUID.fromString(json.getInvoiceItemId()),
                                                                    requestedDate,
                                                                    context.createContext(createdBy, reason, comment));
        } else {
            adjustmentItem = invoiceApi.insertInvoiceItemAdjustment(account.getId(),
                                                                    UUID.fromString(invoiceId),
                                                                    UUID.fromString(json.getInvoiceItemId()),
                                                                    requestedDate,
                                                                    json.getAmount(),
                                                                    json.getCurrency(),
                                                                    context.createContext(createdBy, reason, comment));
        }

        return uriBuilder.buildResponse(InvoiceResource.class, "getInvoice", adjustmentItem.getInvoiceId());
    }

    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path(CHARGES)
    public Response createExternalCharge(final InvoiceItemJsonSimple externalChargeJson,
                                         @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                         @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                         @HeaderParam(HDR_REASON) final String reason,
                                         @HeaderParam(HDR_COMMENT) final String comment,
                                         @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(externalChargeJson.getAccountId()));
        final CallContext callContext = context.createContext(createdBy, reason, comment);

        // Get the effective date of the external charge, in the account timezone
        final LocalDate requestedDate;
        if (requestedDateTimeString == null) {
            requestedDate = clock.getUTCToday();
        } else {
            final DateTime requestedDateTime = DATE_TIME_FORMATTER.parseDateTime(requestedDateTimeString);
            requestedDate = requestedDateTime.toDateTime(account.getTimeZone()).toLocalDate();
        }

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

        return uriBuilder.buildResponse(InvoiceResource.class, "getInvoice", externalCharge.getInvoiceId(), uriInfo.getBaseUri().toString());
    }

    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + CHARGES)
    public Response createExternalChargeForInvoice(final InvoiceItemJsonSimple externalChargeJson,
                                                   @PathParam("invoiceId") final String invoiceIdString,
                                                   @QueryParam(QUERY_REQUESTED_DT) final String requestedDateTimeString,
                                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                   @HeaderParam(HDR_REASON) final String reason,
                                                   @HeaderParam(HDR_COMMENT) final String comment,
                                                   @javax.ws.rs.core.Context final UriInfo uriInfo) throws AccountApiException, InvoiceApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(externalChargeJson.getAccountId()));
        final CallContext callContext = context.createContext(createdBy, reason, comment);

        // Get the effective date of the external charge, in the account timezone
        final LocalDate requestedDate;
        if (requestedDateTimeString == null) {
            requestedDate = clock.getUTCToday();
        } else {
            final DateTime requestedDateTime = DATE_TIME_FORMATTER.parseDateTime(requestedDateTimeString);
            requestedDate = requestedDateTime.toDateTime(account.getTimeZone()).toLocalDate();
        }

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

        return uriBuilder.buildResponse(InvoiceResource.class, "getInvoice", externalCharge.getInvoiceId(), uriInfo.getBaseUri().toString());
    }

    @GET
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + PAYMENTS)
    @Produces(APPLICATION_JSON)
    public Response getPayments(@PathParam("invoiceId") final String invoiceId) throws PaymentApiException {
        final List<Payment> payments = paymentApi.getInvoicePayments(UUID.fromString(invoiceId));

        final List<PaymentJsonSimple> result = new ArrayList<PaymentJsonSimple>(payments.size());
        for (final Payment cur : payments) {
            result.add(new PaymentJsonSimple(cur));
        }

        return Response.status(Status.OK).entity(result).build();
    }

    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @Path("/{invoiceId:" + UUID_PATTERN + "}/" + PAYMENTS)
    public Response createInstantPayment(final PaymentJsonSimple payment,
                                         @QueryParam(QUERY_PAYMENT_EXTERNAL) @DefaultValue("false") final Boolean externalPayment,
                                         @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                         @HeaderParam(HDR_REASON) final String reason,
                                         @HeaderParam(HDR_COMMENT) final String comment) throws AccountApiException, PaymentApiException {
        final Account account = accountApi.getAccountById(UUID.fromString(payment.getAccountId()));

        final UUID invoiceId = UUID.fromString(payment.getInvoiceId());
        final CallContext callContext = context.createContext(createdBy, reason, comment);
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
                                                       @HeaderParam(HDR_COMMENT) final String comment) throws InvoiceApiException, AccountApiException {
        final Invoice invoice = invoiceApi.getInvoice(UUID.fromString(invoiceId));
        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
        }

        final Account account = accountApi.getAccountById(invoice.getAccountId());

        // Send the email (synchronous send)
        invoiceNotifier.notify(account, invoice);

        return Response.status(Status.OK).build();
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
    public Response getTags(@PathParam(ID_PARAM_NAME) final String id, @QueryParam(QUERY_AUDIT) @DefaultValue("false") final Boolean withAudit) throws TagDefinitionApiException {
        return super.getTags(UUID.fromString(id), withAudit);
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
                               @javax.ws.rs.core.Context final UriInfo uriInfo) throws TagApiException {
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
                               @HeaderParam(HDR_COMMENT) final String comment) throws TagApiException {
        return super.deleteTags(UUID.fromString(id), tagList,
                                context.createContext(createdBy, reason, comment));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.INVOICE;
    }
}
