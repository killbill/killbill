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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.callcontext.CallContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestInvoiceIssue extends TestIntegrationBase {

    private CallContext testCallContext;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();
        testCallContext = setupTenant();
    }

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testInvoiceIssue/versionedCatalogs");
        allExtraProperties.put("org.killbill.subscription.align.effectiveDateForExistingSubscriptions", "true");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testInvoiceIssue() throws Exception {
        final DateTime initialDateTime = new DateTime("2024-10-25T00:00:00");
        clock.setTime(initialDateTime);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("plan_MDE5MjU0NTgtNmMyZS03YjkzLTg1ZTAtMTNlZjEzYzY1Zjlj");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(createdEntitlementId, false, callContext);
        assertListenerStatus();

        // Invoice corresponding to TRIAL phase
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 1);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2024, 10, 25), new LocalDate(2024, 11, 1), InvoiceItemType.FIXED, BigDecimal.ZERO));
        // Move clock to 2024-11-01 - recurring invoice generated
        clock.addDays(7);
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();

        // Total 2 invoices
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        //Invoice with startDate=2024-11-01 and endDate=2024-12-01
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2024, 11, 1), new LocalDate(2024, 12, 1), InvoiceItemType.RECURRING, new BigDecimal(300)));

        // upload new catalog version so that we need to transition to a new plan version and calculate the
        // (possibly) new BCD
        uploadCatalog("catalog-v3.xml");

        //Move clock to 2024-12-01
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();

        // Total 3 invoices
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);

        //Invoice with startDate=2024-11-01 and endDate=2024-12-01
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2024, 12, 1), new LocalDate(2025, 1, 1), InvoiceItemType.RECURRING, new BigDecimal(300)));

        //Move clock to 2025-01-01
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();

        // Total 4 invoices
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 4);

        //Invoice with startDate=2025-01-01 and endDate=2025-02-01
        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2025, 1, 1), new LocalDate(2025, 2, 1), InvoiceItemType.RECURRING, new BigDecimal(300)));

        //Future invoice run
        final LocalDate futureDate = new LocalDate(2025, 2, 1);
        final DryRunArguments dryRun = new TestDryRunArguments(DryRunType.TARGET_DATE);
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), futureDate, dryRun, Collections.emptyList(), callContext);
        assertNotNull(dryRunInvoice);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, List.of(new ExpectedInvoiceItemCheck(new LocalDate(2025, 2, 1), new LocalDate(2025, 3, 1), InvoiceItemType.RECURRING, new BigDecimal(300))));

    }

    private void uploadCatalog(final String name) throws CatalogApiException, IOException, URISyntaxException {
        final Path path = Paths.get(org.killbill.commons.utils.io.Resources.getResource("catalogs/testInvoiceIssue/" + name).toURI());
        catalogUserApi.uploadCatalog(Files.readString(path), testCallContext);
    }

}
