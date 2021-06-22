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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestCatalogWithDryRun extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogWithDryRun");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testDryRunWithChangePlanOnNewCatalog() throws Exception {
        // Start with an initialDate such that: catalog V1=2020-09-16T10:34:25 < initialDate < catalog V2=2020-10-18T11:19:01
        final LocalDate initialDate = new LocalDate(2020, 10, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertListenerStatus();

        final LocalDate futureDate = new LocalDate(2020, 10, 21);
        final DryRunArguments dryRunSubscriptionActionArg = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION,
                                                                                    "Pistol",
                                                                                    ProductCategory.BASE,
                                                                                    BillingPeriod.MONTHLY,
                                                                                    "discount",
                                                                                    null,
                                                                                    SubscriptionEventType.CHANGE,
                                                                                    createdEntitlement.getId(),
                                                                                    createdEntitlement.getBundleId(),
                                                                                    futureDate,
                                                                                    BillingActionPolicy.IMMEDIATE);
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(createdEntitlement.getAccountId(), futureDate, dryRunSubscriptionActionArg, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 21), null, InvoiceItemType.FIXED, new BigDecimal("80.00")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 21), new LocalDate(2020, 11, 1), InvoiceItemType.RECURRING, new BigDecimal("7.08")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 21), new LocalDate(2020, 11, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-17.72")));

        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
    }

    @Test(groups = "slow")
    public void testDryRunWithChangePlanWithOverridesOnSamePlan() throws Exception {
        final VersionedCatalog catalog = catalogUserApi.getCatalog("WeaponsHireSmall", callContext);

        // Start with an initialDate such that: catalog V1=2020-09-16T10:34:25 < initialDate < catalog V2=2020-10-18T11:19:01
        final LocalDate initialDate = new LocalDate(2020, 10, 1);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement bpEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 1), null, InvoiceItemType.FIXED, new BigDecimal("100")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 1), new LocalDate(2020, 11, 1), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);
        Assert.assertEquals(curInvoice.getInvoiceItems().get(1).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        clock.setDay(new LocalDate(2020, 10, 19));
        assertListenerStatus();

        // Future change
        final LocalDate changeDate = new LocalDate(2020, 10, 20);
        final PlanPhaseSpecifier spec1 = new PlanPhaseSpecifier("Pistol", BillingPeriod.MONTHLY, "discount", null);
        bpEntitlement.changePlanWithDate(new DefaultEntitlementSpecifier(spec1), changeDate, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Generate the invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(bpEntitlement.getAccountId(), changeDate, callContext);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 1), null, InvoiceItemType.FIXED, new BigDecimal("100")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 1), new LocalDate(2020, 11, 1), InvoiceItemType.RECURRING, new BigDecimal("49.95")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);
        Assert.assertEquals(curInvoice.getInvoiceItems().get(1).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()), 0);

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 20), null, InvoiceItemType.FIXED, new BigDecimal("80")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 20), new LocalDate(2020, 11, 1), InvoiceItemType.RECURRING, new BigDecimal("7.72")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2020, 10, 20), new LocalDate(2020, 11, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-19.34")));
        Assert.assertEquals(curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);
        Assert.assertEquals(curInvoice.getInvoiceItems().get(1).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()), 0);

        // Future dry-run change on the same plan with all prices overriden
        final PlanPhasePriceOverride override = new DefaultPlanPhasePriceOverride("pistol-monthly-discount-evergreen",
                                                                                  account.getCurrency(),
                                                                                  new BigDecimal("70.00"),
                                                                                  new BigDecimal("28.90"),
                                                                                  ImmutableList.<UsagePriceOverride>of());
        final List<PlanPhasePriceOverride> overrides = ImmutableList.<PlanPhasePriceOverride>of(override);
        final DryRunArguments dryRunSubscriptionActionArg = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION,
                                                                                    "Pistol",
                                                                                    ProductCategory.BASE,
                                                                                    BillingPeriod.MONTHLY,
                                                                                    "discount",
                                                                                    PhaseType.EVERGREEN,
                                                                                    SubscriptionEventType.CHANGE,
                                                                                    bpEntitlement.getId(),
                                                                                    bpEntitlement.getBundleId(),
                                                                                    null,
                                                                                    BillingActionPolicy.END_OF_TERM,
                                                                                    overrides);
        final LocalDate targetDate = new LocalDate(2020, 11, 1);
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(bpEntitlement.getAccountId(), targetDate, dryRunSubscriptionActionArg, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2020, 11, 1), null, InvoiceItemType.FIXED, new BigDecimal("70.00")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2020, 11, 1), new LocalDate(2020, 12, 1), InvoiceItemType.RECURRING, new BigDecimal("28.90")));


        // Future dry-run change on the same plan with only the recuring price overriden
        final PlanPhasePriceOverride override2 = new DefaultPlanPhasePriceOverride("pistol-monthly-discount-evergreen",
                                                                                  account.getCurrency(),
                                                                                  null,
                                                                                  new BigDecimal("32.65"),
                                                                                  ImmutableList.<UsagePriceOverride>of());
        final List<PlanPhasePriceOverride> override2s = ImmutableList.<PlanPhasePriceOverride>of(override2);
        final DryRunArguments dryRunSubscriptionActionArg2 = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION,
                                                                                    "Pistol",
                                                                                    ProductCategory.BASE,
                                                                                    BillingPeriod.MONTHLY,
                                                                                    "discount",
                                                                                    PhaseType.EVERGREEN,
                                                                                    SubscriptionEventType.CHANGE,
                                                                                    bpEntitlement.getId(),
                                                                                    bpEntitlement.getBundleId(),
                                                                                    null,
                                                                                    BillingActionPolicy.END_OF_TERM,
                                                                                    override2s);
        final Invoice dryRunInvoice2 = invoiceUserApi.triggerDryRunInvoiceGeneration(bpEntitlement.getAccountId(), targetDate, dryRunSubscriptionActionArg2, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices2 = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices2.add(new ExpectedInvoiceItemCheck(new LocalDate(2020, 11, 1), null, InvoiceItemType.FIXED, new BigDecimal("80.00")));
        expectedInvoices2.add(new ExpectedInvoiceItemCheck(new LocalDate(2020, 11, 1), new LocalDate(2020, 12, 1), InvoiceItemType.RECURRING, new BigDecimal("32.65")));

        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice2, expectedInvoices2);
    }
}
