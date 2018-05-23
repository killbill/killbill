/*
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

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
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

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.jaxrs.json.RolledUpUsageJson;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson.UnitUsageRecordJson;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson.UsageRecordJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.SubscriptionUsageRecord;
import org.killbill.billing.usage.api.UsageApiException;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.USAGES_PATH)
@Api(value = JaxrsResource.USAGES_PATH, description = "Operations on usage", tags="Usage")
public class UsageResource extends JaxRsResourceBase {

    private final UsageUserApi usageUserApi;
    private final EntitlementApi entitlementApi;

    @Inject
    public UsageResource(final JaxrsUriBuilder uriBuilder,
                         final TagUserApi tagUserApi,
                         final CustomFieldUserApi customFieldUserApi,
                         final AuditUserApi auditUserApi,
                         final AccountUserApi accountUserApi,
                         final UsageUserApi usageUserApi,
                         final PaymentApi paymentApi,
                         final EntitlementApi entitlementApi,
                         final Clock clock,
                         final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, null, clock, context);
        this.usageUserApi = usageUserApi;
        this.entitlementApi = entitlementApi;
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Record usage for a subscription")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "Successfully recorded usage data change"),
                           @ApiResponse(code = 400, message = "Invalid subscription (e.g. inactive)")})
    public Response recordUsage(final SubscriptionUsageRecordJson json,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @javax.ws.rs.core.Context final HttpServletRequest request,
                                @javax.ws.rs.core.Context final UriInfo uriInfo) throws EntitlementApiException,
                                                                                        AccountApiException,
                                                                                        UsageApiException {
        verifyNonNullOrEmpty(json, "SubscriptionUsageRecordJson body should be specified");
        verifyNonNullOrEmpty(json.getSubscriptionId(), "SubscriptionUsageRecordJson subscriptionId needs to be set",
                             json.getUnitUsageRecords(), "SubscriptionUsageRecordJson unitUsageRecords needs to be set");
        Preconditions.checkArgument(!json.getUnitUsageRecords().isEmpty());
        for (final UnitUsageRecordJson unitUsageRecordJson : json.getUnitUsageRecords()) {
            verifyNonNullOrEmpty(unitUsageRecordJson.getUnitType(), "UnitUsageRecordJson unitType need to be set");
            Preconditions.checkArgument(Iterables.size(unitUsageRecordJson.getUsageRecords()) > 0,
                                        "UnitUsageRecordJson usageRecords must have at least one element.");
            for (final UsageRecordJson usageRecordJson : unitUsageRecordJson.getUsageRecords()) {
                verifyNonNull(usageRecordJson.getAmount(), "UsageRecordJson amount needs to be set");
                verifyNonNull(usageRecordJson.getRecordDate(), "UsageRecordJson recordDate needs to be set");
            }
        }
        final CallContext callContext = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        // Verify subscription exists..
        final Entitlement entitlement = entitlementApi.getEntitlementForId(json.getSubscriptionId(), callContext);
        if (entitlement.getEffectiveEndDate() != null) {
            final LocalDate highestRecordDate = getHighestRecordDate(json.getUnitUsageRecords());
            if (entitlement.getEffectiveEndDate().compareTo(highestRecordDate) < 0) {
                return Response.status(Status.BAD_REQUEST).build();
            }
        }

        final SubscriptionUsageRecord record = json.toSubscriptionUsageRecord();
        usageUserApi.recordRolledUpUsage(record, callContext);
        return Response.status(Status.CREATED).build();
    }

    @VisibleForTesting
    LocalDate getHighestRecordDate(final List<UnitUsageRecordJson> records) {
        final Iterable<Iterable<LocalDate>> recordedDates = Iterables.transform(records, new Function<UnitUsageRecordJson, Iterable<LocalDate>>() {

            @Override
            public Iterable<LocalDate> apply(final UnitUsageRecordJson input) {
                final Iterable<LocalDate> result = Iterables.transform(input.getUsageRecords(), new Function<UsageRecordJson, LocalDate>() {
                    @Override
                    public LocalDate apply(final UsageRecordJson input) {
                        return input.getRecordDate();
                    }
                });
                return result;
            }
        });
        final Iterable<LocalDate> sortedRecordedDates = Ordering.<LocalDate>natural()
                .reverse()
                .sortedCopy(Iterables.concat(recordedDates));

        return Iterables.getFirst(sortedRecordedDates, null);
    }

    @TimedResource
    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/{unitType}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve usage for a subscription and unit type", response = RolledUpUsageJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Missing start date or end date")})
    public Response getUsage(@PathParam("subscriptionId") final UUID subscriptionId,
                             @PathParam("unitType") final String unitType,
                             @QueryParam(QUERY_START_DATE) final String startDate,
                             @QueryParam(QUERY_END_DATE) final String endDate,
                             @javax.ws.rs.core.Context final HttpServletRequest request) {
        if (startDate == null || endDate == null) {
            return Response.status(Status.BAD_REQUEST).build();
        }
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);

        final LocalDate usageStartDate = LOCAL_DATE_FORMATTER.parseLocalDate(startDate);
        final LocalDate usageEndDate = LOCAL_DATE_FORMATTER.parseLocalDate(endDate);

        final RolledUpUsage usage = usageUserApi.getUsageForSubscription(subscriptionId, unitType, usageStartDate, usageEndDate, tenantContext);
        final RolledUpUsageJson result = new RolledUpUsageJson(usage);
        return Response.status(Status.OK).entity(result).build();
    }

    @TimedResource
    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve usage for a subscription", response = RolledUpUsageJson.class)
    @ApiResponses(value = {@ApiResponse(code = 400, message = "Missing start date or end date")})
    public Response getAllUsage(@PathParam("subscriptionId") final UUID subscriptionId,
                                @QueryParam(QUERY_START_DATE) final String startDate,
                                @QueryParam(QUERY_END_DATE) final String endDate,
                                @javax.ws.rs.core.Context final HttpServletRequest request) {

        if (startDate == null || endDate == null) {
            return Response.status(Status.BAD_REQUEST).build();
        }
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);

        final LocalDate usageStartDate = LOCAL_DATE_FORMATTER.parseLocalDate(startDate);
        final LocalDate usageEndDate = LOCAL_DATE_FORMATTER.parseLocalDate(endDate);

        // The current JAXRS API only allows to look for one transition
        final List<LocalDate> startEndDate = ImmutableList.<LocalDate>builder().add(usageStartDate).add(usageEndDate).build();
        final List<RolledUpUsage> usage = usageUserApi.getAllUsageForSubscription(subscriptionId, startEndDate, tenantContext);
        final RolledUpUsageJson result = new RolledUpUsageJson(usage.get(0));
        return Response.status(Status.OK).entity(result).build();
    }

}

