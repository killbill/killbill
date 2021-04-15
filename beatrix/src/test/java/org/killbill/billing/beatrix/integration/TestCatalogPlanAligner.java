/*
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

public class TestCatalogPlanAligner extends TestIntegrationBase {

    private CallContext testCallContext;
    private Account account;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Setup tenant
        clock.setTime(new DateTime("2020-09-17T12:56:02"));
        testCallContext = setupTenant();

        // Setup account in right tenant
        account = setupAccount(testCallContext);
    }

    @Test(groups = "slow")
    public void testUncancel() throws Exception {
        // Catalog effDt = 2020-09-16T10:34:25
        uploadCatalog("WeaponsHireSmall-v1.xml");
        assertListenerStatus();

        // 2020-09-17T12:56:02
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID subId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 9, 17), null, InvoiceItemType.FIXED, new BigDecimal("0.00")));


        // Catalog effDt = 2020-09-18T11:19:01
        uploadCatalog("WeaponsHireSmall-v2.xml");
        assertListenerStatus();

        final Entitlement entitlement = entitlementApi.getEntitlementForId(subId, testCallContext);

        // 2020-09-18T12:56:02 (WeaponsHireSmall-v2 is active)
        clock.addDays(1);

        // pistol-discount-monthly is only available on WeaponsHireSmall-v2
        // pistol-discount-monthly  has a 3 months discount period
        final PlanPhaseSpecifier spec2  = new PlanPhaseSpecifier("pistol-discount-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec2), clock.getUTCToday(), ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 9, 18), null, InvoiceItemType.FIXED, new BigDecimal("0.00")));


        // Catalog effDt = 2020-09-19T11:19:01 (we remove the plan pistol-discount-monthly)
        uploadCatalog("WeaponsHireSmall-v3.xml");
        assertListenerStatus();

        // 2020-09-19T12:56:02
        clock.addDays(1);

        // Cancel way far in the future after the pending RECURRING phase
        entitlement.cancelEntitlementWithDate(new LocalDate("2020-12-20"), true, ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();

        // 2020-09-20T12:56:02
        clock.addDays(1);

        // We should see the pending RECURRING phase in the future
        busHandler.pushExpectedEvents(NextEvent.UNCANCEL);
        entitlement.uncancelEntitlement(ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();

        // 2020-10-20T12:56:02 Move after TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 17), new LocalDate(2020, 11, 17), InvoiceItemType.RECURRING, new BigDecimal("49.95")));


        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 4, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 11, 17), new LocalDate(2020, 12, 17), InvoiceItemType.RECURRING, new BigDecimal("49.95")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 5, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 12, 17), new LocalDate(2021, 1, 17), InvoiceItemType.RECURRING, new BigDecimal("49.95")));

        // 2021-01-20T12:56:02 Move after DISCOUNT
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 6, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2021, 1, 17), new LocalDate(2021, 2, 17), InvoiceItemType.RECURRING, new BigDecimal("89.95")));


        checkNoMoreInvoiceToGenerate(account.getId(), testCallContext);
    }

    private void uploadCatalog(final String name) throws CatalogApiException, IOException {
        catalogUserApi.uploadCatalog(Resources.asCharSource(Resources.getResource("catalogs/testCatalogPlanAligner/" + name), Charsets.UTF_8).read(), testCallContext);
    }
}
