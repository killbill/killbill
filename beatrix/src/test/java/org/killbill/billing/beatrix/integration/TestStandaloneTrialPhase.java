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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
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
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.utils.io.Resources;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestStandaloneTrialPhase extends TestIntegrationBase {

    private CallContext testCallContext;
    private Account account;

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.subscription.align.effectiveDateForExistingSubscriptions", "true");
        return super.getConfigSource(allExtraProperties);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        clock.setTime(new DateTime("2026-02-26T09:14:00"));
        testCallContext = setupTenant();
        account = setupAccount(testCallContext);
    }

    @Test(groups = "slow", description = "STANDALONE product with trial phase + ACCOUNT billing alignment creates subscription and $0 fixed invoice")
    public void testCreateSubscriptionWithStandaloneTrialAndAccountBillingAlignment() throws Exception {
        uploadCatalog("StandaloneTrialCatalog.xml");
        assertListenerStatus();

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standalone-weekly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        entitlementApi.createBaseEntitlement(account.getId(),
                                             new DefaultEntitlementSpecifier(spec, null, null, null, null),
                                             UUID.randomUUID().toString(),
                                             null, null,
                                             false, true, Collections.emptyList(), testCallContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 2, 26), new LocalDate(2026, 3, 28), InvoiceItemType.FIXED, new BigDecimal("0.00")));
    }

    @Test(groups = "slow", description = "Entitlement date before billing date - matches production scenario where entDate ~1 min before billDate")
    public void testCreateSubscriptionWithEntitlementDateBeforeBillingDate() throws Exception {
        uploadCatalog("StandaloneTrialCatalog.xml");
        assertListenerStatus();

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standalone-weekly", null);

        final LocalDate entitlementDate = new LocalDate(2026, 2, 25);
        final LocalDate billingDate = new LocalDate(2026, 2, 26);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        entitlementApi.createBaseEntitlement(account.getId(),
                                             new DefaultEntitlementSpecifier(spec, null, null, null, null),
                                             UUID.randomUUID().toString(),
                                             entitlementDate, billingDate,
                                             false, true, Collections.emptyList(), testCallContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 2, 26), new LocalDate(2026, 3, 28), InvoiceItemType.FIXED, new BigDecimal("0.00")));
    }

    @Test(groups = "slow", description = "Future billing date with STANDALONE trial and ACCOUNT alignment - billing deferred until billing date")
    public void testCreateSubscriptionWithFutureBillingDate() throws Exception {
        uploadCatalog("StandaloneTrialCatalog.xml");
        assertListenerStatus();

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standalone-weekly", null);

        final LocalDate entitlementDate = new LocalDate(2026, 2, 26);
        final LocalDate billingDate = new LocalDate(2026, 2, 27);

        // With future billing date, only BLOCK fires immediately; CREATE is deferred to billing date
        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        entitlementApi.createBaseEntitlement(account.getId(),
                                             new DefaultEntitlementSpecifier(spec, null, null, null, null),
                                             UUID.randomUUID().toString(),
                                             entitlementDate, billingDate,
                                             false, true, Collections.emptyList(), testCallContext);
        assertListenerStatus();

        // Advance to billing date - CREATE fires and invoice is generated
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        clock.addDays(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 2, 27), new LocalDate(2026, 3, 29), InvoiceItemType.FIXED, new BigDecimal("0.00")));
    }

    @Test(groups = "slow", description = "Trial phase transitions to evergreen - verifies BCD is computed and recurring invoice generated")
    public void testTrialToEvergreenTransition() throws Exception {
        uploadCatalog("StandaloneTrialCatalog.xml");
        assertListenerStatus();

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standalone-weekly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        entitlementApi.createBaseEntitlement(account.getId(),
                                             new DefaultEntitlementSpecifier(spec, null, null, null, null),
                                             UUID.randomUUID().toString(),
                                             null, null,
                                             false, true, Collections.emptyList(), testCallContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 2, 26), new LocalDate(2026, 3, 28), InvoiceItemType.FIXED, new BigDecimal("0.00")));

        // Advance to end of trial (30 days) - should trigger PHASE transition and recurring invoice
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        // WEEKLY billing: 2026-03-28 to 2026-04-04
        invoiceChecker.checkInvoice(account.getId(), 2, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 3, 28), new LocalDate(2026, 4, 4), InvoiceItemType.RECURRING, new BigDecimal("4.56")));
    }

    @Test(groups = "slow", description = "Reproduces IllegalStateException in alignToNextBCDIfRequired when ACCOUNT BCD=0 with catalog version upgrade and BCD alignment enabled. See https://github.com/killbill/killbill/issues/2122")
    public void testCatalogVersionUpgradeWithAccountAlignmentAndBCDZero() throws Exception {
        // Upload V1 then V2 - V2 has effectiveDateForExistingSubscriptions set on the plan.
        // Combined with isEffectiveDateForExistingSubscriptionsAlignedToBCD=true (set in getConfigSource),
        // this triggers alignToNextBCDIfRequired in DefaultSubscriptionBase.getSubscriptionBillingEvents.
        uploadCatalog("StandaloneTrialCatalog.xml");
        uploadCatalog("StandaloneTrialCatalogV2.xml");
        assertListenerStatus();

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("standalone-weekly", null);

        // Bug: When processing PHASE transition (EVERGREEN phase with recurring) inside
        // getSubscriptionBillingEvents, alignToNextBCDIfRequired passes ACCOUNT alignment
        // with accountBillCycleDayLocal=0 to BillCycleDayCalculator.calculateBcdForAlignment,
        // which throws IllegalStateException("Account BCD should be set at this point").
        //
        // DefaultInternalBillingApi.calculateBcdForTransition has the ACCOUNT->SUBSCRIPTION
        // fallback when BCD=0, but alignToNextBCDIfRequired does not.
        //
        // Expected: subscription created with $0 FIXED invoice for trial phase.
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        entitlementApi.createBaseEntitlement(account.getId(),
                                             new DefaultEntitlementSpecifier(spec, null, null, null, null),
                                             UUID.randomUUID().toString(),
                                             null, null,
                                             false, true, Collections.emptyList(), testCallContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2026, 2, 26), new LocalDate(2026, 3, 28), InvoiceItemType.FIXED, new BigDecimal("0.00")));
    }

    private void uploadCatalog(final String name) throws CatalogApiException, IOException, URISyntaxException {
        final Path path = Paths.get(Resources.getResource("catalogs/testStandaloneTrialPhase/" + name).toURI());
        catalogUserApi.uploadCatalog(Files.readString(path), testCallContext);
    }
}
