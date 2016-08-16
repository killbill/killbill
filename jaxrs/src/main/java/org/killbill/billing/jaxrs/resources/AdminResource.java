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

import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.account.api.AccountUserApi;
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
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.clock.Clock;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
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

    private final AdminPaymentApi adminPaymentApi;
    private final TenantUserApi tenantApi;
    private final CacheManager cacheManager;

    @Inject
    public AdminResource(final JaxrsUriBuilder uriBuilder, final TagUserApi tagUserApi, final CustomFieldUserApi customFieldUserApi, final AuditUserApi auditUserApi, final AccountUserApi accountUserApi, final PaymentApi paymentApi, final AdminPaymentApi adminPaymentApi, final CacheManager cacheManager, final TenantUserApi tenantApi, final Clock clock, final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, null, clock, context);
        this.adminPaymentApi = adminPaymentApi;
        this.tenantApi = tenantApi;
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
        }
        else {
            // if not given a specific cacheName, clear all
            cacheManager.clearAll();
        }
        return Response.status(Status.OK).build();
    }

    @POST
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

    @POST
    @Path("/" + CACHE + "/" + TENANTS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Invalidates Caches per tenant level")
    @ApiResponses(value = {})
    public Response invalidatesCacheByTenant(@QueryParam("tenantApiKey") final String tenantApiKey,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {

        // getting Tenant information from the given Api Key
        Tenant tenant = tenantApi.getTenantByApiKey(tenantApiKey);

        // clear tenant-record-id cache by tenantId
        final Ehcache tenantRecordIdCache = cacheManager.getEhcache(CacheType.TENANT_RECORD_ID.getCacheName());
        tenantRecordIdCache.remove(tenant.getId().toString());

        // clear tenant-payment-state-machine-config cache by tenantId
        final Ehcache tenantPaymentStateMachineConfigCache = cacheManager.getEhcache(CacheType.TENANT_PAYMENT_STATE_MACHINE_CONFIG.getCacheName());
        tenantPaymentStateMachineConfigCache.remove(tenant.getId().toString());

        // clear tenant cache by tenantId
        final Ehcache tenantCache = cacheManager.getEhcache(CacheType.TENANT.getCacheName());
        tenantCache.remove(tenant.getId().toString());

        // clear tenant-kv cache by tenantId
        final Ehcache tenantKvCache = cacheManager.getEhcache(CacheType.TENANT_KV.getCacheName());
        tenantKvCache.remove(tenant.getId().toString());

        // clear tenant-config cache by tenantId
        final Ehcache tenantConfigCache = cacheManager.getEhcache(CacheType.TENANT_CONFIG.getCacheName());
        tenantConfigCache.remove(tenant.getId().toString());

        // clear tenant-overdue-config cache by tenantId
        final Ehcache tenantOverdueConfigCache = cacheManager.getEhcache(CacheType.TENANT_OVERDUE_CONFIG.getCacheName());
        tenantOverdueConfigCache.remove(tenant.getId().toString());

        // clear tenant-catalog cache by tenantId
        final Ehcache tenantCatalogCache = cacheManager.getEhcache(CacheType.TENANT_CATALOG.getCacheName());
        tenantCatalogCache.remove(tenant.getId().toString());

        return Response.status(Status.OK).build();
    }
}
