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

package com.ning.billing.jaxrs.resources;

import java.util.UUID;

import javax.inject.Inject;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.clock.Clock;
import com.ning.billing.clock.ClockMock;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
@Path(JaxrsResource.PREFIX + "/test")
public class TestResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(TestResource.class);

    @Inject
    public TestResource(final JaxrsUriBuilder uriBuilder, final TagUserApi tagUserApi, final CustomFieldUserApi customFieldUserApi, final AuditUserApi auditUserApi, final AccountUserApi accountUserApi, final Clock clock, final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, clock, context);
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
    @Path("/clock")
    @Produces(APPLICATION_JSON)
    public Response getCurrentTime(@QueryParam("timeZone") final String timeZoneStr) {
        final DateTimeZone timeZone = timeZoneStr != null ? DateTimeZone.forID(timeZoneStr) : DateTimeZone.UTC;
        final DateTime now = clock.getUTCNow();
        final ClockResource result = new ClockResource(now, timeZone.getID(), new LocalDate(now, timeZone));
        return Response.status(Status.OK).entity(result).build();
    }

    @POST
    @Path("/clock")
    @Produces(APPLICATION_JSON)
    public Response setTestClockTime(@QueryParam(QUERY_REQUESTED_DT) final String requestedClockDate,
                                     @QueryParam("timeZone") final String timeZoneStr) {

        final ClockMock testClock = getClockMock();
        if (requestedClockDate == null) {
            log.info("************      RESETTING CLOCK to " + clock.getUTCNow());
            testClock.resetDeltaFromReality();
        } else {
            final DateTime newTime = DATE_TIME_FORMATTER.parseDateTime(requestedClockDate);
            testClock.setTime(newTime);
        }
        return getCurrentTime(timeZoneStr);
    }


    @PUT
    @Path("/clock")
    @Produces(APPLICATION_JSON)
    public Response updateTestClockTime(@QueryParam("days") final Integer addDays,
                                        @QueryParam("weeks") final Integer addWeeks,
                                        @QueryParam("months") final Integer addMonths,
                                        @QueryParam("years") final Integer addYears,
                                        @QueryParam("timeZone") final String timeZoneStr) {

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
        return getCurrentTime(timeZoneStr);
    }


    private ClockMock getClockMock() {
        if (!(clock instanceof ClockMock)) {
            throw new UnsupportedOperationException("Kill Bill has not been configured to update the time");
        }
        return (ClockMock) clock;
    }
}
