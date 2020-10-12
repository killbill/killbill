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
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

public class TestCatalogEffectiveDate extends TestIntegrationBase {

    private CallContext testCallContext;
    private Account account;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Setup tenant
        clock.setTime(new DateTime("2020-09-20T12:56:02"));
        testCallContext = setupTenant();

        // Setup account in right tenant
        account = setupAccount(testCallContext);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1373")
    public void testHistoricalCatalogChange() throws Exception {
        uploadCatalog("WeaponsHireSmall-v1.xml");
        assertListenerStatus();

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 9, 20), null, InvoiceItemType.FIXED, new BigDecimal("100.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2020, 9, 20), new LocalDate(2020, 10, 20), InvoiceItemType.RECURRING, new BigDecimal("49.95")));

        uploadCatalog("WeaponsHireSmall-v2.xml");
        assertListenerStatus();

        checkNoMoreInvoiceToGenerate(account.getId(), testCallContext);
    }

    private void uploadCatalog(final String name) throws CatalogApiException, IOException {
        catalogUserApi.uploadCatalog(Resources.asCharSource(Resources.getResource("catalogs/testCatalogEffectiveDate/" + name), Charsets.UTF_8).read(), testCallContext);
    }
}
