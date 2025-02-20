/*
 * Copyright 2020-2025 Equinix, Inc
 * Copyright 2014-2025 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.DefaultTierPriceOverride;
import org.killbill.billing.catalog.DefaultTieredBlockPriceOverride;
import org.killbill.billing.catalog.DefaultUsagePriceOverride;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultBaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

public class TestInArrearCreateChangeCancel extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testInArrearCreateChangeCancel");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testCreateChangeCancel() throws Exception {

        final LocalDate today = new LocalDate(2025, 2, 12);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(12));

        //CREATE SUBSCRIPTION BUNDLE WITH BASE AND 3 ADDONs
        final EntitlementSpecifier baseEntitlementSpecifier = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("standard-monthly-in-arrear"));
        final EntitlementSpecifier addOnEntitlementSpecifier1 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("ao1-in-arrear"));
        final EntitlementSpecifier addOnEntitlementSpecifier2 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("ao2-in-arrear"));
        final EntitlementSpecifier addOnEntitlementSpecifier3 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("ao3-in-arrear"));
        final List<EntitlementSpecifier> specifierList = List.of(baseEntitlementSpecifier, addOnEntitlementSpecifier1, addOnEntitlementSpecifier2, addOnEntitlementSpecifier3);
        final BaseEntitlementWithAddOnsSpecifier cartSpecifier = new DefaultBaseEntitlementWithAddOnsSpecifier(null, null, specifierList, null, null, false);
        final List<BaseEntitlementWithAddOnsSpecifier> entitlementWithAddOnsSpecifierList = List.of(cartSpecifier);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.CREATE, NextEvent.CREATE, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final List<UUID> allEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(), entitlementWithAddOnsSpecifierList, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(allEntitlements.get(0), false, callContext);
        final Entitlement addOnEntitlement1 = entitlementApi.getEntitlementForId(allEntitlements.get(1), false, callContext);
        final Entitlement addOnEntitlement2 = entitlementApi.getEntitlementForId(allEntitlements.get(2), false, callContext);
        final Entitlement addOnEntitlement3 = entitlementApi.getEntitlementForId(allEntitlements.get(3), false, callContext);

        //add addon4 with usage price overrides
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        TieredBlockPriceOverride tieredBlockPriceOverride = new DefaultTieredBlockPriceOverride("bullets", BigDecimal.valueOf(1), BigDecimal.valueOf(1), Currency.USD, BigDecimal.valueOf(1000));
        TierPriceOverride tierPriceOverride = new DefaultTierPriceOverride(List.of(tieredBlockPriceOverride));
        UsagePriceOverride usagePriceOverride = new DefaultUsagePriceOverride("ao4-usage", UsageType.CONSUMABLE, List.of(tierPriceOverride));
        PlanPhasePriceOverride planPhaseOverride = new DefaultPlanPhasePriceOverride("ao4-in-arrear-evergreen", Currency.USD, null, null, List.of(usagePriceOverride));
        final EntitlementSpecifier addOnEntitlementSpecifier4 = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("ao4-in-arrear"), null, null, null, List.of(planPhaseOverride));
        UUID addon4EntId = entitlementApi.addEntitlement(baseEntitlement.getBundleId(), addOnEntitlementSpecifier4, today, today, false, Collections.emptyList(), callContext);
        final Entitlement addOnEntitlement4 = entitlementApi.getEntitlementForId(addon4EntId, false, callContext);
        assertListenerStatus();

        final LocalDate changeCancelDate = today.plusMonths(1);

        //CHANGE AO1 to AO1 with recurring price override and date  2025-03-12 - INVOICE NOT GENERATED
        planPhaseOverride = new DefaultPlanPhasePriceOverride("ao1-in-arrear-evergreen", Currency.USD, null, new BigDecimal(1.34), null);
        EntitlementSpecifier aoSpec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("ao1-in-arrear", PhaseType.EVERGREEN), null, null, null, List.of(planPhaseOverride));
        addOnEntitlement1.changePlanWithDate(aoSpec, changeCancelDate, Collections.emptyList(), callContext);

        //CHANGE AO3 to AO3 with recurring price override and date  2025-03-12 - INVOICE NOT GENERATED
        tieredBlockPriceOverride = new DefaultTieredBlockPriceOverride("bullets", BigDecimal.valueOf(1), BigDecimal.valueOf(1), Currency.USD, BigDecimal.valueOf(1000));
        tierPriceOverride = new DefaultTierPriceOverride(List.of(tieredBlockPriceOverride));
        usagePriceOverride = new DefaultUsagePriceOverride("bullets-usage-in-arrear-usage", UsageType.CONSUMABLE, List.of(tierPriceOverride));
        planPhaseOverride = new DefaultPlanPhasePriceOverride("ao3-in-arrear-evergreen", Currency.USD, null, new BigDecimal(6.58), List.of(usagePriceOverride));
        aoSpec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("ao3-in-arrear", PhaseType.EVERGREEN), null, null, null, List.of(planPhaseOverride));
        addOnEntitlement3.changePlanWithDate(aoSpec, changeCancelDate, Collections.emptyList(), callContext);

        //CHANGE AO4 to AO4 with recurring price override and date  2025-03-12 - INVOICE NOT GENERATED
        planPhaseOverride = new DefaultPlanPhasePriceOverride("ao4-in-arrear", Currency.USD, null, new BigDecimal(3.35), null);
        aoSpec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("ao4-in-arrear", PhaseType.EVERGREEN), null, null, null, List.of(planPhaseOverride));
        addOnEntitlement4.changePlanWithDate(aoSpec, changeCancelDate, Collections.emptyList(), callContext);


        //CANCEL BASE with date 2025-03-12 - INVOICE NOT GENERATED
        baseEntitlement.cancelEntitlementWithDate(changeCancelDate, true, Collections.emptyList(), callContext);
        checkNoMoreInvoiceToGenerate(account);

//        DateTime changeCancelDateTime = new DateTime(2025,3, 12,0,0);
//        baseEntitlement.cancelEntitlementWithDate(changeCancelDateTime, changeCancelDateTime, Collections.emptyList(), callContext);
//        checkNoMoreInvoiceToGenerate(account);

    }



}
