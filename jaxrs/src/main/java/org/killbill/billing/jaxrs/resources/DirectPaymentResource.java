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
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.jaxrs.json.DirectPaymentJson;
import org.killbill.billing.jaxrs.json.DirectTransactionJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.DirectPayment;
import org.killbill.billing.payment.api.DirectPaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.DIRECT_PAYMENTS_PATH)
public class DirectPaymentResource extends JaxRsResourceBase {

    private final DirectPaymentApi directPaymentApi;

    @Inject
    public DirectPaymentResource(final JaxrsUriBuilder uriBuilder,
                                 final TagUserApi tagUserApi,
                                 final CustomFieldUserApi customFieldUserApi,
                                 final AuditUserApi auditUserApi,
                                 final AccountUserApi accountUserApi,
                                 final DirectPaymentApi directPaymentApi,
                                 final Clock clock,
                                 final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.directPaymentApi = directPaymentApi;
    }

    @GET
    @Path("/{directPaymentId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    public Response getDirectPayment(@PathParam("directPaymentId") final String directPaymentIdStr,
                                     @QueryParam(QUERY_WITH_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                     @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                     @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final UUID directPaymentIdId = UUID.fromString(directPaymentIdStr);
        final TenantContext tenantContext = context.createContext(request);
        final DirectPayment directPayment = directPaymentApi.getPayment(directPaymentIdId, withPluginInfo, pluginProperties, tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(directPayment.getAccountId(), auditMode.getLevel(), tenantContext);
        final DirectPaymentJson result = new DirectPaymentJson(directPayment, accountAuditLogs);

        return Response.status(Response.Status.OK).entity(result).build();
    }

    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    public Response getDirectPayments(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                      @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                      @QueryParam(QUERY_PAYMENT_PLUGIN_NAME) final String pluginName,
                                      @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                      @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);

        final Pagination<DirectPayment> directPayments;
        if (Strings.isNullOrEmpty(pluginName)) {
            directPayments = directPaymentApi.getPayments(offset, limit, pluginProperties, tenantContext);
        } else {
            directPayments = directPaymentApi.getPayments(offset, limit, pluginName, pluginProperties, tenantContext);
        }

        final URI nextPageUri = uriBuilder.nextPage(DirectPaymentResource.class, "getDirectPayments", directPayments.getNextOffset(), limit, ImmutableMap.<String, String>of(QUERY_PAYMENT_METHOD_PLUGIN_NAME, Strings.nullToEmpty(pluginName),
                                                                                                                                                                             QUERY_AUDIT, auditMode.getLevel().toString()));
        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());

        return buildStreamingPaginationResponse(directPayments,
                                                new Function<DirectPayment, DirectPaymentJson>() {
                                                    @Override
                                                    public DirectPaymentJson apply(final DirectPayment directPayment) {
                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(directPayment.getAccountId()) == null) {
                                                            accountsAuditLogs.get().put(directPayment.getAccountId(), auditUserApi.getAccountAuditLogs(directPayment.getAccountId(), auditMode.getLevel(), tenantContext));
                                                        }
                                                        final AccountAuditLogs accountAuditLogs = accountsAuditLogs.get().get(directPayment.getAccountId());
                                                        return new DirectPaymentJson(directPayment, accountAuditLogs);
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response searchDirectPayments(@PathParam("searchKey") final String searchKey,
                                         @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                         @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                         @QueryParam(QUERY_PAYMENT_PLUGIN_NAME) final String pluginName,
                                         @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                         @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);

        // Search the plugin(s)
        final Pagination<DirectPayment> directPayments;
        if (Strings.isNullOrEmpty(pluginName)) {
            directPayments = directPaymentApi.searchPayments(searchKey, offset, limit, pluginProperties, tenantContext);
        } else {
            directPayments = directPaymentApi.searchPayments(searchKey, offset, limit, pluginName, pluginProperties, tenantContext);
        }

        final URI nextPageUri = uriBuilder.nextPage(DirectPaymentResource.class, "searchDirectPayments", directPayments.getNextOffset(), limit, ImmutableMap.<String, String>of("searchKey", searchKey,
                                                                                                                                                                                QUERY_PAYMENT_METHOD_PLUGIN_NAME, Strings.nullToEmpty(pluginName),
                                                                                                                                                                                QUERY_AUDIT, auditMode.getLevel().toString()));
        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());

        return buildStreamingPaginationResponse(directPayments,
                                                new Function<DirectPayment, DirectPaymentJson>() {
                                                    @Override
                                                    public DirectPaymentJson apply(final DirectPayment directPayment) {
                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(directPayment.getAccountId()) == null) {
                                                            accountsAuditLogs.get().put(directPayment.getAccountId(), auditUserApi.getAccountAuditLogs(directPayment.getAccountId(), auditMode.getLevel(), tenantContext));
                                                        }
                                                        final AccountAuditLogs accountAuditLogs = accountsAuditLogs.get().get(directPayment.getAccountId());
                                                        return new DirectPaymentJson(directPayment, accountAuditLogs);
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @POST
    @Path("/{directPaymentId:" + UUID_PATTERN + "}/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response captureAuthorization(final DirectTransactionJson json,
                                         @PathParam("directPaymentId") final String directPaymentIdStr,
                                         @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                         @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                         @HeaderParam(HDR_REASON) final String reason,
                                         @HeaderParam(HDR_COMMENT) final String comment,
                                         @javax.ws.rs.core.Context final UriInfo uriInfo,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID directPaymentId = UUID.fromString(directPaymentIdStr);
        final DirectPayment initialPayment = directPaymentApi.getPayment(directPaymentId, false, pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : Currency.valueOf(json.getCurrency());

        final DirectPayment payment = directPaymentApi.createCapture(account, directPaymentId, json.getAmount(), currency,
                                                                     json.getDirectTransactionExternalKey(), pluginProperties, callContext);
        return uriBuilder.buildResponse(uriInfo, DirectPaymentResource.class, "getDirectPayment", payment.getId());
    }

    @POST
    @Path("/{directPaymentId:" + UUID_PATTERN + "}/" + REFUNDS)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response refundPayment(final DirectTransactionJson json,
                                  @PathParam("directPaymentId") final String directPaymentIdStr,
                                  @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                  @HeaderParam(HDR_REASON) final String reason,
                                  @HeaderParam(HDR_COMMENT) final String comment,
                                  @javax.ws.rs.core.Context final UriInfo uriInfo,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID directPaymentId = UUID.fromString(directPaymentIdStr);
        final DirectPayment initialPayment = directPaymentApi.getPayment(directPaymentId, false, pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);
        final Currency currency = json.getCurrency() == null ? account.getCurrency() : Currency.valueOf(json.getCurrency());

        final DirectPayment payment = directPaymentApi.createRefund(account, directPaymentId, json.getAmount(), currency,
                                                                    json.getDirectTransactionExternalKey(), pluginProperties, callContext);
        return uriBuilder.buildResponse(uriInfo, DirectPaymentResource.class, "getDirectPayment", payment.getId());
    }

    @DELETE
    @Path("/{directPaymentId:" + UUID_PATTERN + "}/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response voidPayment(final DirectTransactionJson json,
                                @PathParam("directPaymentId") final String directPaymentIdStr,
                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final UriInfo uriInfo,
                                @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        final UUID directPaymentId = UUID.fromString(directPaymentIdStr);
        final DirectPayment initialPayment = directPaymentApi.getPayment(directPaymentId, false, pluginProperties, callContext);

        final Account account = accountUserApi.getAccountById(initialPayment.getAccountId(), callContext);

        final DirectPayment payment = directPaymentApi.createVoid(account, directPaymentId, json.getDirectTransactionExternalKey(), pluginProperties, callContext);
        return uriBuilder.buildResponse(uriInfo, DirectPaymentResource.class, "getDirectPayment", payment.getId());
    }
}
