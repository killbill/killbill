/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestAddonCreateAlignment extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testAddonCreateAlignment");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testCreateAlignmentStartOfBundle() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2024-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Base CTD = 2023-08-31
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.addDays(14); //2023-08-15

        //create addon 2023-08-15
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAOStartOfBundle-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Addon CTD = 2023-08-31 since AO is created with START_OF_BUNDLE alignment
        final Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));
    }

    @Test(groups = "slow")
    public void testCreateAlignmentStartOfSubscription() throws Exception {
        final LocalDate today = new LocalDate(2023, 8, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //create base 2024-08-01
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("basic-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Base CTD = 2023-08-31
        Subscription baseSub = subscriptionApi.getSubscriptionForEntitlementId(baseEntId, false, callContext);
        Assert.assertEquals(baseSub.getChargedThroughDate(), new LocalDate(2023, 8, 31));

        clock.addDays(14); //2023-08-15

        //create addon 2023-08-15
        final PlanPhaseSpecifier addonSpec = new PlanPhaseSpecifier("BasicAOStartOfSubscription-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID addonEntId = entitlementApi.addEntitlement(baseSub.getBundleId(), new DefaultEntitlementSpecifier(addonSpec), null, null, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Addon CTD = 2023-09-14 since AO is created with START_OF_SUBSCRIPTION alignment
        final Subscription addonSub = subscriptionApi.getSubscriptionForEntitlementId(addonEntId, false, callContext);
        Assert.assertEquals(addonSub.getChargedThroughDate(), new LocalDate(2023, 9, 14));
    }
}
