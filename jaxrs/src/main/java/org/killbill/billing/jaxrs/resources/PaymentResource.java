/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;
import javax.inject.Inject;
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
import org.killbill.billing.jaxrs.json.ComboPaymentTransactionJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.PaymentJson;
import org.killbill.billing.jaxrs.json.PaymentTransactionJson;
import org.killbill.billing.jaxrs.json.TagJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
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
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.MetricTag;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.PAYMENTS_PATH)
@Api(value = JaxrsResource.PAYMENTS_PATH, description = "Operations on payments")
public class PaymentResource extends ComboPaymentResource {

    private static final String ID_PARAM_NAME = "paymentId";

    @Inject
    public PaymentResource(final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final AccountUserApi accountUserApi,
                           final PaymentApi paymentApi,
                           final Clock clock,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, clock, context);
    }

    @TimedResource(name = "getPayment")
    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a payment by id", response = PaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Payment not found")})
    public Response getPayment(@PathParam("paymentId") final String paymentIdStr,
                               @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                               @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                               @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final UUID paymentIdId = UUID.fromString(paymentIdStr);
        final TenantContext tenantContext = context.createContext(request);
        final Payment payment = paymentApi.getPayment(paymentIdId, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(payment.getAccountId(), auditMode.getLevel(), tenantContext);
        final PaymentJson result = new PaymentJson(payment, accountAuditLogs);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @TimedResource(name = "getPayment")
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a payment by external key", response = PaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Payment not found")})
    public Response getPaymentByExternalKey(@QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                            @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                            @QueryParam(QUERY_EXTERNAL_KEY) final String paymentExternalKey,
                                            @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                            @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        verifyNonNullOrEmpty(paymentExternalKey, "Payment externalKey needs to be specified");
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);
        final Payment payment = paymentApi.getPaymentByExternalKey(paymentExternalKey, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(payment.getAccountId(), auditMode.getLevel(), tenantContext);
        final PaymentJson result = new PaymentJson(payment, accountAuditLogs);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @TimedResource
    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get payments", response = PaymentJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response getPayments(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                @QueryParam(QUERY_PAYMENT_PLUGIN_NAME) final String pluginName,
                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);

        final Pagination<Payment> payments;
        if (Strings.isNullOrEmpty(pluginName)) {
            payments = paymentApi.getPayments(offset, limit, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        } else {
            payments = paymentApi.getPayments(offset, limit, pluginName, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        }

        final URI nextPageUri = uriBuilder.nextPage(PaymentResource.class, "getPayments", payments.getNextOffset(), limit, ImmutableMap.<String, String>of(QUERY_PAYMENT_METHOD_PLUGIN_NAME, Strings.nullToEmpty(pluginName),
                                                                                                                                                           QUERY_AUDIT, auditMode.getLevel().toString()));
        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());

        return buildStreamingPaginationResponse(payments,
                                                new Function<Payment, PaymentJson>() {
                                                    @Override
                                                    public PaymentJson apply(final Payment payment) {
                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(payment.getAccountId()) == null) {
                                                            accountsAuditLogs.get().put(payment.getAccountId(), auditUserApi.getAccountAuditLogs(payment.getAccountId(), auditMode.getLevel(), tenantContext));
                                                        }
                                                        final AccountAuditLogs accountAuditLogs = accountsAuditLogs.get().get(payment.getAccountId());
                                                        return new PaymentJson(payment, accountAuditLogs);
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @TimedResource
    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Search payments", response = PaymentJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response searchPayments(@PathParam("searchKey") final String searchKey,
                                   @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                   @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                   @QueryParam(QUERY_PAYMENT_PLUGIN_NAME) final String pluginName,
                                   @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                   @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                   @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                   @QueryParam(QUERY_WITH_ATTEMPTS) @DefaultValue("false") final Boolean withAttempts,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);

        // Search the plugin(s)
        final Pagination<Payment> payments;
        if (Strings.isNullOrEmpty(pluginName)) {
            payments = paymentApi.searchPayments(searchKey, offset, limit, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        } else {
            payments = paymentApi.searchPayments(searchKey, offset, limit, pluginName, withPluginInfo, withAttempts, pluginProperties, tenantContext);
        }

        final URI nextPageUri = uriBuilder.nextPage(PaymentResource.class, "searchPayments", payments.getNextOffset(), limit, ImmutableMap.<String, String>of("searchKey", searchKey,
                                                                                                                                                              QUERY_PAYMENT_METHOD_PLUGIN_NAME, Strings.nullToEmpty(pluginName),
                                                                                                                                                              QUERY_AUDIT, auditMode.getLevel().toString()));
        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());

        return buildStreamingPaginationResponse(payments,
                                                new Function<Payment, PaymentJson>() {
                                                    @Override
                                                    public PaymentJson apply(final Payment payment) {
                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(payment.getAccountId()) == null) {
                                                            accountsAuditLogs.get().put(payment.getAccountId(), auditUserApi.getAccountAuditLogs(payment.getAccountId(), auditMode.getLevel(), tenantContext));
                                                        }
                                                        final AccountAuditLogs accountAuditLogs = accountsAuditLogs.get().get(payment.getAccountId());
                                                        return new PaymentJson(payment, accountAuditLogs);
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @TimedResource(name = "completeTransaction")
    @PUT
    @Path("/{paymentId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Complete an existing transaction")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response completeTransaction(@MetricTag(tag = "type", property = "transactionType") final PaymentTransactionJson json,
                                        @PathParam("paymentId") final String paymentIdStr,
                                        @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                        @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final UriInfo uriInfo,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return completeTransactionInternal(json, paymentIdStr, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    @TimedResource(name = "completeTransaction")
    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Complete an existing transaction")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response completeTransactionByExternalKey(@MetricTag(tag = "type", property = "transactionType") final PaymentTransactionJson json,
                                                     @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                                     @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                     @HeaderParam(HDR_REASON) final String reason,
                                                     @HeaderParam(HDR_COMMENT) final String comment,
                                                     @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return completeTransactionInternal(json, null, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }






    private Response completeTransactionInternal(final PaymentTransactionJson json,
                                                 @Nullable final String paymentIdStr,
                                                 final List<String> paymentControlPluginNames,
                                                 final Iterable<String> pluginPropertiesString,
                                                 final String createdBy,
                                                 final String reason,
                                                 final String comment,
                                                 final UriInfo uriInfo,
                                                 final HttpServletRequest request) throws PaymentApiException, AccountApiException {

        final Iterable<PluginProperty> pluginPropertiesFromBody = extractPluginProperties(json.getProperties());

        final Iterable<PluginProperty> pluginPropertiesFromQuery = extractPluginProperties(pluginPropertiesString);

        final Iterable<PluginProperty> pluginProperties = Iterables.concat(pluginPropertiesFromQuery, pluginPropertiesFromBody);

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Payment initialPayment = getPaymentByIdOrKey(paymentIdStr, json == null ? null : json.getPaymentExternalKey(), pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);
        final BigDecimal amount = json == null ? null : json.getAmount();
        final Currency currency = json == null || json.getCurrency() == null ? null : Currency.valueOf(json.getCurrency());

        final PaymentTransaction pendingOrSuccessTransaction = lookupPendingOrSuccessTransaction(initialPayment,
                                                                                                 json != null ? json.getTransactionId() : null,
                                                                                                 json != null ? json.getTransactionExternalKey() : null,
                                                                                                 json != null ? json.getTransactionType() : null);
        // If transaction was already completed, return early (See #626)
        if (pendingOrSuccessTransaction.getTransactionStatus() == TransactionStatus.SUCCESS) {
            return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", pendingOrSuccessTransaction.getPaymentId(), request);
        }


        final PaymentTransaction pendingTransaction = pendingOrSuccessTransaction;
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        final Payment result;
        switch (pendingTransaction.getTransactionType()) {
            case AUTHORIZE:
                result = paymentApi.createAuthorizationWithPaymentControl(account, initialPayment.getPaymentMethodId(), initialPayment.getId(), amount, currency,
                                                                 initialPayment.getExternalKey(), pendingTransaction.getExternalKey(),
                                                                 pluginProperties, paymentOptions, callContext);
                break;
            case CAPTURE:
                result = paymentApi.createCaptureWithPaymentControl(account, initialPayment.getId(), amount, currency, pendingTransaction.getExternalKey(),
                                                           pluginProperties, paymentOptions, callContext);
                break;
            case PURCHASE:
                result = paymentApi.createPurchaseWithPaymentControl(account, initialPayment.getPaymentMethodId(), initialPayment.getId(), amount, currency,
                                                            initialPayment.getExternalKey(), pendingTransaction.getExternalKey(),
                                                            pluginProperties, paymentOptions, callContext);
                break;
            case CREDIT:
                result = paymentApi.createCreditWithPaymentControl(account, initialPayment.getPaymentMethodId(), initialPayment.getId(), amount, currency,
                                                          initialPayment.getExternalKey(), pendingTransaction.getExternalKey(),
                                                          pluginProperties, paymentOptions, callContext);
                break;
            case REFUND:
                result = paymentApi.createRefundWithPaymentControl(account, initialPayment.getId(), amount, currency,
                                                          pendingTransaction.getExternalKey(), pluginProperties, paymentOptions, callContext);
                break;
            default:
                return Response.status(Status.PRECONDITION_FAILED).entity("TransactionType " + pendingTransaction.getTransactionType() + " cannot be completed").build();
        }
        return createPaymentResponse(uriInfo, result, pendingTransaction.getTransactionType(), pendingTransaction.getExternalKey(), request);
    }

    @TimedResource(name = "captureAuthorization")
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Capture an existing authorization")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response captureAuthorization(final PaymentTransactionJson json,
                                         @PathParam("paymentId") final String paymentIdStr,
                                         @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                         @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                         @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                         @HeaderParam(HDR_REASON) final String reason,
                                         @HeaderParam(HDR_COMMENT) final String comment,
                                         @javax.ws.rs.core.Context final UriInfo uriInfo,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return captureAuthorizationInternal(json, paymentIdStr, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    @TimedResource(name = "captureAuthorization")
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Capture an existing authorization")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response captureAuthorizationByExternalKey(final PaymentTransactionJson json,
                                                      @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                      @HeaderParam(HDR_REASON) final String reason,
                                                      @HeaderParam(HDR_COMMENT) final String comment,
                                                      @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return captureAuthorizationInternal(json, null, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    private Response captureAuthorizationInternal(final PaymentTransactionJson json,
                                                  @Nullable final String paymentIdStr,
                                                  final List<String> paymentControlPluginNames,
                                                  final List<String> pluginPropertiesString,
                                                  final String createdBy,
                                                  final String reason,
                                                  final String comment,
                                                  final UriInfo uriInfo,
                                                  final HttpServletRequest request) throws PaymentApiException, AccountApiException {

        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getAmount(), "PaymentTransactionJson amount needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Payment initialPayment = getPaymentByIdOrKey(paymentIdStr, json.getPaymentExternalKey(), pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : Currency.valueOf(json.getCurrency());

        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);

        final Payment payment = paymentApi.createCaptureWithPaymentControl(account, initialPayment.getId(), json.getAmount(), currency,
                                                         json.getTransactionExternalKey(), pluginProperties, paymentOptions, callContext);
        return createPaymentResponse(uriInfo, payment, TransactionType.CAPTURE, json.getTransactionExternalKey(), request);
    }

    @TimedResource(name = "refundPayment")
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Refund an existing payment")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response refundPayment(final PaymentTransactionJson json,
                                  @PathParam("paymentId") final String paymentIdStr,
                                  @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                  @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final UriInfo uriInfo,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return refundPaymentInternal(json, paymentIdStr, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    @TimedResource(name = "refundPayment")
    @POST
    @Path("/" + REFUNDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Refund an existing payment")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response refundPaymentByExternalKey(final PaymentTransactionJson json,
                                               @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                               @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                               @HeaderParam(HDR_REASON) final String reason,
                                               @HeaderParam(HDR_COMMENT) final String comment,
                                               @javax.ws.rs.core.Context final UriInfo uriInfo,
                                               @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return refundPaymentInternal(json, null, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);

    }

    private Response refundPaymentInternal(final PaymentTransactionJson json,
                                           @Nullable final String paymentIdStr,
                                           final List<String> paymentControlPluginNames,
                                           final List<String> pluginPropertiesString,
                                           final String createdBy,
                                           final String reason,
                                           final String comment,
                                           final UriInfo uriInfo,
                                           final HttpServletRequest request) throws PaymentApiException, AccountApiException {

        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getAmount(), "PaymentTransactionJson amount needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Payment initialPayment = getPaymentByIdOrKey(paymentIdStr, json.getPaymentExternalKey(), pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : Currency.valueOf(json.getCurrency());

        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);

        final Payment payment = paymentApi.createRefundWithPaymentControl(account, initialPayment.getId(), json.getAmount(), currency,
                                                        json.getTransactionExternalKey(), pluginProperties, paymentOptions, callContext);

        return createPaymentResponse(uriInfo, payment, TransactionType.REFUND, json.getTransactionExternalKey(), request);
    }

    @TimedResource(name = "voidPayment")
    @DELETE
    @Path("/{paymentId:" + UUID_PATTERN + "}/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Void an existing payment")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response voidPayment(final PaymentTransactionJson json,
                                @PathParam("paymentId") final String paymentIdStr,
                                @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final UriInfo uriInfo,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return voidPaymentInternal(json, paymentIdStr, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    @TimedResource(name = "voidPayment")
    @DELETE
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Void an existing payment")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response voidPaymentByExternalKey(final PaymentTransactionJson json,
                                             @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                             @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final UriInfo uriInfo,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return voidPaymentInternal(json, null, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    private Response voidPaymentInternal(final PaymentTransactionJson json,
                                         @Nullable final String paymentIdStr,
                                         final List<String> paymentControlPluginNames,
                                         final List<String> pluginPropertiesString,
                                         final String createdBy,
                                         final String reason,
                                         final String comment,
                                         final UriInfo uriInfo,
                                         final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Payment initialPayment = getPaymentByIdOrKey(paymentIdStr, json.getPaymentExternalKey(), pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);

        final String transactionExternalKey = json != null ? json.getTransactionExternalKey() : null;
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);

        final Payment payment = paymentApi.createVoidWithPaymentControl(account, initialPayment.getId(), transactionExternalKey,
                                                                        pluginProperties, paymentOptions, callContext);
        return createPaymentResponse(uriInfo, payment, TransactionType.VOID, json.getTransactionExternalKey(), request);
    }

    @TimedResource(name = "chargebackPayment")
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CHARGEBACKS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Record a chargeback")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response chargebackPayment(final PaymentTransactionJson json,
                                      @PathParam("paymentId") final String paymentIdStr,
                                      @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final UriInfo uriInfo,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return chargebackPaymentInternal(json, paymentIdStr, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    @TimedResource(name = "chargebackPayment")
    @POST
    @Path("/" + CHARGEBACKS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Record a chargeback")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response chargebackPaymentByExternalKey(final PaymentTransactionJson json,
                                                   @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                                   @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                   @HeaderParam(HDR_REASON) final String reason,
                                                   @HeaderParam(HDR_COMMENT) final String comment,
                                                   @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return chargebackPaymentInternal(json, null, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    private Response chargebackPaymentInternal(final PaymentTransactionJson json,
                                               @Nullable final String paymentIdStr,
                                               final List<String> paymentControlPluginNames,
                                               final List<String> pluginPropertiesString,
                                               final String createdBy,
                                               final String reason,
                                               final String comment,
                                               final UriInfo uriInfo,
                                               final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getAmount(), "PaymentTransactionJson amount needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Payment initialPayment = getPaymentByIdOrKey(paymentIdStr, json.getPaymentExternalKey(), pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : Currency.valueOf(json.getCurrency());

        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);

        final Payment payment = paymentApi.createChargebackWithPaymentControl(account, initialPayment.getId(), json.getAmount(), currency,
                                                            json.getTransactionExternalKey(), paymentOptions, callContext);
        return createPaymentResponse(uriInfo, payment, TransactionType.CHARGEBACK, json.getTransactionExternalKey(), request);
    }

    @TimedResource(name = "chargebackReversalPayment")
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CHARGEBACK_REVERSALS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Record a chargeback reversal")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response chargebackReversalPayment(final PaymentTransactionJson json,
                                              @PathParam("paymentId") final String paymentIdStr,
                                              @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                              @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                              @HeaderParam(HDR_REASON) final String reason,
                                              @HeaderParam(HDR_COMMENT) final String comment,
                                              @javax.ws.rs.core.Context final UriInfo uriInfo,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return chargebackReversalPaymentInternal(json, paymentIdStr, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    @TimedResource(name = "chargebackReversalPayment")
    @POST
    @Path("/" + CHARGEBACK_REVERSALS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Record a chargeback reversal")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 404, message = "Account or payment not found"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response chargebackReversalPaymentByExternalKey(final PaymentTransactionJson json,
                                                           @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                                           @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                           @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                           @HeaderParam(HDR_REASON) final String reason,
                                                           @HeaderParam(HDR_COMMENT) final String comment,
                                                           @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                           @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return chargebackReversalPaymentInternal(json, null, paymentControlPluginNames, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    private Response chargebackReversalPaymentInternal(final PaymentTransactionJson json,
                                                       @Nullable final String paymentIdStr,
                                                       final List<String> paymentControlPluginNames,
                                                       final List<String> pluginPropertiesString,
                                                       final String createdBy,
                                                       final String reason,
                                                       final String comment,
                                                       final UriInfo uriInfo,
                                                       final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getTransactionExternalKey(), "PaymentTransactionJson transactionExternalKey needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Payment initialPayment = getPaymentByIdOrKey(paymentIdStr, json.getPaymentExternalKey(), pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);

        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);

        final Payment payment = paymentApi.createChargebackReversalWithPaymentControl(account, initialPayment.getId(), json.getTransactionExternalKey(), paymentOptions, callContext);
        return createPaymentResponse(uriInfo, payment, TransactionType.CHARGEBACK, json.getTransactionExternalKey(), request);
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/" + COMBO)
    @ApiOperation(value = "Combo api to create a new payment transaction on a existing (or not) account ")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Payment transaction created successfully"),
                           @ApiResponse(code = 400, message = "Invalid data for Account or PaymentMethod"),
                           @ApiResponse(code = 402, message = "Transaction declined by gateway"),
                           @ApiResponse(code = 422, message = "Payment is aborted by a control plugin"),
                           @ApiResponse(code = 502, message = "Failed to submit payment transaction"),
                           @ApiResponse(code = 503, message = "Payment in unknown status, failed to receive gateway response"),
                           @ApiResponse(code = 504, message = "Payment operation timeout")})
    public Response createComboPayment(@MetricTag(tag = "type", property = "transactionType") final ComboPaymentTransactionJson json,
                                       @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {

        verifyNonNullOrEmpty(json, "ComboPaymentTransactionJson body should be specified");

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Account account = getOrCreateAccount(json.getAccount(), callContext);

        final Iterable<PluginProperty> paymentMethodPluginProperties = extractPluginProperties(json.getPaymentMethodPluginProperties());
        final UUID paymentMethodId = getOrCreatePaymentMethod(account, json.getPaymentMethod(), paymentMethodPluginProperties, callContext);

        final PaymentTransactionJson paymentTransactionJson = json.getTransaction();
        final TransactionType transactionType = TransactionType.valueOf(paymentTransactionJson.getTransactionType());
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        final Payment result;

        final Iterable<PluginProperty> transactionPluginProperties = extractPluginProperties(json.getTransactionPluginProperties());

        final Currency currency = paymentTransactionJson.getCurrency() == null ? account.getCurrency() : Currency.valueOf(paymentTransactionJson.getCurrency());
        final UUID paymentId = null; // If we need to specify a paymentId (e.g 3DS authorization, we can use regular API, no need for combo call)
        switch (transactionType) {
            case AUTHORIZE:
                result = paymentApi.createAuthorizationWithPaymentControl(account, paymentMethodId, paymentId, paymentTransactionJson.getAmount(), currency,
                                                                          paymentTransactionJson.getPaymentExternalKey(), paymentTransactionJson.getTransactionExternalKey(),
                                                                          transactionPluginProperties, paymentOptions, callContext);
                break;
            case PURCHASE:
                result = paymentApi.createPurchaseWithPaymentControl(account, paymentMethodId, paymentId, paymentTransactionJson.getAmount(), currency,
                                                                     paymentTransactionJson.getPaymentExternalKey(), paymentTransactionJson.getTransactionExternalKey(),
                                                                     transactionPluginProperties, paymentOptions, callContext);
                break;
            case CREDIT:
                result = paymentApi.createCreditWithPaymentControl(account, paymentMethodId, paymentId, paymentTransactionJson.getAmount(), currency,
                                                                   paymentTransactionJson.getPaymentExternalKey(), paymentTransactionJson.getTransactionExternalKey(),
                                                                   transactionPluginProperties, paymentOptions, callContext);
                break;
            default:
                return Response.status(Status.PRECONDITION_FAILED).entity("TransactionType " + transactionType + " is not allowed for an account").build();
        }
        return createPaymentResponse(uriInfo, result, transactionType, paymentTransactionJson.getTransactionExternalKey(), request);
    }

    @TimedResource(name = "cancelScheduledPaymentTransaction")
    @DELETE
    @Path("/{paymentTransactionId:" + UUID_PATTERN + "}/" + CANCEL_SCHEDULED_PAYMENT_TRANSACTION)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Cancels a scheduled payment attempt retry")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentTransactionId supplied")})
    public Response cancelScheduledPaymentTransactionById(@PathParam("paymentTransactionId") final String paymentTransactionId,
                                                          @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                          @HeaderParam(HDR_REASON) final String reason,
                                                          @HeaderParam(HDR_COMMENT) final String comment,
                                                          @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        paymentApi.cancelScheduledPaymentTransaction(UUID.fromString(paymentTransactionId), callContext);
        return Response.status(Status.OK).build();
    }

    @TimedResource(name = "cancelScheduledPaymentTransaction")
    @DELETE
    @Path("/" + CANCEL_SCHEDULED_PAYMENT_TRANSACTION)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Cancels a scheduled payment attempt retry")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentTransactionExternalKey supplied")})
    public Response cancelScheduledPaymentTransactionByExternalKey(@QueryParam(QUERY_TRANSACTION_EXTERNAL_KEY) final String paymentTransactionExternalKey,
                                                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                                   @HeaderParam(HDR_REASON) final String reason,
                                                                   @HeaderParam(HDR_COMMENT) final String comment,
                                                                   @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        paymentApi.cancelScheduledPaymentTransaction(paymentTransactionExternalKey, callContext);
        return Response.status(Status.OK).build();
    }




    @TimedResource
    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve payment custom fields", response = CustomFieldJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid payment id supplied")})
    public Response getCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(UUID.fromString(id), auditMode, context.createContext(request));
    }

    @TimedResource
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to payment")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid payment id supplied")})
    public Response createCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       final List<CustomFieldJson> customFields,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request,
                                       @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(UUID.fromString(id), customFields,
                                        context.createContext(createdBy, reason, comment, request), uriInfo, request);
    }

    @TimedResource
    @DELETE
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from payment payment")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid payment id supplied")})
    public Response deleteCustomFields(@PathParam(ID_PARAM_NAME) final String id,
                                       @QueryParam(QUERY_CUSTOM_FIELDS) final String customFieldList,
                                       @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                       @HeaderParam(HDR_REASON) final String reason,
                                       @HeaderParam(HDR_COMMENT) final String comment,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(UUID.fromString(id), customFieldList,
                                        context.createContext(createdBy, reason, comment, request));
    }

    @TimedResource
    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + TAGS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve payment payment tags", response = TagJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid payment id supplied"),
                           @ApiResponse(code = 404, message = "Invoice not found")})
    public Response getTags(@PathParam(ID_PARAM_NAME) final String id,
                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                            @QueryParam(QUERY_TAGS_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                            @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException, PaymentApiException {
        final TenantContext tenantContext = context.createContext(request);
        final UUID paymentId = UUID.fromString(id);
        final Payment payment = paymentApi.getPayment(paymentId, false, false, ImmutableList.<PluginProperty>of(), tenantContext);
        return super.getTags(payment.getAccountId(), paymentId, auditMode, includedDeleted, tenantContext);
    }

    @TimedResource
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add tags to payment payment")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid payment id supplied")})
    public Response createTags(@PathParam(ID_PARAM_NAME) final String id,
                               @QueryParam(QUERY_TAGS) final String tagList,
                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                               @HeaderParam(HDR_REASON) final String reason,
                               @HeaderParam(HDR_COMMENT) final String comment,
                               @javax.ws.rs.core.Context final UriInfo uriInfo,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws TagApiException {
        return super.createTags(UUID.fromString(id), tagList, uriInfo,
                                context.createContext(createdBy, reason, comment, request), request);
    }

    @TimedResource
    @DELETE
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + TAGS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove tags from payment payment")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid payment id supplied")})
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
