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

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.InvoiceItemJson;
import org.killbill.billing.jaxrs.json.InvoicePaymentJson;
import org.killbill.billing.jaxrs.json.InvoicePaymentTransactionJson;
import org.killbill.billing.jaxrs.json.PaymentTransactionJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.util.UUIDs;
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
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.INVOICE_PAYMENTS_PATH)
@Api(value = JaxrsResource.INVOICE_PAYMENTS_PATH, description = "Operations on invoice payments", tags = "InvoicePayment")
public class InvoicePaymentResource extends JaxRsResourceBase {

    private static final String ID_PARAM_NAME = "paymentId";

    private final InvoicePaymentApi invoicePaymentApi;

    @Inject
    public InvoicePaymentResource(final AccountUserApi accountUserApi,
                                  final PaymentApi paymentApi,
                                  final JaxrsUriBuilder uriBuilder,
                                  final TagUserApi tagUserApi,
                                  final CustomFieldUserApi customFieldUserApi,
                                  final AuditUserApi auditUserApi,
                                  final InvoicePaymentApi invoicePaymentApi,
                                  final Clock clock,
                                  final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
        this.invoicePaymentApi = invoicePaymentApi;
    }

    @TimedResource
    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a payment by id", response = InvoicePaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid payment id supplied"),
                           @ApiResponse(code = 404, message = "Payment not found")})
    public Response getInvoicePayment(@PathParam("paymentId") final UUID paymentId,
                                      @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                      @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                      @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Payment payment = paymentApi.getPayment(paymentId, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(payment.getAccountId(), auditMode.getLevel(), tenantContext);

        final List<InvoicePayment> invoicePayments = invoicePaymentApi.getInvoicePayments(paymentId, tenantContext);
        final InvoicePayment invoicePayment = Iterables.tryFind(invoicePayments, new Predicate<InvoicePayment>() {
            @Override
            public boolean apply(final InvoicePayment input) {
                return input.getType() == InvoicePaymentType.ATTEMPT;
            }
        }).orNull();
        final UUID invoiceId = invoicePayment != null ? invoicePayment.getInvoiceId() : null;

        final InvoicePaymentJson result = new InvoicePaymentJson(payment, invoiceId, accountAuditLogs);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @TimedResource
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Refund a payment, and adjust the invoice if needed", response = InvoicePaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created refund successfully"),
                           @ApiResponse(code = 400, message = "Invalid payment id supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found")})
    public Response createRefundWithAdjustments(@PathParam("paymentId") final UUID paymentId,
                                                final InvoicePaymentTransactionJson json,
                                                @QueryParam(QUERY_PAYMENT_EXTERNAL) @DefaultValue("false") final Boolean externalPayment,
                                                @QueryParam(QUERY_PAYMENT_METHOD_ID) final UUID paymentMethodId,
                                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                @HeaderParam(HDR_REASON) final String reason,
                                                @HeaderParam(HDR_COMMENT) final String comment,
                                                @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        verifyNonNullOrEmpty(json, "InvoicePaymentTransactionJson body should be specified");

        final CallContext callContextNoAccountId = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        final Payment payment = paymentApi.getPayment(paymentId, false, false, ImmutableList.<PluginProperty>of(), callContextNoAccountId);
        final Account account = accountUserApi.getAccountById(payment.getAccountId(), callContextNoAccountId);
        final CallContext callContext = context.createCallContextWithAccountId(account.getId(), createdBy, reason, comment, request);

        final Iterable<PluginProperty> pluginProperties;
        final String transactionExternalKey = json.getTransactionExternalKey() != null ? json.getTransactionExternalKey() : UUIDs.randomUUID().toString();
        final String paymentExternalKey = json.getPaymentExternalKey() != null ? json.getPaymentExternalKey() : UUIDs.randomUUID().toString();

        final boolean isAdjusted = json.isAdjusted() != null && json.isAdjusted();
        final Map<UUID, BigDecimal> adjustments = new HashMap<UUID, BigDecimal>();
        if (isAdjusted) {
            if (json.getAdjustments() != null && !json.getAdjustments().isEmpty()) {
                for (final InvoiceItemJson item : json.getAdjustments()) {
                    adjustments.put(item.getInvoiceItemId(), item.getAmount());
                }
                pluginProperties = extractPluginProperties(pluginPropertiesString);
            } else {
                pluginProperties = extractPluginProperties(pluginPropertiesString);
            }
        } else {
            pluginProperties = extractPluginProperties(pluginPropertiesString);
        }

        final UUID paymentIdToRedirectTo;
        if (externalPayment) {
            invoicePaymentApi.createCreditForInvoicePayment(isAdjusted,
                                                            adjustments,
                                                            account,
                                                            paymentId,
                                                            paymentMethodId,
                                                            null,
                                                            json.getAmount(),
                                                            account.getCurrency(),
                                                            json.getEffectiveDate(),
                                                            paymentExternalKey,
                                                            transactionExternalKey,
                                                            pluginProperties,
                                                            createInvoicePaymentControlPluginApiPaymentOptions(true),
                                                            callContext);
            // /!\ Note! The invoicePayment#paymentId points to the original payment (PURCHASE) here, NOT the new one (CREDIT)
            paymentIdToRedirectTo = paymentApi.getPaymentByTransactionExternalKey(transactionExternalKey, false, false, ImmutableList.<PluginProperty>of(), callContext).getId();
        } else {
            invoicePaymentApi.createRefundForInvoicePayment(isAdjusted,
                                                            adjustments,
                                                            account,
                                                            payment.getId(),
                                                            json.getAmount(),
                                                            account.getCurrency(),
                                                            json.getEffectiveDate(),
                                                            transactionExternalKey,
                                                            pluginProperties,
                                                            createInvoicePaymentControlPluginApiPaymentOptions(false),
                                                            callContext);
            // Note that the InvoicePayment may not be created (i.e. return null), even though the refund went through (wrong item adjustments for instance)
            paymentIdToRedirectTo = payment.getId();
        }

        return uriBuilder.buildResponse(uriInfo, InvoicePaymentResource.class, "getInvoicePayment", paymentIdToRedirectTo, request);
    }

    @TimedResource
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CHARGEBACKS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Record a chargeback", response = InvoicePaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created chargeback successfully"),
                           @ApiResponse(code = 400, message = "Invalid payment id supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found")})
    public Response createChargeback(@PathParam("paymentId") final UUID paymentId,
                                     final InvoicePaymentTransactionJson json,
                                     @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                     @HeaderParam(HDR_REASON) final String reason,
                                     @HeaderParam(HDR_COMMENT) final String comment,
                                     @javax.ws.rs.core.Context final UriInfo uriInfo,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        verifyNonNullOrEmpty(json, "InvoicePaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getAmount(), "InvoicePaymentTransactionJson amount needs to be set");

        final CallContext callContextNoAccountId = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        final Payment payment = paymentApi.getPayment(paymentId, false, false, ImmutableList.<PluginProperty>of(), callContextNoAccountId);
        final Account account = accountUserApi.getAccountById(payment.getAccountId(), callContextNoAccountId);
        final CallContext callContext = context.createCallContextWithAccountId(account.getId(), createdBy, reason, comment, request);
        final String transactionExternalKey = json.getTransactionExternalKey() != null ? json.getTransactionExternalKey() : UUIDs.randomUUID().toString();

        invoicePaymentApi.createChargebackForInvoicePayment(account,
                                                            payment.getId(),
                                                            json.getAmount(),
                                                            account.getCurrency(),
                                                            json.getEffectiveDate(),
                                                            transactionExternalKey,
                                                            extractPluginProperties(pluginPropertiesString),
                                                            createInvoicePaymentControlPluginApiPaymentOptions(false),
                                                            callContext);
        return uriBuilder.buildResponse(uriInfo, InvoicePaymentResource.class, "getInvoicePayment", payment.getId(), request);
    }

    @TimedResource
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CHARGEBACK_REVERSALS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Record a chargebackReversal", response = InvoicePaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Created chargeback reversal successfully"),
                           @ApiResponse(code = 400, message = "Invalid payment id supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found")})
    public Response createChargebackReversal(@PathParam("paymentId") final UUID paymentId,
                                             final InvoicePaymentTransactionJson json,
                                             @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final UriInfo uriInfo,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        verifyNonNullOrEmpty(json, "InvoicePaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getTransactionExternalKey(), "InvoicePaymentTransactionJson transactionExternalKey needs to be set");

        final CallContext callContextNoAccountId = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        final Payment payment = paymentApi.getPayment(paymentId, false, false, ImmutableList.<PluginProperty>of(), callContextNoAccountId);
        final Account account = accountUserApi.getAccountById(payment.getAccountId(), callContextNoAccountId);
        final CallContext callContext = context.createCallContextWithAccountId(account.getId(), createdBy, reason, comment, request);

        invoicePaymentApi.createChargebackReversalForInvoicePayment(account,
                                                                    payment.getId(),
                                                                    json.getEffectiveDate(),
                                                                    json.getTransactionExternalKey(),
                                                                    extractPluginProperties(pluginPropertiesString),
                                                                    createInvoicePaymentControlPluginApiPaymentOptions(false),
                                                                    callContext);
        return uriBuilder.buildResponse(uriInfo, InvoicePaymentResource.class, "getInvoicePayment", paymentId, request);
    }

    @TimedResource(name = "completeInvoicePaymentTransaction")
    @PUT
    @Path("/{paymentId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Complete an existing transaction")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response completeInvoicePaymentTransaction(@PathParam("paymentId") final UUID paymentId,
                                                      final PaymentTransactionJson json,
                                                      @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                      @HeaderParam(HDR_REASON) final String reason,
                                                      @HeaderParam(HDR_COMMENT) final String comment,
                                                      @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Payment payment = paymentApi.getPayment(paymentId, false, false, ImmutableList.<PluginProperty>of(), tenantContext);
        final List<InvoicePayment> invoicePayments = invoicePaymentApi.getInvoicePayments(paymentId, tenantContext);

        final InvoicePayment originalInvoicePaymentAttempt = Iterables.tryFind(invoicePayments, new Predicate<InvoicePayment>() {
            @Override
            public boolean apply(final InvoicePayment input) {
                return input.getType() == InvoicePaymentType.ATTEMPT && !input.isSuccess();
            }
        }).orNull();

        final UUID invoiceId = originalInvoicePaymentAttempt != null ? originalInvoicePaymentAttempt.getInvoiceId() : null;
        if (invoiceId == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);

        final List<String> controlPluginNames = new ArrayList<String>();
        controlPluginNames.addAll(paymentControlPluginNames);

        final Account account = accountUserApi.getAccountById(payment.getAccountId(), tenantContext);
        final BigDecimal amount = json == null ? null : json.getAmount();
        final Currency currency = json == null ? null : json.getCurrency();

        final CallContext callContext = context.createCallContextWithAccountId(account.getId(), createdBy, reason, comment, request);

        final PaymentTransaction pendingOrSuccessTransaction = lookupPendingOrSuccessTransaction(payment,
                                                                                                 json != null ? json.getTransactionId() : null,
                                                                                                 json != null ? json.getTransactionExternalKey() : null,
                                                                                                 json != null ? json.getTransactionType() : null);
        // If transaction was already completed, return early (See #626)
        if (pendingOrSuccessTransaction.getTransactionStatus() == TransactionStatus.SUCCESS) {
            return Response.status(Status.NO_CONTENT).build();
        }

        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        invoicePaymentApi.createPurchaseForInvoicePayment(account, invoiceId, payment.getPaymentMethodId(), payment.getId(), amount, currency, null,
                                                          payment.getExternalKey(), pendingOrSuccessTransaction.getExternalKey(),
                                                          pluginProperties, paymentOptions, callContext);

        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve payment custom fields", response = CustomFieldJson.class, responseContainer = "List", nickname = "getInvoicePaymentCustomFields")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid payment id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(id, auditMode, context.createTenantContextNoAccountId(request));
    }

    @TimedResource
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to payment", response = CustomField.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Custom field created successfully"),
                           @ApiResponse(code = 400, message = "Invalid payment id supplied")})
    public Response createInvoicePaymentCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                     final List<CustomFieldJson> customFields,
                                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                     @HeaderParam(HDR_REASON) final String reason,
                                                     @HeaderParam(HDR_COMMENT) final String comment,
                                                     @javax.ws.rs.core.Context final HttpServletRequest request,
                                                     @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(id, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request), uriInfo, request);
    }

    @TimedResource
    @PUT
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Modify custom fields to payment")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid payment id supplied")})
    public Response modifyInvoicePaymentCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                     final List<CustomFieldJson> customFields,
                                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                     @HeaderParam(HDR_REASON) final String reason,
                                                     @HeaderParam(HDR_COMMENT) final String comment,
                                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.modifyCustomFields(id, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @DELETE
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from payment")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid payment id supplied")})
    public Response deleteInvoicePaymentCustomFields(@PathParam(ID_PARAM_NAME) final UUID id,
                                                     @QueryParam(QUERY_CUSTOM_FIELD) final List<UUID> customFieldList,
                                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                     @HeaderParam(HDR_REASON) final String reason,
                                                     @HeaderParam(HDR_COMMENT) final String comment,
                                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(id, customFieldList,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve payment tags", response = TagJson.class, responseContainer = "List", nickname = "getInvoicePaymentTags")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid payment id supplied"),
                           @ApiResponse(code = 404, message = "Payment not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final UUID paymentId,
                            @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Payment payment = paymentApi.getPayment(paymentId, false, false, pluginProperties, tenantContext);
        return super.getTags(payment.getAccountId(), paymentId, auditMode, includedDeleted, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add tags to payment", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Tag created successfully"),
                           @ApiResponse(code = 400, message = "Invalid payment id supplied")})
    public Response createInvoicePaymentTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                             final List<UUID> tagList,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final UriInfo uriInfo,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(id, tagList, uriInfo,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request), request);
    }

    @TimedResource
    @DELETE
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from payment")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid payment id supplied")})
    public Response deleteInvoicePaymentTags(@PathParam(ID_PARAM_NAME) final UUID id,
                                             @QueryParam(QUERY_TAG) final List<UUID> tagList,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.deleteTags(id, tagList,
                                context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.PAYMENT;
    }
}
