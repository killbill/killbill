/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import org.killbill.billing.jaxrs.json.PaymentJson;
import org.killbill.billing.jaxrs.json.PaymentTransactionJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.PAYMENTS_PATH)
@Api(value = JaxrsResource.PAYMENTS_PATH, description = "Operations on payments")
public class PaymentResource extends ComboPaymentResource {

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

    @Timed
    @GET
    @Path("/{paymentId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a payment by id", response = PaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Payment not found")})
    public Response getPayment(@PathParam("paymentId") final String paymentIdStr,
                               @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                               @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final UUID paymentIdId = UUID.fromString(paymentIdStr);
        final TenantContext tenantContext = context.createContext(request);
        final Payment payment = paymentApi.getPayment(paymentIdId, withPluginInfo, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(payment.getAccountId(), auditMode.getLevel(), tenantContext);
        final PaymentJson result = new PaymentJson(payment, accountAuditLogs);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @Timed
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a payment by id", response = PaymentJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Payment not found")})
    public Response getPaymentByExternalKey(@QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                            @QueryParam(QUERY_EXTERNAL_KEY) final String paymentExternalKey,
                                            @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                            @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                            @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        verifyNonNullOrEmpty(paymentExternalKey, "Payment externalKey needs to be specified");
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);
        final Payment payment = paymentApi.getPaymentByExternalKey(paymentExternalKey, withPluginInfo, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(payment.getAccountId(), auditMode.getLevel(), tenantContext);
        final PaymentJson result = new PaymentJson(payment, accountAuditLogs);
        return Response.status(Response.Status.OK).entity(result).build();
    }

