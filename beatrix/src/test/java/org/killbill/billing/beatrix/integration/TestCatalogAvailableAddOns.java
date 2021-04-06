/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;

public class TestCatalogAvailableAddOns extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogAvailableAddOns");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testPlanWithNewAddOns() throws Exception {
        // Catalog v1 starts in 2021-03-31
        // Catalog v2 starts in 2021-04-30
        // -> Start on catalog V1
        final LocalDate today = new LocalDate(2021, 04, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));

        // Create Base Plan on catalog V1
        final String productName = "Pistol";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier(productName, term, "DEFAULT", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final UUID bpEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec1), "externalKey", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        Entitlement bpEntitlement = entitlementApi.getEntitlementForId(bpEntitlementId, callContext);

        // Verify we cannot create an add-on Laser-Scope as it is not available in V1
        final PlanPhaseSpecifier addOnSpec = new PlanPhaseSpecifier("Laser-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        try {
            entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), clock.getUTCToday(), clock.getUTCToday(), false, ImmutableList.<PluginProperty>of(), callContext);
            Assert.fail("Should fail to create Laser-scope add-on as it is not available");
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.CAT_NO_SUCH_PRODUCT.getCode());
        }

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Change Plan (same Plan pistol-monthly), but using catalog V2 (the price increase should be reflected)
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("pistol-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        bpEntitlement = bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), clock.getUTCToday(), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Verify we can now create the add-on since is available on the new version
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        entitlementApi.addEntitlement(bpEntitlement.getBundleId(), new DefaultEntitlementSpecifier(addOnSpec), clock.getUTCToday(), clock.getUTCToday(), false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

    }

}
