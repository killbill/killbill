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

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.Block;
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.jaxrs.json.RolledUpUsageJson;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson.UnitUsageRecordJson;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson.UsageRecordJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PluginProperty;
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
import org.killbill.commons.metrics.api.annotation.TimedResource;
import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.commons.utils.collect.Iterables;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.USAGES_PATH)
@Tag(name = "Usage", description = "Operations on usage")
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
                         final InvoicePaymentApi invoicePaymentApi,
                         final EntitlementApi entitlementApi,
                         final Clock clock,
                         final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
        this.usageUserApi = usageUserApi;
        this.entitlementApi = entitlementApi;
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Record usage for a subscription")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully recorded usage data change"),
                           @ApiResponse(responseCode = "400", description = "Invalid subscription or unknown unit type for the plan")})
    public Response recordUsage(final SubscriptionUsageRecordJson json,
                                @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                @HeaderParam(HDR_REASON) final String reason,
                                @HeaderParam(HDR_COMMENT) final String comment,
                                @jakarta.ws.rs.core.Context final HttpServletRequest request,
                                @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws EntitlementApiException,
                                                                                        AccountApiException,
                                                                                        UsageApiException {
        verifyNonNullOrEmpty(json, "SubscriptionUsageRecordJson body should be specified");
        verifyNonNullOrEmpty(json.getSubscriptionId(), "SubscriptionUsageRecordJson subscriptionId needs to be set",
                             json.getUnitUsageRecords(), "SubscriptionUsageRecordJson unitUsageRecords needs to be set");
        Preconditions.checkArgument(!json.getUnitUsageRecords().isEmpty(), "json.getUnitUsageRecords() is empty");

        for (final UnitUsageRecordJson unitUsageRecordJson : json.getUnitUsageRecords()) {
            verifyNonNullOrEmpty(unitUsageRecordJson.getUnitType(), "UnitUsageRecordJson unitType need to be set");
            Preconditions.checkArgument(Iterables.size(unitUsageRecordJson.getUsageRecords()) > 0,
                                        "UnitUsageRecordJson usageRecords must have at least one element.");
            for (final UsageRecordJson usageRecordJson : unitUsageRecordJson.getUsageRecords()) {
                verifyNonNull(usageRecordJson.getAmount(), "UsageRecordJson amount needs to be set");
                verifyNonNull(usageRecordJson.getRecordDate(), "UsageRecordJson recordDate needs to be set");
            }
        }
        final CallContext callContextNoAccount = context.createCallContextNoAccountId(createdBy, reason, comment, request);
        // Verify subscription exists..
        final Entitlement entitlement = entitlementApi.getEntitlementForId(json.getSubscriptionId(), false, callContextNoAccount);
        if (entitlement.getEffectiveEndDate() != null) {
            final DateTime highestRecordDate = getHighestRecordDate(json.getUnitUsageRecords());
            if (entitlement.getEffectiveEndDate().compareTo(highestRecordDate) < 0) {
                return Response.status(Status.BAD_REQUEST).build();
            }
        }

        final SubscriptionUsageRecord record = json.toSubscriptionUsageRecord();
        // Reject usage recorded with a unit type the subscription's plan does not define
        // (see https://github.com/killbill/killbill/issues/2013). The plan comes from the
        // entitlement itself, so a subscription on a superseded (retired) plan version is
        // still validated against that version's units, not the latest catalog. Every
        // unit type is checked before anything is recorded, so a bad record cannot
        // partially land.
        final Set<String> allowedUnitTypes = getAllowedUnitTypes(entitlement.getLastActivePlan());
        for (final UnitUsageRecordJson unitUsageRecordJson : json.getUnitUsageRecords()) {
            if (!allowedUnitTypes.contains(unitUsageRecordJson.getUnitType())) {
                return Response.status(Status.BAD_REQUEST)
                               .entity(String.format("Unknown unit type '%s' for the subscription's plan", unitUsageRecordJson.getUnitType()))
                               .type("text/plain")
                               .build();
            }
        }
        // See this discussion: https://github.com/killbill/killbill/pull/1861#discussion_r1199581600
        final CallContext callContext = context.createCallContextWithAccountId(entitlement.getAccountId(), createdBy, reason, comment, request);
        usageUserApi.recordRolledUpUsage(record, callContext);
        return Response.status(Status.CREATED).build();
    }

    @VisibleForTesting
    DateTime getHighestRecordDate(final List<UnitUsageRecordJson> records) {
        return records.stream()
                      .map(UnitUsageRecordJson::getUsageRecords) // stream of List<UsageRecordJson>
                      .flatMap(Collection::stream) // flattened, so UsageRecordJson instead of List<UsageRecordJson>
                      .map(UsageRecordJson::getRecordDate) // get "recordDate"
                      .max(Comparator.naturalOrder()) // Same as ".sorted(Comparator.reverseOrder()).findFirst()"
                      .orElse(null);
    }

    @VisibleForTesting
    Set<String> getAllowedUnitTypes(final Plan plan) {
        final Set<String> result = new HashSet<>();
        if (plan == null || plan.getAllPhases() == null) {
            return result;
        }
        for (final PlanPhase phase : plan.getAllPhases()) {
            if (phase == null || phase.getUsages() == null) {
                continue;
            }
            for (final Usage usage : phase.getUsages()) {
                if (usage == null) {
                    continue;
                }
                // Consumable usages carry their units on the tiered blocks, capacity
                // usages on the tier limits; top-level blocks and limits cover the
                // remaining shapes, so every plan-defined unit is collected.
                if (usage.getTiers() != null) {
                    for (final Tier tier : usage.getTiers()) {
                        if (tier == null) {
                            continue;
                        }
                        if (tier.getTieredBlocks() != null) {
                            for (final TieredBlock tieredBlock : tier.getTieredBlocks()) {
                                addUnitType(result, tieredBlock);
                            }
                        }
                        if (tier.getLimits() != null) {
                            for (final Limit limit : tier.getLimits()) {
                                addUnitType(result, limit);
                            }
                        }
                    }
                }
                if (usage.getBlocks() != null) {
                    for (final Block block : usage.getBlocks()) {
                        addUnitType(result, block);
                    }
                }
                if (usage.getLimits() != null) {
                    for (final Limit limit : usage.getLimits()) {
                        addUnitType(result, limit);
                    }
                }
            }
        }
        return result;
    }

    private void addUnitType(final Set<String> result, final Block block) {
        if (block != null) {
            addUnitType(result, block.getUnit());
        }
    }

    private void addUnitType(final Set<String> result, final Limit limit) {
        if (limit != null) {
            addUnitType(result, limit.getUnit());
        }
    }

    private void addUnitType(final Set<String> result, final Unit unit) {
        if (unit != null && unit.getName() != null) {
            result.add(unit.getName());
        }
    }

    @TimedResource
    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}/{unitType}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve usage for a subscription and unit type")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RolledUpUsageJson.class))),
                           @ApiResponse(responseCode = "400", description = "Missing start date or end date")})
    public Response getUsage(@PathParam("subscriptionId") final UUID subscriptionId,
                             @PathParam("unitType") final String unitType,
                             @QueryParam(QUERY_START_DATE) final String startDate,
                             @QueryParam(QUERY_END_DATE) final String endDate,
                             @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                             @jakarta.ws.rs.core.Context final HttpServletRequest request) {
        if (startDate == null || endDate == null) {
            return Response.status(Status.BAD_REQUEST).build();
        }
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);

        final DateTime usageStartDate = DATE_TIME_FORMATTER.parseDateTime(startDate);
        final DateTime usageEndDate = DATE_TIME_FORMATTER.parseDateTime(endDate);

        final RolledUpUsage usage = usageUserApi.getUsageForSubscription(subscriptionId, unitType, usageStartDate, usageEndDate, pluginProperties, tenantContext);
        final RolledUpUsageJson result = new RolledUpUsageJson(usage);
        return Response.status(Status.OK).entity(result).build();
    }

    @TimedResource
    @GET
    @Path("/{subscriptionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    @Operation(summary = "Retrieve usage for a subscription")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "successful operation", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RolledUpUsageJson.class))),
                           @ApiResponse(responseCode = "400", description = "Missing start date or end date")})
    public Response getAllUsage(@PathParam("subscriptionId") final UUID subscriptionId,
                                @QueryParam(QUERY_START_DATE) final String startDate,
                                @QueryParam(QUERY_END_DATE) final String endDate,
                                @QueryParam(QUERY_PLUGIN_PROPERTY) final List<String> pluginPropertiesString,
                                @jakarta.ws.rs.core.Context final HttpServletRequest request) {

        if (startDate == null || endDate == null) {
            return Response.status(Status.BAD_REQUEST).build();
        }
        final TenantContext tenantContext = context.createTenantContextNoAccountId(request);
        final Iterable<PluginProperty> pluginProperties = extractPluginProperties(pluginPropertiesString);

        final DateTime usageStartDate = DATE_TIME_FORMATTER.parseDateTime(startDate);
        final DateTime usageEndDate = DATE_TIME_FORMATTER.parseDateTime(endDate);

        // The current JAXRS API only allows to look for one transition
        final List<DateTime> startEndDate = List.of(usageStartDate, usageEndDate);
        final List<RolledUpUsage> usage = usageUserApi.getAllUsageForSubscription(subscriptionId, startEndDate, pluginProperties, tenantContext);
        final RolledUpUsageJson result = new RolledUpUsageJson(usage.get(0));
        return Response.status(Status.OK).entity(result).build();
    }

}

