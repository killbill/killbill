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

package org.killbill.billing.jaxrs.resources;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.clock.Clock;
import org.killbill.billing.jaxrs.json.PaymentMethodJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.PAYMENT_METHODS_PATH)
public class PaymentMethodResource extends JaxRsResourceBase {

    private final PaymentApi paymentApi;

    @Inject
    public PaymentMethodResource(final PaymentApi paymentApi,
                                 final AccountUserApi accountUserApi,
                                 final JaxrsUriBuilder uriBuilder,
                                 final TagUserApi tagUserApi,
                                 final CustomFieldUserApi customFieldUserApi,
                                 final AuditUserApi auditUserApi,
                                 final Clock clock,
                                 final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.paymentApi = paymentApi;
    }

    @GET
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getPaymentMethod(@PathParam("paymentMethodId") final String paymentMethodId,
                                     @QueryParam(QUERY_PAYMENT_METHOD_PLUGIN_INFO) @DefaultValue("false") final Boolean withPluginInfo,
                                     @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, PaymentApiException {
        final TenantContext tenantContext = context.createContext(request);

        final PaymentMethod paymentMethod = paymentApi.getPaymentMethodById(UUID.fromString(paymentMethodId), false, withPluginInfo, tenantContext);
        final Account account = accountUserApi.getAccountById(paymentMethod.getAccountId(), tenantContext);
        final AccountAuditLogs accountAuditLogs = auditUserApi.getAccountAuditLogs(paymentMethod.getAccountId(), auditMode.getLevel(), tenantContext);
        final PaymentMethodJson json = PaymentMethodJson.toPaymentMethodJson(account, paymentMethod, accountAuditLogs);

        return Response.status(Status.OK).entity(json).build();
    }

    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    public Response getPaymentMethods(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                      @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                      @QueryParam(QUERY_PAYMENT_METHOD_PLUGIN_NAME) final String pluginName,
                                      @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final TenantContext tenantContext = context.createContext(request);

        final Pagination<PaymentMethod> paymentMethods;
        if (Strings.isNullOrEmpty(pluginName)) {
            paymentMethods = paymentApi.getPaymentMethods(offset, limit, tenantContext);
        } else {
            paymentMethods = paymentApi.getPaymentMethods(offset, limit, pluginName, tenantContext);
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
                                                                log.warn("Unable to retrieve account", e);
                                                                return null;
                                                            }
                                                        }

                                                        return PaymentMethodJson.toPaymentMethodJson(accounts.get(paymentMethod.getAccountId()), paymentMethod, accountsAuditLogs.get().get(paymentMethod.getAccountId()));
                                                    }
                                                },
                                                nextPageUri);
    }

    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response searchPaymentMethods(@PathParam("searchKey") final String searchKey,
                                         @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                         @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                         @QueryParam(QUERY_PAYMENT_METHOD_PLUGIN_NAME) final String pluginName,
                                         @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                         @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final TenantContext tenantContext = context.createContext(request);

        // Search the plugin(s)
        final Pagination<PaymentMethod> paymentMethods;
        if (Strings.isNullOrEmpty(pluginName)) {
            paymentMethods = paymentApi.searchPaymentMethods(searchKey, offset, limit, tenantContext);
        } else {
            paymentMethods = paymentApi.searchPaymentMethods(searchKey, offset, limit, pluginName, tenantContext);
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
                                                                log.warn("Unable to retrieve account", e);
                                                                return null;
                                                            }
                                                        }

                                                        return PaymentMethodJson.toPaymentMethodJson(accounts.get(paymentMethod.getAccountId()), paymentMethod, accountsAuditLogs.get().get(paymentMethod.getAccountId()));
                                                    }
                                                },
                                                nextPageUri);
    }

    @DELETE
    @Produces(APPLICATION_JSON)
    @Path("/{paymentMethodId:" + UUID_PATTERN + "}")
    public Response deletePaymentMethod(@PathParam("paymentMethodId") final String paymentMethodId,
                                        @QueryParam(QUERY_DELETE_DEFAULT_PM_WITH_AUTO_PAY_OFF) @DefaultValue("false") final Boolean deleteDefaultPaymentMethodWithAutoPayOff,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException, AccountApiException {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final PaymentMethod paymentMethod = paymentApi.getPaymentMethodById(UUID.fromString(paymentMethodId), false, false, callContext);
        final Account account = accountUserApi.getAccountById(paymentMethod.getAccountId(), callContext);

        paymentApi.deletedPaymentMethod(account, UUID.fromString(paymentMethodId), deleteDefaultPaymentMethodWithAutoPayOff, callContext);

        return Response.status(Status.OK).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.PAYMENT_METHOD;
    }
}
