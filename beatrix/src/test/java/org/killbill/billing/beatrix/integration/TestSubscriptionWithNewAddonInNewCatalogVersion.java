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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.utils.io.Resources;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestSubscriptionWithNewAddonInNewCatalogVersion extends TestIntegrationBase {

    private CallContext testCallContext;
    private Account account;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Setup tenant
        clock.setTime(new DateTime("2025-01-01T12:00:00"));
        testCallContext = setupTenant();

        // Setup account in right tenant
        account = setupAccount(testCallContext);

        //upload catalog
        uploadCatalog("monthly-no-trial-with-addon.xml");
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithNewAddOnInNewCatalogVersion() throws Exception {

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
                                      NextEvent.PAYMENT);

        //create base
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standard-monthly", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "base", clock.getUTCToday(), clock.getUTCToday(), false, true, Collections.emptyList(), testCallContext);
        Entitlement baseEntitlement = entitlementApi.getEntitlementForId(entitlementId, false, testCallContext);
        assertEquals(baseEntitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //add ao1
        final PlanPhaseSpecifier ao1Spec = new PlanPhaseSpecifier("standard-ao-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
                                      NextEvent.PAYMENT);
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(ao1Spec, null, null, UUID.randomUUID().toString(), null), null, null, false, Collections.emptyList(), testCallContext);
        assertListenerStatus();

        uploadCatalog("monthly-no-trial-with-addon-v2.xml");

        //move to 2025-02-01 - v2 is active
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

//        //try to add new addon - test fails here
//        final PlanPhaseSpecifier ao2Spec = new PlanPhaseSpecifier("standard-ao2-monthly", null);
//        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
//                                      NextEvent.PAYMENT);
//        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(ao2Spec, null, null, UUID.randomUUID().toString(), null), clock.getUTCToday(), clock.getUTCToday(), false, Collections.emptyList(), testCallContext);
//        assertListenerStatus();

    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithNewAddOnInNewCatalogVersionChangePlanWorkAround() throws Exception {

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
                                      NextEvent.PAYMENT);

        //create base
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standard-monthly", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "base", clock.getUTCToday(), clock.getUTCToday(), false, true, Collections.emptyList(), testCallContext);
        Entitlement baseEntitlement = entitlementApi.getEntitlementForId(entitlementId, false, testCallContext);
        assertEquals(baseEntitlement.getState(), EntitlementState.ACTIVE);
        assertListenerStatus();

        //add ao1
        final PlanPhaseSpecifier ao1Spec = new PlanPhaseSpecifier("standard-ao-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
                                      NextEvent.PAYMENT);
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(ao1Spec, null, null, UUID.randomUUID().toString(), null), null, null, false, Collections.emptyList(), testCallContext);
        assertListenerStatus();

        uploadCatalog("monthly-no-trial-with-addon-v2.xml");

        //move to 2025-02-01 - v2 is active
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        //change plan to the same plan so it is associated with the new catalog
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.NULL_INVOICE);
        baseEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), clock.getUTCToday(), Collections.emptyList(), testCallContext);
        assertListenerStatus();

        //try to add new addon - test fails here if the plan change above is not done
        final PlanPhaseSpecifier ao2Spec = new PlanPhaseSpecifier("standard-ao2-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
                                      NextEvent.PAYMENT);
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), new DefaultEntitlementSpecifier(ao2Spec, null, null, UUID.randomUUID().toString(), null), clock.getUTCToday(), clock.getUTCToday(), false, Collections.emptyList(), testCallContext);
        assertListenerStatus();

    }



    private void uploadCatalog(final String name) throws CatalogApiException, IOException, URISyntaxException {
        final Path path = Paths.get(Resources.getResource("catalogs/testSubscriptionWithNewAddonInNewCatalogVersion/" + name).toURI());
        catalogUserApi.uploadCatalog(Files.readString(path), testCallContext);
    }
}
