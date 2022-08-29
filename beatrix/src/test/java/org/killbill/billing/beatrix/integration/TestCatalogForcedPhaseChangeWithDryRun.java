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

import com.google.common.collect.ImmutableList;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestCatalogForcedPhaseChangeWithDryRun extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogForcedPhaseChange");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testDryRunWithChangePlanInMiddleOfSubscriptionWithPhaseSpecified() throws Exception {
        final LocalDate initialDate = new LocalDate(2022, 6, 28);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertListenerStatus();

        final LocalDate futureDate = new LocalDate(2022, 7, 13);
        final DryRunArguments dryRunSubscriptionActionArg = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION,
                "Gun",
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                null,
                PhaseType.EVERGREEN,
                SubscriptionEventType.CHANGE,
                createdEntitlement.getId(),
                createdEntitlement.getBundleId(),
                futureDate,
                BillingActionPolicy.IMMEDIATE);
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(createdEntitlement.getAccountId(), futureDate, dryRunSubscriptionActionArg, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2022, 7, 13), new LocalDate(2022, 7, 28), InvoiceItemType.RECURRING, new BigDecimal("20.00")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2022, 7, 13), new LocalDate(2022, 7, 28), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2.50")));

        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
    }

    @Test(groups = "slow")
    public void testDryRunWithChangePlanInMiddleOfSubscriptionWithoutPhaseSpecified() throws Exception {
        final LocalDate initialDate = new LocalDate(2022, 6, 28);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertListenerStatus();

        final LocalDate futureDate = new LocalDate(2022, 7, 13);
        final DryRunArguments dryRunSubscriptionActionArg = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION,
                "Gun",
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                null,
                null,
                SubscriptionEventType.CHANGE,
                createdEntitlement.getId(),
                createdEntitlement.getBundleId(),
                futureDate,
                BillingActionPolicy.IMMEDIATE);
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(createdEntitlement.getAccountId(), futureDate, dryRunSubscriptionActionArg, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2022, 7, 13), new LocalDate(2022, 7, 28), InvoiceItemType.RECURRING, new BigDecimal("10.00")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2022, 7, 13), new LocalDate(2022, 7, 28), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2.50")));

        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
    }

    @Test(groups = "slow")
    public void testDryRunWithChangePlanOnStartOfSubscriptionWithPhaseSpecified() throws Exception {
        final LocalDate initialDate = new LocalDate(2022, 6, 28);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertListenerStatus();

        final DryRunArguments dryRunSubscriptionActionArg = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION,
                "Gun",
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                null,
                PhaseType.EVERGREEN,
                SubscriptionEventType.CHANGE,
                createdEntitlement.getId(),
                createdEntitlement.getBundleId(),
                initialDate,
                BillingActionPolicy.IMMEDIATE);
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(createdEntitlement.getAccountId(), initialDate, dryRunSubscriptionActionArg, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2022, 6, 28), new LocalDate(2022, 7, 28), InvoiceItemType.RECURRING, new BigDecimal("40.00")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2022, 6, 28), new LocalDate(2022, 7, 28), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-5.00")));

        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
    }

    @Test(groups = "slow")
    public void testDryRunWithChangePlanOnStartOfSubscriptionWithoutPhaseSpecified() throws Exception {
        final LocalDate initialDate = new LocalDate(2022, 6, 28);
        clock.setDay(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(null));
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement createdEntitlement = entitlementApi.getEntitlementForId(createdEntitlementId, callContext);
        assertListenerStatus();

        final DryRunArguments dryRunSubscriptionActionArg = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION,
                "Gun",
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                null,
                null,
                SubscriptionEventType.CHANGE,
                createdEntitlement.getId(),
                createdEntitlement.getBundleId(),
                initialDate,
                BillingActionPolicy.IMMEDIATE);
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(createdEntitlement.getAccountId(), initialDate, dryRunSubscriptionActionArg, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2022, 6, 28), new LocalDate(2022, 7, 28), InvoiceItemType.RECURRING, new BigDecimal("20.00")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2022, 6, 28), new LocalDate(2022, 7, 28), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-5.00")));

        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);
    }

}