    @Timed
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
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);

        final Pagination<Payment> payments;
        if (Strings.isNullOrEmpty(pluginName)) {
            payments = paymentApi.getPayments(offset, limit, withPluginInfo, pluginProperties, tenantContext);
        } else {
            payments = paymentApi.getPayments(offset, limit, pluginName, withPluginInfo, pluginProperties, tenantContext);
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

    @Timed
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
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);

        // Search the plugin(s)
        final Pagination<Payment> payments;
        if (Strings.isNullOrEmpty(pluginName)) {
            payments = paymentApi.searchPayments(searchKey, offset, limit, withPluginInfo, pluginProperties, tenantContext);
        } else {
            payments = paymentApi.searchPayments(searchKey, offset, limit, pluginName, withPluginInfo, pluginProperties, tenantContext);
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

    @Timed
    @PUT
    @Path("/{paymentId:" + UUID_PATTERN + "}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Complete an existing transaction")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found")})
    public Response completeTransaction(final PaymentTransactionJson json,
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

    @Timed
    @PUT
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Complete an existing transaction")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account or payment not found")})
    public Response completeTransactionByExternalKey(final PaymentTransactionJson json,
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
        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getTransactionType(), "PaymentTransactionJson transactionType needs to be set");
        if (paymentIdStr == null) {
            verifyNonNullOrEmpty(json.getPaymentExternalKey(), "PaymentTransactionJson externalKey needs to be set");
        }

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Payment initialPayment = getPaymentByIdOrKey(paymentIdStr, json.getPaymentExternalKey(), pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : Currency.valueOf(json.getCurrency());

        final TransactionType transactionType = TransactionType.valueOf(json.getTransactionType());
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        switch (transactionType) {
            case AUTHORIZE:
                paymentApi.createAuthorizationWithPaymentControl(account, initialPayment.getPaymentMethodId(), initialPayment.getId(), json.getAmount(), currency,
                                                                 json.getPaymentExternalKey(), json.getTransactionExternalKey(),
                                                                 pluginProperties, paymentOptions, callContext);
                break;
            case PURCHASE:
                paymentApi.createPurchaseWithPaymentControl(account, initialPayment.getPaymentMethodId(), initialPayment.getId(), json.getAmount(), currency,
                                                            json.getPaymentExternalKey(), json.getTransactionExternalKey(),
                                                            pluginProperties, paymentOptions, callContext);
                break;
            case CREDIT:
                paymentApi.createCreditWithPaymentControl(account, initialPayment.getPaymentMethodId(), initialPayment.getId(), json.getAmount(), currency,
                                                          json.getPaymentExternalKey(), json.getTransactionExternalKey(),
                                                          pluginProperties, paymentOptions, callContext);
                break;
            default:
                // It looks like we need at least REFUND? See https://github.com/killbill/killbill/issues/371
                return Response.status(Status.PRECONDITION_FAILED).entity("TransactionType " + transactionType + " cannot be completed").build();
        }
        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", initialPayment.getId());
    }

    @Timed
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Capture an existing authorization")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found")})
    public Response captureAuthorization(final PaymentTransactionJson json,
                                         @PathParam("paymentId") final String paymentIdStr,
                                         @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                         @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                         @HeaderParam(HDR_REASON) final String reason,
                                         @HeaderParam(HDR_COMMENT) final String comment,
                                         @javax.ws.rs.core.Context final UriInfo uriInfo,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return captureAuthorizationInternal(json, paymentIdStr, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    @Timed
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Capture an existing authorization")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account or payment not found")})
    public Response captureAuthorizationByExternalKey(final PaymentTransactionJson json,
                                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                      @HeaderParam(HDR_REASON) final String reason,
                                                      @HeaderParam(HDR_COMMENT) final String comment,
                                                      @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return captureAuthorizationInternal(json, null, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    private Response captureAuthorizationInternal(final PaymentTransactionJson json,
                                                  @Nullable final String paymentIdStr,
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

        final Payment payment = paymentApi.createCapture(account, initialPayment.getId(), json.getAmount(), currency,
                                                         json.getTransactionExternalKey(), pluginProperties, callContext);
        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", payment.getId());
    }

    @Timed
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Refund an existing payment")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found")})
    public Response refundPayment(final PaymentTransactionJson json,
                                  @PathParam("paymentId") final String paymentIdStr,
                                  @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final UriInfo uriInfo,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return refundPaymentInternal(json, paymentIdStr, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    @Timed
    @POST
    @Path("/" + REFUNDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Refund an existing payment")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account or payment not found")})
    public Response refundPaymentByExternalKey(final PaymentTransactionJson json,
                                               @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                               @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                               @HeaderParam(HDR_REASON) final String reason,
                                               @HeaderParam(HDR_COMMENT) final String comment,
                                               @javax.ws.rs.core.Context final UriInfo uriInfo,
                                               @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return refundPaymentInternal(json, null, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);

    }

    private Response refundPaymentInternal(final PaymentTransactionJson json,
                                           @Nullable final String paymentIdStr,
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

        final Payment payment = paymentApi.createRefund(account, initialPayment.getId(), json.getAmount(), currency,
                                                        json.getTransactionExternalKey(), pluginProperties, callContext);
        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", payment.getId());

    }

    @Timed
    @DELETE
    @Path("/{paymentId:" + UUID_PATTERN + "}/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Void an existing payment")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment not found")})
    public Response voidPayment(final PaymentTransactionJson json,
                                @PathParam("paymentId") final String paymentIdStr,
                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final UriInfo uriInfo,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return voidPaymentInternal(json, paymentIdStr, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    @Timed
    @DELETE
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Void an existing payment")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account or payment not found")})
    public Response voidPaymentByExternalKey(final PaymentTransactionJson json,
                                             @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                             @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                             @HeaderParam(HDR_REASON) final String reason,
                                             @HeaderParam(HDR_COMMENT) final String comment,
                                             @javax.ws.rs.core.Context final UriInfo uriInfo,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return voidPaymentInternal(json, null, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    private Response voidPaymentInternal(final PaymentTransactionJson json,
                                         @Nullable final String paymentIdStr,
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
        final Payment payment = paymentApi.createVoid(account, initialPayment.getId(), transactionExternalKey, pluginProperties, callContext);
        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", payment.getId());
    }

    @Timed
    @POST
    @Path("/{paymentId:" + UUID_PATTERN + "}/" + CHARGEBACKS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Record a chargeback")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentId supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response chargebackPayment(final PaymentTransactionJson json,
                                      @PathParam("paymentId") final String paymentIdStr,
                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                      @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                      @HeaderParam(HDR_REASON) final String reason,
                                      @HeaderParam(HDR_COMMENT) final String comment,
                                      @javax.ws.rs.core.Context final UriInfo uriInfo,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return chargebackPaymentInternal(json, paymentIdStr, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    @Timed
    @POST
    @Path("/" + CHARGEBACKS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Record a chargeback")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account not found")})
    public Response chargebackPaymentByExternalKey(final PaymentTransactionJson json,
                                                   @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                                   @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                   @HeaderParam(HDR_REASON) final String reason,
                                                   @HeaderParam(HDR_COMMENT) final String comment,
                                                   @javax.ws.rs.core.Context final UriInfo uriInfo,
                                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        return chargebackPaymentInternal(json, null, pluginPropertiesString, createdBy, reason, comment, uriInfo, request);
    }

    private Response chargebackPaymentInternal(final PaymentTransactionJson json,
                                               @Nullable final String paymentIdStr,
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

        final Payment payment = paymentApi.createChargeback(account, initialPayment.getId(), json.getAmount(), currency,
                                                            json.getTransactionExternalKey(), callContext);
        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", payment.getId());
    }

    @Timed
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/" + COMBO)
    @ApiOperation(value = "Combo api to create a new payment transaction on a existing (or not) account ")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid data for Account or PaymentMethod")})
    public Response createComboPayment(final ComboPaymentTransactionJson json,
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
        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", result.getId());
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.PAYMENT;
    }
}
