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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
import org.killbill.billing.jaxrs.json.AuditLogJson;
import org.killbill.billing.jaxrs.json.CustomFieldJson;
import org.killbill.billing.jaxrs.json.PaymentMethodJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.PAYMENT_METHODS_PATH)
@Api(value = JaxrsResource.PAYMENT_METHODS_PATH, description = "Operations on payment methods", tags="PaymentMethod")
public class PaymentMethodResource extends JaxRsResourceBase {

    @Inject
    public PaymentMethodResource(final AccountUserApi accountUserApi,
                                 final JaxrsUriBuilder uriBuilder,
                                 final TagUserApi tagUserApi,
                                 final CustomFieldUserApi customFieldUserApi,
                                 final AuditUserApi auditUserApi,
                                 final PaymentApi paymentApi,
                                 final InvoicePaymentApi invoicePaymentApi,
                                 final Clock clock,
                                 final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
    }

    @TimedResource(name = "getPaymentMethod")
    @GET
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a payment method by id", response = PaymentMethodJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid paymentMethodId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment method not found")})
    public Response getPaymentMethod(@PathParam("paymentMethodId") final UUID paymentMethodId,
                                     @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                                     @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                     @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                     @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);

        final PaymentMethod paymentMethod = paymentApi.getPaymentMethodById(paymentMethodId, includedDeleted, withPluginInfo, pluginProperties, tenantContext);
        final Account account = accountUserApi.getAccountById(paymentMethod.getAccountId(), tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(paymentMethod.getAccountId(), auditMode.getLevel(), tenantContext);
        final PaymentMethodJson json = PaymentMethodJson.toPaymentMethodJson(account, paymentMethod, accountAuditLogs);

        return Response.status(Status.OK).entity(json).build();
    }

    @TimedResource(name = "getPaymentMethod")
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve a payment method by external key", response = PaymentMethodJson.class)
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account or payment method not found")})
    public Response getPaymentMethodByKey(@ApiParam(required=true) @QueryParam(QUERY_EXTERNAL_KEY) final String externalKey,
                                          @QueryParam(QUERY_INCLUDED_DELETED) @DefaultValue("false") final Boolean includedDeleted,
                                          @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                          @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                          @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                          @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);

        final PaymentMethod paymentMethod = paymentApi.getPaymentMethodByExternalKey(externalKey, includedDeleted, withPluginInfo, pluginProperties, tenantContext);
        final Account account = accountUserApi.getAccountById(paymentMethod.getAccountId(), tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(paymentMethod.getAccountId(), auditMode.getLevel(), tenantContext);
        final PaymentMethodJson json = PaymentMethodJson.toPaymentMethodJson(account, paymentMethod, accountAuditLogs);
        return Response.status(Status.OK).entity(json).build();
    }

    @TimedResource
    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "List payment methods", response = PaymentMethodJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response getPaymentMethods(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                      @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                      @QueryParam(QUERY_PAYMENT_METHOD_PLUGIN_NAME) final String pluginName,
                                      @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                      @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);

        final Pagination<PaymentMethod> paymentMethods;
        if (Strings.isNullOrEmpty(pluginName)) {
            paymentMethods = paymentApi.getPaymentMethods(offset, limit, withPluginInfo, pluginProperties, tenantContext);
        } else {
            paymentMethods = paymentApi.getPaymentMethods(offset, limit, pluginName, withPluginInfo, pluginProperties, tenantContext);
        }

        final URI nextPageUri = uriBuilder.nextPage(PaymentMethodResource.class, "getPaymentMethods", paymentMethods.getNextOffset(), limit, ImmutableMap.<String, String>of(QUERY_PAYMENT_METHOD_PLUGIN_NAME, Strings.nullToEmpty(pluginName),
                                                                                                                                                                             QUERY_AUDIT, auditMode.getLevel().toString()));

        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());
        final Map<UUID, Account> accounts = new HashMap<UUID, Account>();
        return buildStreamingPaginationResponse(paymentMethods,
                                                new Function<PaymentMethod, PaymentMethodJson>() {
                                                    @Override
                                                    public PaymentMethodJson apply(final PaymentMethod paymentMethod) {
                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(paymentMethod.getAccountId()) == null) {
                                                            accountsAuditLogs.get().put(paymentMethod.getAccountId(), auditUserApi.getAccountAuditLogs(paymentMethod.getAccountId(), auditMode.getLevel(), tenantContext));
                                                        }

                                                        // Lookup the associated account(s)
                                                        if (accounts.get(paymentMethod.getAccountId()) == null) {
                                                            final Account account;
                                                            try {
                                                                account = accountUserApi.getAccountById(paymentMethod.getAccountId(), tenantContext);
                                                                accounts.put(paymentMethod.getAccountId(), account);
                                                            } catch (final AccountApiException e) {
                                                                log.warn("Error retrieving accountId='{}'", paymentMethod.getAccountId(), e);
                                                                return null;
                                                            }
                                                        }

                                                        return PaymentMethodJson.toPaymentMethodJson(accounts.get(paymentMethod.getAccountId()), paymentMethod, accountsAuditLogs.get().get(paymentMethod.getAccountId()));
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @TimedResource
    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Search payment methods", response = PaymentMethodJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response searchPaymentMethods(@PathParam("searchKey") final String searchKey,
                                         @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                         @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                         @QueryParam(QUERY_PAYMENT_METHOD_PLUGIN_NAME) final String pluginName,
                                         @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                         @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                         @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);

        // Search the plugin(s)
        final Pagination<PaymentMethod> paymentMethods;
        if (Strings.isNullOrEmpty(pluginName)) {
            paymentMethods = paymentApi.searchPaymentMethods(searchKey, offset, limit, withPluginInfo, pluginProperties, tenantContext);
        } else {
            paymentMethods = paymentApi.searchPaymentMethods(searchKey, offset, limit, pluginName, withPluginInfo, pluginProperties, tenantContext);
        }

        final URI nextPageUri = uriBuilder.nextPage(PaymentMethodResource.class, "searchPaymentMethods", paymentMethods.getNextOffset(), limit, ImmutableMap.<String, String>of("searchKey", searchKey,
                                                                                                                                                                                QUERY_PAYMENT_METHOD_PLUGIN_NAME, Strings.nullToEmpty(pluginName),
                                                                                                                                                                                QUERY_AUDIT, auditMode.getLevel().toString()));

        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());
        final Map<UUID, Account> accounts = new HashMap<UUID, Account>();
        return buildStreamingPaginationResponse(paymentMethods,
                                                new Function<PaymentMethod, PaymentMethodJson>() {
                                                    @Override
                                                    public PaymentMethodJson apply(final PaymentMethod paymentMethod) {
                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(paymentMethod.getAccountId()) == null) {
                                                            accountsAuditLogs.get().put(paymentMethod.getAccountId(), auditUserApi.getAccountAuditLogs(paymentMethod.getAccountId(), auditMode.getLevel(), tenantContext));
                                                        }

                                                        // Lookup the associated account(s)
                                                        if (accounts.get(paymentMethod.getAccountId()) == null) {
                                                            final Account account;
                                                            try {
                                                                account = accountUserApi.getAccountById(paymentMethod.getAccountId(), tenantContext);
                                                                accounts.put(paymentMethod.getAccountId(), account);
                                                            } catch (final AccountApiException e) {
                                                                log.warn("Error retrieving accountId='{}'", paymentMethod.getAccountId(), e);
                                                                return null;
                                                            }
                                                        }

                                                        return PaymentMethodJson.toPaymentMethodJson(accounts.get(paymentMethod.getAccountId()), paymentMethod, accountsAuditLogs.get().get(paymentMethod.getAccountId()));
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @TimedResource
    @DELETE
    @Produces(APPLICATION_JSON)
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Delete a payment method")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid paymentMethodId supplied"),
                           @ApiResponse(code = 404, message = "Account or payment method not found")})
    public Response deletePaymentMethod(@PathParam("paymentMethodId") final UUID paymentMethodId,
                                        @QueryParam(QUERY_DELETE_DEFAULT_PM_WITH_AUTO_PAY_OFF) @DefaultValue("false") final Boolean deleteDefaultPaymentMethodWithAutoPayOff,
                                        @QueryParam(QUERY_FORCE_DEFAULT_PM_DELETION) @DefaultValue("false") final Boolean forceDefaultPaymentMethodDeletion,
                                        @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        final PaymentMethod paymentMethod = paymentApi.getPaymentMethodById(paymentMethodId, false, false, pluginProperties, callContext);
        final Account account = accountUserApi.getAccountById(paymentMethod.getAccountId(), callContext);

        paymentApi.deletePaymentMethod(account, paymentMethodId, deleteDefaultPaymentMethodWithAutoPayOff, forceDefaultPaymentMethodDeletion, pluginProperties, callContext);

        return Response.status(Status.NO_CONTENT).build();
    }

    @TimedResource
    @GET
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve payment method custom fields", response = CustomFieldJson.class, responseContainer = "List", nickname = "getPaymentMethodCustomFields")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid payment method id supplied")})
    public Response getCustomFields(@PathParam("paymentMethodId") final UUID paymentMethodId,
                                    @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        return super.getCustomFields(paymentMethodId, auditMode, context.createTenantContextNoAccountId(request));
    }

    @TimedResource
    @POST
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Add custom fields to payment method", response = CustomField.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 201, message = "Custom field created successfully"),
                           @ApiResponse(code = 400, message = "Invalid payment method id supplied")})
    public Response createPaymentMethodCustomFields(@PathParam("paymentMethodId") final UUID paymentMethodId,
                                                    final List<CustomFieldJson> customFields,
                                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                    @HeaderParam(HDR_REASON) final String reason,
                                                    @HeaderParam(HDR_COMMENT) final String comment,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request,
                                                    @javax.ws.rs.core.Context final UriInfo uriInfo) throws CustomFieldApiException {
        return super.createCustomFields(paymentMethodId, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request), uriInfo, request);
    }


    @TimedResource
    @PUT
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Modify custom fields to payment method")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid payment method id supplied")})
    public Response modifyPaymentMethodCustomFields(@PathParam("paymentMethodId") final UUID paymentMethodId,
                                                    final List<CustomFieldJson> customFields,
                                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                    @HeaderParam(HDR_REASON) final String reason,
                                                    @HeaderParam(HDR_COMMENT) final String comment,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.modifyCustomFields(paymentMethodId, customFields,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }


    @TimedResource
    @DELETE
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}/" + CUSTOM_FIELDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Remove custom fields from payment method")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid payment method id supplied")})
    public Response deletePaymentMethodCustomFields(@PathParam("paymentMethodId") final UUID paymentMethodId,
                                                    @QueryParam(QUERY_CUSTOM_FIELD) final List<UUID> customFieldList,
                                                    @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                    @HeaderParam(HDR_REASON) final String reason,
                                                    @HeaderParam(HDR_COMMENT) final String comment,
                                                    @javax.ws.rs.core.Context final HttpServletRequest request) throws CustomFieldApiException {
        return super.deleteCustomFields(paymentMethodId, customFieldList,
                                        context.createCallContextNoAccountId(createdBy, reason, comment, request));
    }

    @TimedResource
    @GET
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}/" + AUDIT_LOG_WITH_HISTORY)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve payment method audit logs with history by id", response = AuditLogJson.class, responseContainer = "List")
    @ApiResponses(value = {@ApiResponse(code = 404, message = "Account not found")})
    public Response getPaymentMethodAuditLogsWithHistory(@PathParam("paymentMethodId") final UUID paymentMethodId,
                                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final List<AuditLogWithHistory> auditLogWithHistory = paymentApi.getPaymentMethodAuditLogsWithHistoryForId(paymentMethodId, AuditLevel.FULL, tenantContext);
        return Response.status(Status.OK).entity(getAuditLogsWithHistory(auditLogWithHistory)).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.PAYMENT_METHOD;
    }
}
