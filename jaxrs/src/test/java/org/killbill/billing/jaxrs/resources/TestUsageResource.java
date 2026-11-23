/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.ws.rs.core.Response;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
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
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson.UnitUsageRecordJson;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson.UsageRecordJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestUsageResource extends JaxrsTestSuiteNoDB {

    private UsageResource createUsageResource() {
        final UsageResource result = new UsageResource(
                null, // uriBuilder
                null, // tagUserApi
                null, // customFieldUserApi
                null, // auditUserApi
                null, // accountUserApi
                null, // usageUserApi
                null, // paymentApi
                null, // invoicePaymentApi
                null, // entitlementApi
                null, // clock
                null // context
        );
        return Mockito.spy(result);
    }

    private UsageResource createUsageResource(final EntitlementApi entitlementApi, final UsageUserApi usageUserApi, final Context context) {
        final UsageResource result = new UsageResource(
                null, // uriBuilder
                null, // tagUserApi
                null, // customFieldUserApi
                null, // auditUserApi
                null, // accountUserApi
                usageUserApi,
                null, // paymentApi
                null, // invoicePaymentApi
                entitlementApi,
                null, // clock
                context
        );
        return Mockito.spy(result);
    }

    private Plan buildPlanWithTieredBlockUnits(final String... unitTypes) {
        final Plan plan = Mockito.mock(Plan.class);
        final PlanPhase phase = Mockito.mock(PlanPhase.class);
        final Usage usage = Mockito.mock(Usage.class);
        final Tier tier = Mockito.mock(Tier.class);
        final TieredBlock[] blocks = new TieredBlock[unitTypes.length];
        for (int i = 0; i < unitTypes.length; i++) {
            final TieredBlock block = Mockito.mock(TieredBlock.class);
            final Unit unit = Mockito.mock(Unit.class);
            Mockito.when(unit.getName()).thenReturn(unitTypes[i]);
            Mockito.when(block.getUnit()).thenReturn(unit);
            blocks[i] = block;
        }
        Mockito.when(tier.getTieredBlocks()).thenReturn(blocks);
        Mockito.when(tier.getLimits()).thenReturn(new Limit[0]);
        Mockito.when(usage.getTiers()).thenReturn(new Tier[]{tier});
        Mockito.when(usage.getBlocks()).thenReturn(new Block[0]);
        Mockito.when(usage.getLimits()).thenReturn(new Limit[0]);
        Mockito.when(phase.getUsages()).thenReturn(new Usage[]{usage});
        Mockito.when(plan.getAllPhases()).thenReturn(new PlanPhase[]{phase});
        return plan;
    }

    @Test(groups = "fast")
    public void testGetHighestRecordDate() {
        final UsageResource usageResource = createUsageResource();

        final List<UsageRecordJson> fooRecords = List.of(
                new UsageRecordJson(new LocalDate(2018, 03, 04).toDateTimeAtStartOfDay(), BigDecimal.valueOf(28L)),
                new UsageRecordJson(new LocalDate(2018, 03, 05).toDateTimeAtStartOfDay(), BigDecimal.valueOf(2L)),
                new UsageRecordJson(new LocalDate(2018, 03, 01).toDateTimeAtStartOfDay(), BigDecimal.valueOf(1L)),
                new UsageRecordJson(new LocalDate(2018, 04, 06).toDateTimeAtStartOfDay(), BigDecimal.valueOf(24L)));

        final UnitUsageRecordJson unitRecordFoo = new UnitUsageRecordJson("foo", fooRecords);

        final List<UsageRecordJson> barRecords = List.of(
                new UsageRecordJson(new LocalDate(2018, 02, 04).toDateTimeAtStartOfDay(), BigDecimal.valueOf(28L)),
                new UsageRecordJson(new LocalDate(2018, 03, 06).toDateTimeAtStartOfDay(), BigDecimal.valueOf(2L)),
                new UsageRecordJson(new LocalDate(2018, 04, 18).toDateTimeAtStartOfDay(), BigDecimal.valueOf(1L)), // Highest date point
                new UsageRecordJson(new LocalDate(2018, 04, 13).toDateTimeAtStartOfDay(), BigDecimal.valueOf(24L)));

        final UnitUsageRecordJson unitRecordBar = new UnitUsageRecordJson("bar", barRecords);

        final List<UsageRecordJson> zooRecords = List.of(
                new UsageRecordJson(new LocalDate(2018, 02, 04).toDateTimeAtStartOfDay(), BigDecimal.valueOf(28L)),
                new UsageRecordJson(new LocalDate(2018, 03, 06).toDateTimeAtStartOfDay(), BigDecimal.valueOf(2L)),
                new UsageRecordJson(new LocalDate(2018, 04, 17).toDateTimeAtStartOfDay(), BigDecimal.valueOf(1L)),
                new UsageRecordJson(new LocalDate(2018, 04, 12).toDateTimeAtStartOfDay(), BigDecimal.valueOf(24L)));

        final UnitUsageRecordJson unitRecordZoo = new UnitUsageRecordJson("zoo", zooRecords);

        final List<UnitUsageRecordJson> input = List.of(unitRecordFoo, unitRecordBar, unitRecordZoo);
        final DateTime result = usageResource.getHighestRecordDate(input);

        Assert.assertTrue(result.compareTo(new LocalDate(2018, 04, 18).toDateTimeAtStartOfDay()) == 0);
    }

    @Test(groups = "fast")
    public void testGetAllowedUnitTypes() {
        final UsageResource usageResource = createUsageResource();

        final Plan plan = buildPlanWithTieredBlockUnits("server-hourly-type-1", "server-hourly-type-2");
        final Set<String> result = usageResource.getAllowedUnitTypes(plan);

        Assert.assertEquals(result.size(), 2);
        Assert.assertTrue(result.contains("server-hourly-type-1"));
        Assert.assertTrue(result.contains("server-hourly-type-2"));
        Assert.assertFalse(result.contains("dummy"));
    }

    @Test(groups = "fast")
    public void testGetAllowedUnitTypesCapacityLimits() {
        final UsageResource usageResource = createUsageResource();

        final Plan plan = Mockito.mock(Plan.class);
        final PlanPhase phase = Mockito.mock(PlanPhase.class);
        final Usage usage = Mockito.mock(Usage.class);
        final Tier tier = Mockito.mock(Tier.class);
        final Limit limit = Mockito.mock(Limit.class);
        final Unit unit = Mockito.mock(Unit.class);
        Mockito.when(unit.getName()).thenReturn("bandwidth-type-1");
        Mockito.when(limit.getUnit()).thenReturn(unit);
        Mockito.when(tier.getTieredBlocks()).thenReturn(new TieredBlock[0]);
        Mockito.when(tier.getLimits()).thenReturn(new Limit[]{limit});
        Mockito.when(usage.getTiers()).thenReturn(new Tier[]{tier});
        Mockito.when(usage.getBlocks()).thenReturn(new Block[0]);
        Mockito.when(usage.getLimits()).thenReturn(new Limit[0]);
        Mockito.when(phase.getUsages()).thenReturn(new Usage[]{usage});
        Mockito.when(plan.getAllPhases()).thenReturn(new PlanPhase[]{phase});

        final Set<String> result = usageResource.getAllowedUnitTypes(plan);

        Assert.assertEquals(result, Set.of("bandwidth-type-1"));
    }

    @Test(groups = "fast")
    public void testGetAllowedUnitTypesNullPlan() {
        final UsageResource usageResource = createUsageResource();

        Assert.assertTrue(usageResource.getAllowedUnitTypes(null).isEmpty());
    }

    @Test(groups = "fast")
    public void testRecordUsageRejectsUnknownUnitType() throws Exception {
        final UUID subscriptionId = UUID.randomUUID();
        final Plan plan = buildPlanWithTieredBlockUnits("server-hourly-type-1");

        final Entitlement entitlement = Mockito.mock(Entitlement.class);
        Mockito.when(entitlement.getEffectiveEndDate()).thenReturn(null);
        Mockito.when(entitlement.getLastActivePlan()).thenReturn(plan);
        Mockito.when(entitlement.getAccountId()).thenReturn(UUID.randomUUID());

        final EntitlementApi entitlementApi = Mockito.mock(EntitlementApi.class);
        final CallContext callContext = Mockito.mock(CallContext.class);
        Mockito.when(entitlementApi.getEntitlementForId(Mockito.eq(subscriptionId), Mockito.eq(false), Mockito.any())).thenReturn(entitlement);

        final Context context = Mockito.mock(Context.class);
        Mockito.when(context.createCallContextNoAccountId(Mockito.anyString(), Mockito.anyString(),
                                                            Mockito.anyString(), Mockito.any())).thenReturn(callContext);

        final UsageUserApi usageUserApi = Mockito.mock(UsageUserApi.class);
        final UsageResource usageResource = createUsageResource(entitlementApi, usageUserApi, context);

        final List<UsageRecordJson> records = List.of(
                new UsageRecordJson(new LocalDate(2024, 05, 03).toDateTimeAtStartOfDay(), BigDecimal.ONE));
        // server-hourly-type-2 belongs to another plan, dummy is not in the catalog at all
        final SubscriptionUsageRecordJson json = new SubscriptionUsageRecordJson(subscriptionId, "t1",
                                                                                List.of(new UnitUsageRecordJson("server-hourly-type-2", records),
                                                                                        new UnitUsageRecordJson("dummy", records)));

        final Response response = usageResource.recordUsage(json, "demo", "demo", "demo", null, null);

        Assert.assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        Mockito.verify(usageUserApi, Mockito.never()).recordRolledUpUsage(Mockito.any(), Mockito.any());
    }

    @Test(groups = "fast")
    public void testRecordUsageAcceptsKnownUnitType() throws Exception {
        final UUID subscriptionId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final Plan plan = buildPlanWithTieredBlockUnits("server-hourly-type-1");

        final Entitlement entitlement = Mockito.mock(Entitlement.class);
        Mockito.when(entitlement.getEffectiveEndDate()).thenReturn(null);
        Mockito.when(entitlement.getLastActivePlan()).thenReturn(plan);
        Mockito.when(entitlement.getAccountId()).thenReturn(accountId);

        final EntitlementApi entitlementApi = Mockito.mock(EntitlementApi.class);
        final CallContext callContext = Mockito.mock(CallContext.class);
        Mockito.when(entitlementApi.getEntitlementForId(Mockito.eq(subscriptionId), Mockito.eq(false), Mockito.any())).thenReturn(entitlement);

        final Context context = Mockito.mock(Context.class);
        Mockito.when(context.createCallContextNoAccountId(Mockito.anyString(), Mockito.anyString(),
                                                            Mockito.anyString(), Mockito.any())).thenReturn(callContext);
        Mockito.when(context.createCallContextWithAccountId(Mockito.eq(accountId), Mockito.anyString(), Mockito.anyString(),
                                                            Mockito.anyString(), Mockito.any())).thenReturn(callContext);

        final UsageUserApi usageUserApi = Mockito.mock(UsageUserApi.class);
        final UsageResource usageResource = createUsageResource(entitlementApi, usageUserApi, context);

        final List<UsageRecordJson> records = List.of(
                new UsageRecordJson(new LocalDate(2024, 05, 03).toDateTimeAtStartOfDay(), BigDecimal.ONE));
        final SubscriptionUsageRecordJson json = new SubscriptionUsageRecordJson(subscriptionId, "t1",
                                                                                List.of(new UnitUsageRecordJson("server-hourly-type-1", records)));

        final Response response = usageResource.recordUsage(json, "demo", "demo", "demo", null, null);

        Assert.assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        Mockito.verify(usageUserApi, Mockito.times(1)).recordRolledUpUsage(Mockito.any(), Mockito.any());
    }
}
