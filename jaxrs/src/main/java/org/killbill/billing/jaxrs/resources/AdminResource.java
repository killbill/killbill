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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.json.AdminPaymentJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.AdminPaymentApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.RecordIdApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.dao.SystemTags;
import org.killbill.clock.Clock;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.ADMIN_PATH)
@Api(value = JaxrsResource.ADMIN_PATH, description = "Admin operations (will require special privileges)")
public class AdminResource extends JaxRsResourceBase {

    private static final String OK = "OK";

    private final AdminPaymentApi adminPaymentApi;
    private final InvoiceUserApi invoiceUserApi;
    private final TenantUserApi tenantApi;
    private final CacheManager cacheManager;
    private final RecordIdApi recordIdApi;

    @Inject
    public AdminResource(final JaxrsUriBuilder uriBuilder, final TagUserApi tagUserApi, final CustomFieldUserApi customFieldUserApi, final AuditUserApi auditUserApi, final AccountUserApi accountUserApi, final PaymentApi paymentApi, final AdminPaymentApi adminPaymentApi, final InvoiceUserApi invoiceUserApi, final CacheManager cacheManager, final TenantUserApi tenantApi, final RecordIdApi recordIdApi, final Clock clock, final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, null, clock, context);
        this.adminPaymentApi = adminPaymentApi;
        this.invoiceUserApi = invoiceUserApi;
        this.tenantApi = tenantApi;
        this.recordIdApi = recordIdApi;
        this.cacheManager = cacheManager;
    }

    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/payments/{paymentId:" + UUID_PATTERN + "}/transactions/{paymentTransactionId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Update existing paymentTransaction and associated payment state")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid account data supplied")})
    public Response updatePaymentTransactionState(final AdminPaymentJson json,
                                                  @PathParam("paymentId") final String paymentIdStr,
                                                  @PathParam("paymentTransactionId") final String paymentTransactionIdStr,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {

        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final Payment payment = paymentApi.getPayment(UUID.fromString(paymentIdStr), false, false, ImmutableList.<PluginProperty>of(), callContext);

        final UUID paymentTransactionId = UUID.fromString(paymentTransactionIdStr);

        final PaymentTransaction paymentTransaction = Iterables.tryFind(payment.getTransactions(), new Predicate<PaymentTransaction>() {
            @Override
            public boolean apply(final PaymentTransaction input) {
                return input.getId().equals(paymentTransactionId);
            }
        }).orNull();

        adminPaymentApi.fixPaymentTransactionState(payment, paymentTransaction, TransactionStatus.valueOf(json.getTransactionStatus()),
                                                   json.getLastSuccessPaymentState(), json.getCurrentPaymentStateName(), ImmutableList.<PluginProperty>of(), callContext);
        return Response.status(Status.OK).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/invoices")
    @ApiOperation(value = "Trigger an invoice generation for all parked accounts")
    @ApiResponses(value = {})
    public Response triggerInvoiceGenerationForParkedAccounts(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                                              @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                              @HeaderParam(HDR_REASON) final String reason,
                                                              @HeaderParam(HDR_COMMENT) final String comment,
                                                              @javax.ws.rs.core.Context final HttpServletRequest request) {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        // TODO Consider adding a real invoice API post 0.18.x
        final Pagination<Tag> tags = tagUserApi.searchTags(SystemTags.PARK_TAG_DEFINITION_NAME, offset, limit, callContext);

        final StreamingOutput json = new StreamingOutput() {
            @Override
            public void write(final OutputStream output) throws IOException, WebApplicationException {
                final JsonGenerator generator = mapper.getFactory().createGenerator(output);
                generator.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

                generator.writeStartObject();
                for (final Tag tag : tags) {
                    final UUID accountId = tag.getObjectId();
                    try {
                        invoiceUserApi.triggerInvoiceGeneration(accountId, clock.getUTCToday(), null, callContext);
                        generator.writeStringField(accountId.toString(), OK);
                    } catch (final InvoiceApiException e) {
                        if (e.getCode() != ErrorCode.INVOICE_NOTHING_TO_DO.getCode()) {
                            log.warn("Unable to trigger invoice generation for accountId='{}'", accountId);
                        }
                        generator.writeStringField(accountId.toString(), ErrorCode.fromCode(e.getCode()).toString());
                    }
                }
                generator.writeEndObject();
                generator.close();
            }
        };

        final URI nextPageUri = uriBuilder.nextPage(AdminResource.class,
                                                    "triggerInvoiceGenerationForParkedAccounts",
                                                    tags.getNextOffset(),
                                                    limit,
                                                    ImmutableMap.<String, String>of());
        return Response.status(Status.OK)
                       .entity(json)
                       .header(HDR_PAGINATION_CURRENT_OFFSET, tags.getCurrentOffset())
                       .header(HDR_PAGINATION_NEXT_OFFSET, tags.getNextOffset())
                       .header(HDR_PAGINATION_TOTAL_NB_RECORDS, tags.getTotalNbRecords())
                       .header(HDR_PAGINATION_MAX_NB_RECORDS, tags.getMaxNbRecords())
                       .header(HDR_PAGINATION_NEXT_PAGE_URI, nextPageUri)
                       .build();
    }

    @DELETE
    @Path("/" + CACHE)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Invalidates the given Cache if specified, otherwise invalidates all caches")
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Cache name does not exist or is not alive")})
    public Response invalidatesCache(@QueryParam("cacheName") final String cacheName,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) {
        if (null != cacheName && !cacheName.isEmpty()) {
            final Ehcache cache = cacheManager.getEhcache(cacheName);
            // check if cache is null
            if (cache == null) {
                log.warn("Cache for specified cacheName='{}' does not exist or is not alive", cacheName);
                return Response.status(Status.BAD_REQUEST).build();
            }
            // Clear given cache
            cache.removeAll();
        } else {
            // if not given a specific cacheName, clear all
            cacheManager.clearAll();
        }
        return Response.status(Status.OK).build();
    }

    @DELETE
    @Path("/" + CACHE + "/" + ACCOUNTS + "/{accountId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Invalidates Caches per account level")
    @ApiResponses(value = {})
    public Response invalidatesCacheByAccount(@PathParam("accountId") final String accountId,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) {

        // clear account-record-id cache by accountId
        final Ehcache accountRecordIdCache = cacheManager.getEhcache(CacheType.ACCOUNT_RECORD_ID.getCacheName());
        accountRecordIdCache.remove(accountId);

        // clear account-immutable cache by accountId
        final Ehcache accountImmutableCache = cacheManager.getEhcache(CacheType.ACCOUNT_IMMUTABLE.getCacheName());
        accountImmutableCache.remove(UUID.fromString(accountId));

        // clear account-bcd cache by accountId
        final Ehcache accountBcdCache = cacheManager.getEhcache(CacheType.ACCOUNT_BCD.getCacheName());
        accountBcdCache.remove(UUID.fromString(accountId));

        return Response.status(Status.OK).build();
    }

    @DELETE
    @Path("/" + CACHE + "/" + TENANTS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Invalidates Caches per tenant level")
    @ApiResponses(value = {})
    public Response invalidatesCacheByTenant(@QueryParam("tenantApiKey") final String tenantApiKey,
                                             @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {

        // creating Tenant Context from Request
        TenantContext tenantContext = context.createContext(request);

        Tenant currentTenant = tenantApi.getTenantById(tenantContext.getTenantId());

        // getting Tenant Record Id
        Long tenantRecordId = recordIdApi.getRecordId(tenantContext.getTenantId(), ObjectType.TENANT, tenantContext);

        // clear tenant-record-id cache by tenantId
        final Ehcache tenantRecordIdCache = cacheManager.getEhcache(CacheType.TENANT_RECORD_ID.getCacheName());
        tenantRecordIdCache.remove(currentTenant.getId().toString());

        // clear tenant-payment-state-machine-config cache by tenantRecordId
        final Ehcache tenantPaymentStateMachineConfigCache = cacheManager.getEhcache(CacheType.TENANT_PAYMENT_STATE_MACHINE_CONFIG.getCacheName());
        removeCacheByKey(tenantPaymentStateMachineConfigCache, tenantRecordId.toString());

        // clear tenant cache by tenantApiKey
        final Ehcache tenantCache = cacheManager.getEhcache(CacheType.TENANT.getCacheName());
        tenantCache.remove(currentTenant.getApiKey());

        // clear tenant-kv cache by tenantRecordId
        final Ehcache tenantKvCache = cacheManager.getEhcache(CacheType.TENANT_KV.getCacheName());
        removeCacheByKey(tenantKvCache, tenantRecordId.toString());

        // clear tenant-config cache by tenantRecordId
        final Ehcache tenantConfigCache = cacheManager.getEhcache(CacheType.TENANT_CONFIG.getCacheName());
        tenantConfigCache.remove(tenantRecordId);

        // clear tenant-overdue-config cache by tenantRecordId
        final Ehcache tenantOverdueConfigCache = cacheManager.getEhcache(CacheType.TENANT_OVERDUE_CONFIG.getCacheName());
        tenantOverdueConfigCache.remove(tenantRecordId);

        // clear tenant-catalog cache by tenantRecordId
        final Ehcache tenantCatalogCache = cacheManager.getEhcache(CacheType.TENANT_CATALOG.getCacheName());
        tenantCatalogCache.remove(tenantRecordId);

        return Response.status(Status.OK).build();
    }

    private void removeCacheByKey(final Ehcache tenantCache, final String tenantRecordId) {
        for (Object key : tenantCache.getKeys()) {
            if (null != key && key.toString().endsWith("::" + tenantRecordId)) {
                tenantCache.remove(key);
            }
        }
    }

}
