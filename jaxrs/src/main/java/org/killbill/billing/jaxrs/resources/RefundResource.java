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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.json.RefundJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.Refund;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.clock.Clock;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.REFUNDS_PATH)
public class RefundResource extends JaxRsResourceBase {

    private final PaymentApi paymentApi;

    @Inject
    public RefundResource(final PaymentApi paymentApi,
                          final JaxrsUriBuilder uriBuilder,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final AuditUserApi auditUserApi,
                          final AccountUserApi accountUserApi,
                          final Clock clock,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
        this.paymentApi = paymentApi;
    }

    @GET
    @Path("/{refundId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getRefund(@PathParam("refundId") final String refundId,
                              @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                              @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                              @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);
        final Refund refund = paymentApi.getRefund(UUID.fromString(refundId), false, pluginProperties, tenantContext);
        final List<AuditLog> auditLogs = auditUserApi.getAuditLogs(refund.getId(), ObjectType.REFUND, auditMode.getLevel(), tenantContext);
        // TODO Return adjusted items
        return Response.status(Status.OK).entity(new RefundJson(refund, null, auditLogs)).build();
    }

    @GET
    @Path("/" + PAGINATION)
    @Produces(APPLICATION_JSON)
    public Response getRefunds(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                               @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                               @QueryParam(QUERY_PAYMENT_PLUGIN_NAME) final String pluginName,
                               @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                               @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                               @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);

        final Pagination<Refund> refunds;
        if (Strings.isNullOrEmpty(pluginName)) {
            refunds = paymentApi.getRefunds(offset, limit, pluginProperties, tenantContext);
        } else {
            refunds = paymentApi.getRefunds(offset, limit, pluginName, pluginProperties, tenantContext);
        }

        final URI nextPageUri = uriBuilder.nextPage(RefundResource.class, "getRefunds", refunds.getNextOffset(), limit, ImmutableMap.<String, String>of(QUERY_PAYMENT_METHOD_PLUGIN_NAME, Strings.nullToEmpty(pluginName),
                                                                                                                                                        QUERY_AUDIT, auditMode.getLevel().toString()));

        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());
        final Map<UUID, UUID> paymentIdAccountIdMappings = new HashMap<UUID, UUID>();
        return buildStreamingPaginationResponse(refunds,
                                                new Function<Refund, RefundJson>() {
                                                    @Override
                                                    public RefundJson apply(final Refund refund) {
                                                        UUID kbAccountId = null;
                                                        if (!AuditLevel.NONE.equals(auditMode.getLevel()) && paymentIdAccountIdMappings.get(refund.getPaymentId()) == null) {
                                                            try {
                                                                kbAccountId = paymentApi.getPayment(refund.getPaymentId(), false, pluginProperties, tenantContext).getAccountId();
                                                                paymentIdAccountIdMappings.put(refund.getPaymentId(), kbAccountId);
                                                            } catch (final PaymentApiException e) {
                                                                log.warn("Unable to retrieve payment for id " + refund.getPaymentId());
                                                            }
                                                        }

                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(kbAccountId) == null) {
                                                            accountsAuditLogs.get().put(kbAccountId, auditUserApi.getAccountAuditLogs(kbAccountId, auditMode.getLevel(), tenantContext));
                                                        }

                                                        final List<AuditLog> auditLogs = accountsAuditLogs.get().get(kbAccountId) == null ? null : accountsAuditLogs.get().get(kbAccountId).getAuditLogsForRefund(refund.getId());
                                                        return new RefundJson(refund, null, auditLogs);
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @GET
    @Path("/" + SEARCH + "/{searchKey:" + ANYTHING_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response searchRefunds(@PathParam("searchKey") final String searchKey,
                                  @QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                  @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                  @QueryParam(QUERY_PAYMENT_PLUGIN_NAME) final String pluginName,
                                  @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                  @QueryParam(QUERY_AUDIT) @DefaultValue("NONE") final AuditMode auditMode,
                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);
        final TenantContext tenantContext = context.createContext(request);

        // Search the plugin(s)
        final Pagination<Refund> refunds;
        if (Strings.isNullOrEmpty(pluginName)) {
            refunds = paymentApi.searchRefunds(searchKey, offset, limit, pluginProperties, tenantContext);
        } else {
            refunds = paymentApi.searchRefunds(searchKey, offset, limit, pluginName, pluginProperties, tenantContext);
        }

        final URI nextPageUri = uriBuilder.nextPage(RefundResource.class, "searchRefunds", refunds.getNextOffset(), limit, ImmutableMap.<String, String>of("searchKey", searchKey,
                                                                                                                                                           QUERY_PAYMENT_METHOD_PLUGIN_NAME, Strings.nullToEmpty(pluginName),
                                                                                                                                                           QUERY_AUDIT, auditMode.getLevel().toString()));

        final AtomicReference<Map<UUID, AccountAuditLogs>> accountsAuditLogs = new AtomicReference<Map<UUID, AccountAuditLogs>>(new HashMap<UUID, AccountAuditLogs>());
        final Map<UUID, UUID> paymentIdAccountIdMappings = new HashMap<UUID, UUID>();
        return buildStreamingPaginationResponse(refunds,
                                                new Function<Refund, RefundJson>() {
                                                    @Override
                                                    public RefundJson apply(final Refund refund) {
                                                        UUID kbAccountId = null;
                                                        if (!AuditLevel.NONE.equals(auditMode.getLevel()) && paymentIdAccountIdMappings.get(refund.getPaymentId()) == null) {
                                                            try {
                                                                kbAccountId = paymentApi.getPayment(refund.getPaymentId(), false, pluginProperties, tenantContext).getAccountId();
                                                                paymentIdAccountIdMappings.put(refund.getPaymentId(), kbAccountId);
                                                            } catch (final PaymentApiException e) {
                                                                log.warn("Unable to retrieve payment for id " + refund.getPaymentId());
                                                            }
                                                        }

                                                        // Cache audit logs per account
                                                        if (accountsAuditLogs.get().get(kbAccountId) == null) {
                                                            accountsAuditLogs.get().put(kbAccountId, auditUserApi.getAccountAuditLogs(kbAccountId, auditMode.getLevel(), tenantContext));
                                                        }

                                                        final List<AuditLog> auditLogs = accountsAuditLogs.get().get(kbAccountId) == null ? null : accountsAuditLogs.get().get(kbAccountId).getAuditLogsForRefund(refund.getId());
                                                        return new RefundJson(refund, null, auditLogs);
                                                    }
                                                },
                                                nextPageUri
                                               );
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.REFUND;
    }
}
