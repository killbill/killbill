/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import org.killbill.billing.jaxrs.json.AccountJson;
import org.killbill.billing.jaxrs.json.ComboPaymentTransactionJson;
import org.killbill.billing.jaxrs.json.PaymentJson;
import org.killbill.billing.jaxrs.json.PaymentMethodJson;
import org.killbill.billing.jaxrs.json.PaymentTransactionJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
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
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.PAYMENTS_PATH)
@Api(value = JaxrsResource.PAYMENTS_PATH, description = "Operations on payments")
public class PaymentResource extends JaxRsResourceBase {

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
        final Payment initialPayment = getPayment(paymentIdStr, json.getPaymentExternalKey(), pluginProperties, callContext);


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
        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getAmount(), "PaymentTransactionJson amount needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
            final UUID paymentId = UUID.fromString(paymentIdStr);
        final Payment initialPayment = paymentApi.getPayment(paymentId, false, pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : Currency.valueOf(json.getCurrency());

        final Payment payment = paymentApi.createRefund(account, paymentId, json.getAmount(), currency,
                                                        json.getTransactionExternalKey(), pluginProperties, callContext);
            return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", payment.getId());
    }

    @Timed
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/" + COMBO)
    @ApiOperation(value = "Combo api to create a new payment transaction on a existing (or not) account ")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid data for Account or PaymentMethod")})
    public Response createPayment(final ComboPaymentTransactionJson json,
                                  @QueryParam(QUERY_PAYMENT_CONTROL_PLUGIN_NAME) final List<String> paymentControlPluginNames,
                                  @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final UriInfo uriInfo,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {

        verifyNonNullOrEmpty(json, "ComboPaymentTransactionJson body should be specified");

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final Account account = getOrCreateAccount(json.getAccount(), callContext);

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final UUID paymentMethodId = getOrCreatePaymentMethod(account, json.getPaymentMethod(), pluginProperties, callContext);

        final PaymentTransactionJson paymentTransactionJson = json.getTransaction();
        final TransactionType transactionType = TransactionType.valueOf(paymentTransactionJson.getTransactionType());
        final PaymentOptions paymentOptions = createControlPluginApiPaymentOptions(paymentControlPluginNames);
        final Payment result;

        final UUID paymentId = null; // If we need to specify a paymentId (e.g 3DS authorization, we can use regular API, no need for combo call)
        switch (transactionType) {
            case AUTHORIZE:
                result = paymentApi.createAuthorizationWithPaymentControl(account, paymentMethodId, paymentId, paymentTransactionJson.getAmount(), account.getCurrency(),
                                                                          paymentTransactionJson.getPaymentExternalKey(), paymentTransactionJson.getTransactionExternalKey(),
                                                                          pluginProperties, paymentOptions, callContext);
                break;
            case PURCHASE:
                result = paymentApi.createPurchaseWithPaymentControl(account, paymentMethodId, paymentId, paymentTransactionJson.getAmount(), account.getCurrency(),
                                                                     paymentTransactionJson.getPaymentExternalKey(), paymentTransactionJson.getTransactionExternalKey(),
                                                                     pluginProperties, paymentOptions, callContext);
                break;
            case CREDIT:
                result = paymentApi.createCreditWithPaymentControl(account, paymentMethodId, paymentId, paymentTransactionJson.getAmount(), account.getCurrency(),
                                                                   paymentTransactionJson.getPaymentExternalKey(), paymentTransactionJson.getTransactionExternalKey(),
                                                                   pluginProperties, paymentOptions, callContext);
                break;
            default:
                return Response.status(Status.PRECONDITION_FAILED).entity("TransactionType " + transactionType + " is not allowed for an account").build();
        }
        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", result.getId());
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
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID paymentId = UUID.fromString(paymentIdStr);
        final Payment initialPayment = paymentApi.getPayment(paymentId, false, pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);

        final String transactionExternalKey = json != null ? json.getTransactionExternalKey() : null;
        final Payment payment = paymentApi.createVoid(account, paymentId, transactionExternalKey, pluginProperties, callContext);
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
        verifyNonNullOrEmpty(json, "PaymentTransactionJson body should be specified");
        verifyNonNullOrEmpty(json.getAmount(), "PaymentTransactionJson amount needs to be set");

        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID paymentId = UUID.fromString(paymentIdStr);
        final Payment initialPayment = paymentApi.getPayment(paymentId, false, pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : Currency.valueOf(json.getCurrency());

        final Payment payment = paymentApi.createChargeback(account, paymentId, json.getAmount(), currency,
                                                            json.getTransactionExternalKey(), callContext);
        return uriBuilder.buildResponse(uriInfo, PaymentResource.class, "getPayment", payment.getId());
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.PAYMENT;
    }

    private Account getOrCreateAccount(final AccountJson accountJson, final CallContext callContext) throws AccountApiException {
        // Attempt to retrieve by accountId if specified
        if (accountJson.getAccountId() != null) {
            try {
                return accountUserApi.getAccountById(UUID.fromString(accountJson.getAccountId()), callContext);
            } catch (AccountApiException ignore) {
            }
        }

        verifyNonNullOrEmpty(accountJson.getExternalKey(), "Account externalKey should be specified");
        try {
            // Attempt to retrieve by account externalKey
            return accountUserApi.getAccountByKey(accountJson.getExternalKey(), callContext);
        } catch (AccountApiException ignore) {
        }
        // Finally create if does not exist
        return accountUserApi.createAccount(accountJson.toAccountData(), callContext);
    }

    private UUID getOrCreatePaymentMethod(final Account account, final PaymentMethodJson paymentMethodJson, final Iterable<PluginProperty> pluginProperties, final CallContext callContext) throws PaymentApiException {
        // Attempt to retrieve by paymentMethodId if specified
        if (paymentMethodJson.getPaymentMethodId() != null) {
            try {
                return paymentApi.getPaymentMethodById(UUID.fromString(paymentMethodJson.getPaymentMethodId()), false, false, pluginProperties, callContext).getId();
            } catch (PaymentApiException ignore) {
            }
        }

        verifyNonNullOrEmpty(paymentMethodJson.getExternalKey(), "PaymentMethod externalKey should be specified");
        try {
            // Attempt to retrieve by paymentMethod externalKey
            return paymentApi.getPaymentMethodByExternalKey(paymentMethodJson.getExternalKey(), false, false, pluginProperties, callContext).getId();
        } catch (PaymentApiException ignore) {
        }
        final PaymentMethod paymentData = paymentMethodJson.toPaymentMethod(account.getId().toString());
        // Finally create if does not exist
        return paymentApi.addPaymentMethod(account, paymentMethodJson.getExternalKey(), paymentMethodJson.getPluginName(), paymentMethodJson.isDefault(),
                                           paymentData.getPluginDetail(), pluginProperties, callContext);
    }

    Payment getPayment(@Nullable final String paymentIdStr, @Nullable final String externalKey, final Iterable<PluginProperty> pluginProperties, final TenantContext tenantContext) throws PaymentApiException {

        Preconditions.checkArgument(paymentIdStr != null || externalKey != null, "Need to set either paymentId or payment externalKey");
        if (paymentIdStr != null) {
            final UUID paymentId = UUID.fromString(paymentIdStr);
            return paymentApi.getPayment(paymentId, false, pluginProperties, tenantContext);
        } else {
            return paymentApi.getPaymentByExternalKey(externalKey, false, pluginProperties, tenantContext);
        }
    }

}
