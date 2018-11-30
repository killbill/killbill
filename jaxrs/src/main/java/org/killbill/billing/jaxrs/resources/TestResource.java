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

import java.util.Iterator;

import javax.inject.Inject;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.RecordIdApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.BusEventWithMetadata;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Iterables;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

//
// Test endpoint that should not be enabled on a production system.
// The clock manipulation will only work if the ClockMock instance was injected
// throughout the system; if not it will throw 500 (UnsupportedOperationException)
//
// Note that moving the clock back and forth on a running system may cause weird side effects,
// so to be used with great caution.
//
//
@Path(JaxrsResource.TEST_PATH)
@Api(value = JaxrsResource.TEST_PATH, description = "Operations for testing", hidden=true)
public class TestResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(TestResource.class);
    private static final int MILLIS_IN_SEC = 1000;

    private final PersistentBus persistentBus;
    private final NotificationQueueService notificationQueueService;
    private final RecordIdApi recordIdApi;
    private final TenantUserApi tenantApi;
    private final CatalogUserApi catalogUserApi;
    private final CacheControllerDispatcher cacheControllerDispatcher;

    @Inject
    public TestResource(final JaxrsUriBuilder uriBuilder, final TagUserApi tagUserApi, final CustomFieldUserApi customFieldUserApi,
                        final AuditUserApi auditUserApi, final AccountUserApi accountUserApi, final RecordIdApi recordIdApi,
                        final PersistentBus persistentBus, final NotificationQueueService notificationQueueService, final PaymentApi paymentApi,
                        final InvoicePaymentApi invoicePaymentApi, final TenantUserApi tenantApi, final CatalogUserApi catalogUserApi,
                        final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
        this.persistentBus = persistentBus;
        this.notificationQueueService = notificationQueueService;
        this.recordIdApi = recordIdApi;
        this.catalogUserApi = catalogUserApi;
        this.tenantApi = tenantApi;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
    }

    public final class ClockResource {

        private final DateTime currentUtcTime;
        private final String timeZone;
        private final LocalDate localDate;

        @JsonCreator
        public ClockResource(@JsonProperty("currentUtcTime") final DateTime currentUtcTime,
                             @JsonProperty("timeZone") final String timeZone,
                             @JsonProperty("localDate") final LocalDate localDate) {

            this.currentUtcTime = currentUtcTime;
            this.timeZone = timeZone;
            this.localDate = localDate;
        }

        public DateTime getCurrentUtcTime() {
            return currentUtcTime;
        }

        public String getTimeZone() {
            return timeZone;
        }

        public LocalDate getLocalDate() {
            return localDate;
        }
    }

    @GET
    @Path("/queues")
    @ApiOperation(value = "Wait for all available bus events and notifications to be processed")
    @ApiResponses(value = {@ApiResponse(code = 412, message = "Timeout too short")})
    public Response waitForQueuesToComplete(@QueryParam("timeoutSec") @DefaultValue("5") final Long timeoutSec,
                                            @javax.ws.rs.core.Context final HttpServletRequest request) {
        final boolean areAllNotificationsProcessed = waitForNotificationToComplete(request, timeoutSec);
        return Response.status(areAllNotificationsProcessed ? Status.OK : Status.PRECONDITION_FAILED).build();
    }

    @GET
    @Path("/clock")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get the current time", response = ClockResource.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid timezone supplied")})
    public Response getCurrentTime(@QueryParam("timeZone") final String timeZoneStr) {
        final DateTimeZone timeZone = timeZoneStr != null ? DateTimeZone.forID(timeZoneStr) : DateTimeZone.UTC;
        final DateTime now = clock.getUTCNow();
        final ClockResource result = new ClockResource(now, timeZone.getID(), new LocalDate(now, timeZone));
        return Response.status(Status.OK).entity(result).build();
    }

    @POST
    @Path("/clock")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Set the current time", response = ClockResource.class)
    @ApiResponses(value = {/* @ApiResponse(code = 200, message = "Successful"), */
                           @ApiResponse(code = 400, message = "Invalid time or timezone supplied")})
    public Response setTestClockTime(@QueryParam(QUERY_REQUESTED_DT) final String requestedClockDate,
                                     @QueryParam("timeZone") final String timeZoneStr,
                                     @QueryParam("timeoutSec") @DefaultValue("5") final Long timeoutSec,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) {

        final ClockMock testClock = getClockMock();
        if (requestedClockDate == null) {
            testClock.resetDeltaFromReality();
        } else {
            final DateTime newTime = DATE_TIME_FORMATTER.parseDateTime(requestedClockDate).toDateTime(DateTimeZone.UTC);
            testClock.setTime(newTime);
        }

        waitForNotificationToComplete(request, timeoutSec);

        return getCurrentTime(timeZoneStr);
    }

    @PUT
    @Path("/clock")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Move the current time", response = ClockResource.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Invalid timezone supplied")})
    public Response updateTestClockTime(@QueryParam("days") final Integer addDays,
                                        @QueryParam("weeks") final Integer addWeeks,
                                        @QueryParam("months") final Integer addMonths,
                                        @QueryParam("years") final Integer addYears,
                                        @QueryParam("timeZone") final String timeZoneStr,
                                        @QueryParam("timeoutSec") @DefaultValue("5") final Long timeoutSec,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) {

        final ClockMock testClock = getClockMock();
        if (addDays != null) {
            testClock.addDays(addDays);
        } else if (addWeeks != null) {
            testClock.addWeeks(addWeeks);
        } else if (addMonths != null) {
            testClock.addMonths(addMonths);
        } else if (addYears != null) {
            testClock.addYears(addYears);
        }

        waitForNotificationToComplete(request, timeoutSec);

        return getCurrentTime(timeZoneStr);
    }



    private boolean waitForNotificationToComplete(final ServletRequest request, final Long timeoutSec) {
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Long tenantRecordId = recordIdApi.getRecordId(tenantContext.getTenantId(), ObjectType.TENANT, tenantContext);

        int nbTryLeft = timeoutSec != null ? timeoutSec.intValue() : 0;
        boolean areAllNotificationsProcessed = false;
        try {
            while (!areAllNotificationsProcessed && nbTryLeft > 0) {
                areAllNotificationsProcessed = areAllNotificationsProcessed(tenantRecordId);
                // Processing of notifications may have triggered bus events, which may trigger other notifications
                // effective immediately. Hence, we need to make sure all bus events have been processed too.
                areAllNotificationsProcessed = areAllNotificationsProcessed && areAllBusEventsProcessed(tenantRecordId);
                // We do a re-check of the notification queues in case of race conditions.
                areAllNotificationsProcessed = areAllNotificationsProcessed && areAllNotificationsProcessed(tenantRecordId);
                areAllNotificationsProcessed = areAllNotificationsProcessed && areAllBusEventsProcessed(tenantRecordId);
                if (!areAllNotificationsProcessed) {
                    Thread.sleep(MILLIS_IN_SEC);
                    nbTryLeft--;
                }
            }
        } catch (final InterruptedException ignore) {
        }

        if (!areAllNotificationsProcessed) {
            log.warn("TestResource: there are more notifications or bus events to process, consider increasing the timeout (currently {}s)", timeoutSec);
        }

        return areAllNotificationsProcessed;
    }

    private boolean areAllNotificationsProcessed(final Long tenantRecordId) {
        int nbNotifications = 0;
        for (final NotificationQueue notificationQueue : notificationQueueService.getNotificationQueues()) {
            final Iterator<NotificationEventWithMetadata<NotificationEvent>> iterator = notificationQueue.getFutureOrInProcessingNotificationForSearchKey2(null, tenantRecordId).iterator();
            try {
                while (iterator.hasNext()) {
                    final NotificationEventWithMetadata<NotificationEvent> notificationEvent = iterator.next();
                    if (!notificationEvent.getEffectiveDate().isAfter(clock.getUTCNow())) {
                        nbNotifications += 1;
                    }
                }
            } finally {
                // Go through all results to close the connection
                while (iterator.hasNext()) {
                    iterator.next();
                }
            }
        }
        if (nbNotifications != 0) {
            log.info("TestResource: {} queue(s) with more notification(s) to process", nbNotifications);
        }
        return nbNotifications == 0;
    }

    private boolean areAllBusEventsProcessed(final Long tenantRecordId) {
        final Iterable<BusEventWithMetadata<BusEvent>> availableBusEventForSearchKey2 = persistentBus.getAvailableOrInProcessingBusEventsForSearchKey2(null, tenantRecordId);
        // This will go through all results to close the connection
        final int nbBusEvents = Iterables.size(availableBusEventForSearchKey2);
        if (nbBusEvents != 0) {
            log.info("TestResource: at least {} more bus event(s) to process", nbBusEvents);
        }
        return nbBusEvents == 0;
    }

    private ClockMock getClockMock() {
        if (!(clock instanceof ClockMock)) {
            throw new UnsupportedOperationException("Kill Bill has not been configured to update the time");
        }
        return (ClockMock) clock;
    }
}
