/*
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.UUID;

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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.jaxrs.json.AdminPaymentJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.AdminPaymentApi;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.server.healthchecks.KillbillHealthcheck;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.RecordIdApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.config.tenant.PerTenantConfig;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.dao.SystemTags;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.BusEventWithMetadata;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

@Singleton
@Path(JaxrsResource.ADMIN_PATH)
@Api(value = JaxrsResource.ADMIN_PATH, description = "Admin operations (will require special privileges)", tags="Admin")
public class AdminResource extends JaxRsResourceBase {

    private static final String OK = "OK";

    private final AdminPaymentApi adminPaymentApi;
    private final InvoiceUserApi invoiceUserApi;
    private final TenantUserApi tenantApi;
    private final CacheControllerDispatcher cacheControllerDispatcher;
    private final RecordIdApi recordIdApi;
    private final PersistentBus persistentBus;
    private final NotificationQueueService notificationQueueService;
    private final KillbillHealthcheck killbillHealthcheck;

    @Inject
    public AdminResource(final JaxrsUriBuilder uriBuilder,
                         final TagUserApi tagUserApi,
                         final CustomFieldUserApi customFieldUserApi,
                         final AuditUserApi auditUserApi,
                         final AccountUserApi accountUserApi,
                         final PaymentApi paymentApi,
                         final InvoicePaymentApi invoicePaymentApi,
                         final AdminPaymentApi adminPaymentApi,
                         final InvoiceUserApi invoiceUserApi,
                         final CacheControllerDispatcher cacheControllerDispatcher,
                         final TenantUserApi tenantApi,
                         final RecordIdApi recordIdApi,
                         final PersistentBus persistentBus,
                         final NotificationQueueService notificationQueueService,
                         final KillbillHealthcheck killbillHealthcheck,
                         final Clock clock,
                         final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
        this.adminPaymentApi = adminPaymentApi;
        this.invoiceUserApi = invoiceUserApi;
        this.tenantApi = tenantApi;
        this.recordIdApi = recordIdApi;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
        this.persistentBus = persistentBus;
        this.notificationQueueService = notificationQueueService;
        this.killbillHealthcheck = killbillHealthcheck;
    }

    @GET
    @Path("/queues")
    @Produces(APPLICATION_OCTET_STREAM)
    @ApiOperation(value = "Get queues entries", response = Response.class)
    @ApiResponses(value = {@ApiResponse(code = 200, message = "Success"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied"),
                           @ApiResponse(code = 404, message = "Account not found")})
    public Response getQueueEntries(@QueryParam("accountId") final UUID accountId,
                                    @QueryParam("queueName") final String queueName,
                                    @QueryParam("serviceName") final String serviceName,
                                    @QueryParam("withHistory") @DefaultValue("true") final Boolean withHistory,
                                    @QueryParam("minDate") final String minDateOrNull,
                                    @QueryParam("maxDate") final String maxDateOrNull,
                                    @QueryParam("withInProcessing") @DefaultValue("true") final Boolean withInProcessing,
                                    @QueryParam("withBusEvents") @DefaultValue("true") final Boolean withBusEvents,
                                    @QueryParam("withNotifications") @DefaultValue("true") final Boolean withNotifications,
                                    @javax.ws.rs.core.Context final HttpServletRequest request) {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Long tenantRecordId = recordIdApi.getRecordId(tenantContext.getTenantId(), ObjectType.TENANT, tenantContext);
        final Long accountRecordId = accountId == null ? null : recordIdApi.getRecordId(accountId, ObjectType.ACCOUNT, tenantContext);

        // Limit search results by default
        final DateTime minDate = Strings.isNullOrEmpty(minDateOrNull) ? clock.getUTCNow().minusDays(2) : DATE_TIME_FORMATTER.parseDateTime(minDateOrNull).toDateTime(DateTimeZone.UTC);
        final DateTime maxDate = Strings.isNullOrEmpty(maxDateOrNull) ? clock.getUTCNow().plusDays(2) : DATE_TIME_FORMATTER.parseDateTime(maxDateOrNull).toDateTime(DateTimeZone.UTC);

        final StreamingOutput json = new StreamingOutput() {
            @Override
            public void write(final OutputStream output) throws IOException, WebApplicationException {
                Iterator<BusEventWithMetadata<BusEvent>> busEventsIterator = null;
                Iterator<NotificationEventWithMetadata<NotificationEvent>> notificationsIterator = null;

                try {
                    final JsonGenerator generator = mapper.getFactory().createGenerator(output);
                    generator.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

                    generator.writeStartObject();

                    if (withBusEvents) {
                        generator.writeFieldName("busEvents");
                        generator.writeStartArray();
                        busEventsIterator = getBusEvents(withInProcessing, withHistory, minDate, maxDate, accountRecordId, tenantRecordId).iterator();
                        while (busEventsIterator.hasNext()) {
                            final BusEventWithMetadata<BusEvent> busEvent = busEventsIterator.next();
                            generator.writeObject(new BusEventWithRichMetadata(busEvent));
                        }
                        generator.writeEndArray();
                    }

                    if (withNotifications) {
                        generator.writeFieldName("notifications");
                        generator.writeStartArray();

                        notificationsIterator = getNotifications(queueName, serviceName, withInProcessing, withHistory, minDate, maxDate, accountRecordId, tenantRecordId).iterator();
                        while (notificationsIterator.hasNext()) {
                            final NotificationEventWithMetadata<NotificationEvent> notification = notificationsIterator.next();
                            generator.writeObject(notification);
                        }
                        generator.writeEndArray();
                    }

                    generator.writeEndObject();
                    generator.close();
                } finally {
                    // In case the client goes away (IOException), make sure to close the underlying DB connection
                    if (busEventsIterator != null) {
                        while (busEventsIterator.hasNext()) {
                            busEventsIterator.next();
                        }
                    }
                    if (notificationsIterator != null) {
                        while (notificationsIterator.hasNext()) {
                            notificationsIterator.next();
                        }
                    }
                }
            }
        };

        return Response.status(Status.OK).entity(json).build();
    }

    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/payments/{paymentId:" + UUID_PATTERN + "}/transactions/{paymentTransactionId:" + UUID_PATTERN + "}")
    @ApiOperation(value = "Update existing paymentTransaction and associated payment state")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account data supplied")})
    public Response updatePaymentTransactionState(@PathParam("paymentId") final UUID paymentId,
                                                  @PathParam("paymentTransactionId") final UUID paymentTransactionId,
                                                  final AdminPaymentJson json,
                                                  @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                  @HeaderParam(HDR_REASON) final String reason,
                                                  @HeaderParam(HDR_COMMENT) final String comment,
                                                  @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {

        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        final Payment payment = paymentApi.getPayment(paymentId, false, false, ImmutableList.<PluginProperty>of(), callContext);
        final PaymentTransaction paymentTransaction = Iterables.tryFind(payment.getTransactions(), new Predicate<PaymentTransaction>() {
            @Override
            public boolean apply(final PaymentTransaction input) {
                return input.getId().equals(paymentTransactionId);
            }
        }).orNull();

        adminPaymentApi.fixPaymentTransactionState(payment, paymentTransaction, TransactionStatus.valueOf(json.getTransactionStatus()),
                                                   json.getLastSuccessPaymentState(), json.getCurrentPaymentStateName(), ImmutableList.<PluginProperty>of(), callContext);
        return Response.status(Status.NO_CONTENT).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Path("/invoices")
    @ApiOperation(value = "Trigger an invoice generation for all parked accounts")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "Successful operation")})
    public Response triggerInvoiceGenerationForParkedAccounts(@QueryParam(QUERY_SEARCH_OFFSET) @DefaultValue("0") final Long offset,
                                                              @QueryParam(QUERY_SEARCH_LIMIT) @DefaultValue("100") final Long limit,
                                                              @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                                              @HeaderParam(HDR_REASON) final String reason,
                                                              @HeaderParam(HDR_COMMENT) final String comment,
                                                              @javax.ws.rs.core.Context final HttpServletRequest request) {
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);

        // TODO Consider adding a real invoice API post 0.18.x
        final Pagination<Tag> tags = tagUserApi.searchTags(SystemTags.PARK_TAG_DEFINITION_NAME, offset, limit, callContext);
        final Iterator<Tag> iterator = tags.iterator();

        final StreamingOutput json = new StreamingOutput() {
            @Override
            public void write(final OutputStream output) throws IOException, WebApplicationException {
                try {
                    final JsonGenerator generator = mapper.getFactory().createGenerator(output);
                    generator.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

                    generator.writeStartObject();
                    while (iterator.hasNext()) {
                        final Tag tag = iterator.next();
                        final UUID accountId = tag.getObjectId();
                        try {
                            invoiceUserApi.triggerInvoiceGeneration(accountId, clock.getUTCToday(), callContext);
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
                } finally {
                    // In case the client goes away (IOException), make sure to close the underlying DB connection
                    tags.close();
                }
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
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Cache name does not exist or is not alive")})
    public Response invalidatesCache(@QueryParam("cacheName") final String cacheName,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) {
        if (null != cacheName && !cacheName.isEmpty()) {
            // Clear given cache
            final CacheType cacheType = CacheType.findByName(cacheName);
            if (cacheType != null) {
                cacheControllerDispatcher.getCacheController(cacheType).removeAll();
            } else {
                log.warn("Cache for specified cacheName='{}' does not exist or is not alive", cacheName);
                return Response.status(Status.BAD_REQUEST).build();
            }
        } else {
            // if not given a specific cacheName, clear all
            cacheControllerDispatcher.clearAll();
        }
        return Response.status(Status.NO_CONTENT).build();
    }

    @DELETE
    @Path("/" + CACHE + "/" + ACCOUNTS + "/{accountId:" + UUID_PATTERN + "}/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Invalidates Caches per account level")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation"),
                           @ApiResponse(code = 400, message = "Invalid account id supplied")})
    public Response invalidatesCacheByAccount(@PathParam("accountId") final UUID accountId,
                                              @javax.ws.rs.core.Context final HttpServletRequest request) {

        final TenantContext tenantContext = context.createTenantContextWithAccountId(accountId, request);
        final Long accountRecordId = recordIdApi.getRecordId(accountId, ObjectType.ACCOUNT, tenantContext);

        // clear account-record-id cache by accountId (note: String!)
        final CacheController<String, Long> accountRecordIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_RECORD_ID);
        accountRecordIdCacheController.remove(accountId.toString());

        // clear account-immutable cache by account record id
        final CacheController<Long, ImmutableAccountData> accountImmutableCacheController = cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_IMMUTABLE);
        accountImmutableCacheController.remove(accountRecordId);

        // clear account-bcd cache by accountId (note: UUID!)
        final CacheController<UUID, Integer> accountBCDCacheController = cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_BCD);
        accountBCDCacheController.remove(accountId);

        return Response.status(Status.NO_CONTENT).build();
    }

    @DELETE
    @Path("/" + CACHE + "/" + TENANTS)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Invalidates Caches per tenant level")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation")})
    public Response invalidatesCacheByTenant(@javax.ws.rs.core.Context final HttpServletRequest request) throws TenantApiException {

        // creating Tenant Context from Request
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);

        final Tenant currentTenant = tenantApi.getTenantById(tenantContext.getTenantId());

        // getting Tenant Record Id
        final Long tenantRecordId = recordIdApi.getRecordId(tenantContext.getTenantId(), ObjectType.TENANT, tenantContext);

        final Function<String, Boolean> tenantKeysMatcher = new Function<String, Boolean>() {
            @Override
            public Boolean apply(@Nullable final String key) {
                return key != null && key.endsWith("::" + tenantRecordId);
            }
        };

        // clear tenant-record-id cache by tenantId
        final CacheController<String, Long> tenantRecordIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT_RECORD_ID);
        tenantRecordIdCacheController.remove(currentTenant.getId().toString());

        // clear tenant-payment-state-machine-config cache by tenantRecordId
        final CacheController<String, Object> tenantPaymentStateMachineConfigCacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT_PAYMENT_STATE_MACHINE_CONFIG);
        tenantPaymentStateMachineConfigCacheController.remove(tenantKeysMatcher);

        // clear tenant cache by tenantApiKey
        final CacheController<String, Tenant> tenantCacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT);
        tenantCacheController.remove(currentTenant.getApiKey());

        // clear tenant-kv cache by tenantRecordId
        final CacheController<String, String> tenantKVCacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT_KV);
        tenantKVCacheController.remove(tenantKeysMatcher);

        // clear tenant-config cache by tenantRecordId
        final CacheController<Long, PerTenantConfig> tenantConfigCacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT_CONFIG);
        tenantConfigCacheController.remove(tenantRecordId);

        // clear tenant-overdue-config cache by tenantRecordId
        final CacheController<Long, Object> tenantOverdueConfigCacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT_OVERDUE_CONFIG);
        tenantOverdueConfigCacheController.remove(tenantRecordId);

        // clear tenant-catalog cache by tenantRecordId
        final CacheController<Long, Catalog> tenantCatalogCacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT_CATALOG);
        tenantCatalogCacheController.remove(tenantRecordId);

        return Response.status(Status.NO_CONTENT).build();
    }

    @PUT
    @Path("/" + HEALTHCHECK)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Put the host back into rotation")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation")})
    public Response putInRotation(@javax.ws.rs.core.Context final HttpServletRequest request) {
        killbillHealthcheck.putInRotation();
        return Response.status(Status.NO_CONTENT).build();
    }

    @DELETE
    @Path("/" + HEALTHCHECK)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Put the host out of rotation")
    @ApiResponses(value = {@ApiResponse(code = 204, message = "Successful operation")})
    public Response putOutOfRotation(@javax.ws.rs.core.Context final HttpServletRequest request) {
        killbillHealthcheck.putOutOfRotation();
        return Response.status(Status.NO_CONTENT).build();
    }

    private Iterable<NotificationEventWithMetadata<NotificationEvent>> getNotifications(@Nullable final String queueName,
                                                                                        @Nullable final String serviceName,
                                                                                        final boolean includeInProcessing,
                                                                                        final boolean includeHistory,
                                                                                        @Nullable final DateTime minEffectiveDate,
                                                                                        @Nullable final DateTime maxEffectiveDate,
                                                                                        @Nullable final Long accountRecordId,
                                                                                        final Long tenantRecordId) {
        Iterable<NotificationEventWithMetadata<NotificationEvent>> notifications = ImmutableList.<NotificationEventWithMetadata<NotificationEvent>>of();
        for (final NotificationQueue notificationQueue : notificationQueueService.getNotificationQueues()) {
            if (queueName != null && !queueName.equals(notificationQueue.getQueueName())) {
                continue;
            } else if (serviceName != null && !serviceName.equals(notificationQueue.getServiceName())) {
                continue;
            }

            if (includeInProcessing) {
                if (accountRecordId != null) {
                    notifications = Iterables.<NotificationEventWithMetadata<NotificationEvent>>concat(notifications,
                                                                                                       notificationQueue.getFutureOrInProcessingNotificationForSearchKeys(accountRecordId, tenantRecordId));
                } else {
                    notifications = Iterables.<NotificationEventWithMetadata<NotificationEvent>>concat(notifications,
                                                                                                       notificationQueue.getFutureOrInProcessingNotificationForSearchKey2(maxEffectiveDate, tenantRecordId));
                }
            } else {
                if (accountRecordId != null) {
                    notifications = Iterables.<NotificationEventWithMetadata<NotificationEvent>>concat(notifications,
                                                                                                       notificationQueue.getFutureNotificationForSearchKeys(accountRecordId, tenantRecordId));
                } else {
                    notifications = Iterables.<NotificationEventWithMetadata<NotificationEvent>>concat(notifications,
                                                                                                       notificationQueue.getFutureNotificationForSearchKey2(maxEffectiveDate, tenantRecordId));
                }
            }

            if (includeHistory) {
                if (accountRecordId != null) {
                    notifications = Iterables.<NotificationEventWithMetadata<NotificationEvent>>concat(notificationQueue.getHistoricalNotificationForSearchKeys(accountRecordId, tenantRecordId),
                                                                                                       notifications);
                } else {
                    notifications = Iterables.<NotificationEventWithMetadata<NotificationEvent>>concat(notificationQueue.getHistoricalNotificationForSearchKey2(minEffectiveDate, tenantRecordId),
                                                                                                       notifications);
                }
            }
        }

        // Note: entries are properly ordered by queue, but not cross queues unfortunately
        return notifications;
    }

    private Iterable<BusEventWithMetadata<BusEvent>> getBusEvents(final boolean includeInProcessing,
                                                                  final boolean includeHistory,
                                                                  @Nullable final DateTime minCreatedDate,
                                                                  @Nullable final DateTime maxCreatedDate,
                                                                  @Nullable final Long accountRecordId,
                                                                  final Long tenantRecordId) {
        Iterable<BusEventWithMetadata<BusEvent>> busEvents = ImmutableList.<BusEventWithMetadata<BusEvent>>of();
        if (includeInProcessing) {
            if (accountRecordId != null) {
                busEvents = Iterables.<BusEventWithMetadata<BusEvent>>concat(busEvents,
                                                                             persistentBus.getAvailableOrInProcessingBusEventsForSearchKeys(accountRecordId, tenantRecordId));
            } else {
                busEvents = Iterables.<BusEventWithMetadata<BusEvent>>concat(busEvents,
                                                                             persistentBus.getAvailableOrInProcessingBusEventsForSearchKey2(maxCreatedDate, tenantRecordId));
            }
        } else {
            if (accountRecordId != null) {
                busEvents = Iterables.<BusEventWithMetadata<BusEvent>>concat(busEvents,
                                                                             persistentBus.getAvailableBusEventsForSearchKeys(accountRecordId, tenantRecordId));
            } else {
                busEvents = Iterables.<BusEventWithMetadata<BusEvent>>concat(busEvents,
                                                                             persistentBus.getAvailableBusEventsForSearchKey2(maxCreatedDate, tenantRecordId));
            }
        }

        if (includeHistory) {
            if (accountRecordId != null) {
                busEvents = Iterables.<BusEventWithMetadata<BusEvent>>concat(persistentBus.getHistoricalBusEventsForSearchKeys(accountRecordId, tenantRecordId),
                                                                             busEvents);
            } else {
                busEvents = Iterables.<BusEventWithMetadata<BusEvent>>concat(persistentBus.getHistoricalBusEventsForSearchKey2(minCreatedDate, tenantRecordId),
                                                                             busEvents);
            }
        }

        // Note: entries are properly ordered by queue, but not cross queues unfortunately
        return busEvents;
    }

    private class BusEventWithRichMetadata extends BusEventWithMetadata<BusEvent> {

        private final String className;

        public BusEventWithRichMetadata(final BusEventWithMetadata<BusEvent> event) {
            super(event.getRecordId(), event.getUserToken(), event.getCreatedDate(), event.getSearchKey1(), event.getSearchKey2(), event.getEvent());
            this.className = event.getEvent().getClass().getName();
        }

        public String getClassName() {
            return className;
        }
    }
}
