/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultBaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionEvent;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/*
 * FOR SEMPLICITY I TEST ONLY SUBSCRIPTION SYSTEM, BUT THE BUG IS PRESENT ALSO IN DRY RUN API
 */
public class TestSubscriptionChangeOfPlan extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testSubscriptionChangeOfPlan");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1881")
    public void testChangeOfPlanInDifferentMonths() throws Exception {
        LocalDate today = new LocalDate(2025, 1, 1);
        clock.setDay(today);

        final Account account = createAccount(getAccountData(null));

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(),
                                                                                            "externalKey",
                                                                                            "HOME20MB",
                                                                                            ProductCategory.BASE,
                                                                                            BillingPeriod.ANNUAL,
                                                                                            NextEvent.CREATE,
                                                                                            NextEvent.BLOCK,
                                                                                            NextEvent.INVOICE,
                                                                                            NextEvent.INVOICE_PAYMENT_ERROR);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(),
                                                                     false,
                                                                     false,
                                                                     true,
                                                                     callContext);
        assertEquals(invoices.size(), 1);

        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2025, 1, 1),
                                                                                          new LocalDate(2026, 1, 1),
                                                                                          InvoiceItemType.RECURRING,
                                                                                          new BigDecimal("100")),
                                                             new ExpectedInvoiceItemCheck(new LocalDate(2025, 1, 1),
                                                                                          null,
                                                                                          InvoiceItemType.FIXED,
                                                                                          new BigDecimal("10")));

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, toBeChecked);

        SubscriptionBase subscriptionBase = subscriptionChecker.checkSubscriptionCreated(bpEntitlement.getId(), internalCallContext);

        assertEquals(subscriptionBase.getCurrentPlan().getName(), "HOME20MB_ANNUAL_PLAN");
        assertEquals(new LocalDate(subscriptionBase.getStartDate()), new LocalDate("2025-01-01"));
        assertEquals(new LocalDate(subscriptionBase.getChargedThroughDate()), new LocalDate("2026-01-01"));
        assertEquals(subscriptionBase.getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);
        assertSame(subscriptionBase.getState(), EntitlementState.ACTIVE);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // We have created an annual subscription that start on 2025-01-01.
        // We move forward the clock to 2025-02-16 and we update the product.
        // We would expect an invoice with this items:
        //    type=[FIXED] startDate=[2025-02-16] endDate=[null] amount=[20.00]
        //    type=[RECURRING] startDate=[2025-02-16] endDate=[2026-01-01] amount=[139.84]
        //    type=[REPAIR_ADJ] startDate=[2025-02-16] endDate=[2026-01-01] amount=[-87.40]

        // But we have:
        //    type=[FIXED] startDate=[2025-02-16] endDate=[null] amount=[20.00]
        //    type=[RECURRING] startDate=[2025-02-16] endDate=[2026-02-01] amount=[153.42]
        //    type=[REPAIR_ADJ] startDate=[2025-02-16] endDate=[2026-01-01] amount=[-87.40]
        //
        // 2025-02-16
        //

        clock.setDay(new LocalDate(2025, 2, 16));

        changeEntitlementAndCheckForCompletion(bpEntitlement,
                                               "HOME100MB",
                                               BillingPeriod.ANNUAL,
                                               BillingActionPolicy.IMMEDIATE,
                                               NextEvent.CHANGE);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2025, 2, 16),
                                             null,
                                             InvoiceItemType.FIXED,
                                             new BigDecimal("20")),
                new ExpectedInvoiceItemCheck(new LocalDate(2025, 2, 16),
                                             new LocalDate(2026, 1, 1),
                                             InvoiceItemType.RECURRING,
                                             new BigDecimal("139.84")),
                new ExpectedInvoiceItemCheck(new LocalDate(2025, 2, 16),
                                             new LocalDate(2026, 1, 1),
                                             InvoiceItemType.REPAIR_ADJ,
                                             new BigDecimal("-87.40")));

        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        subscriptionBase = subscriptionChecker.checkSubscriptionCreated(bpEntitlement.getId(), internalCallContext);

        assertEquals(subscriptionBase.getCurrentPlan().getName(), "HOME100MB_ANNUAL_PLAN");
        assertSame(subscriptionBase.getState(), EntitlementState.ACTIVE);
        assertEquals(new LocalDate(subscriptionBase.getChargedThroughDate()), new LocalDate("2026-01-01"));
        assertEquals(subscriptionBase.getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        checkNoMoreInvoiceToGenerate(account);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1881")
    public void testAddAddonToPlanInDifferentMonths() throws Exception {
        LocalDate today = new LocalDate(2025, 1, 1);
        clock.setDay(today);

        final Account account = createAccount(getAccountData(null));

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(),
                                                                                            "externalKey",
                                                                                            "HOME20MB",
                                                                                            ProductCategory.BASE,
                                                                                            BillingPeriod.ANNUAL,
                                                                                            NextEvent.CREATE,
                                                                                            NextEvent.BLOCK,
                                                                                            NextEvent.INVOICE,
                                                                                            NextEvent.INVOICE_PAYMENT_ERROR);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(),
                                                                     false,
                                                                     false,
                                                                     true,
                                                                     callContext);
        assertEquals(invoices.size(), 1);

        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(new ExpectedInvoiceItemCheck(new LocalDate(2025, 1, 1),
                                                                                          new LocalDate(2026, 1, 1),
                                                                                          InvoiceItemType.RECURRING,
                                                                                          new BigDecimal("100")),
                                                             new ExpectedInvoiceItemCheck(new LocalDate(2025, 1, 1),
                                                                                          null,
                                                                                          InvoiceItemType.FIXED,
                                                                                          new BigDecimal("10")));

        invoiceChecker.checkInvoice(account.getId(), 1, callContext, toBeChecked);

        SubscriptionBase subscriptionBase = subscriptionChecker.checkSubscriptionCreated(bpEntitlement.getId(), internalCallContext);

        assertEquals(subscriptionBase.getCurrentPlan().getName(), "HOME20MB_ANNUAL_PLAN");
        assertEquals(new LocalDate(subscriptionBase.getStartDate()), new LocalDate("2025-01-01"));
        assertEquals(new LocalDate(subscriptionBase.getChargedThroughDate()), new LocalDate("2026-01-01"));
        assertEquals(subscriptionBase.getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);
        assertSame(subscriptionBase.getState(), EntitlementState.ACTIVE);

        assertEquals(bpEntitlement.getSubscriptionBase().getCurrentPlan().getRecurringBillingPeriod(), BillingPeriod.ANNUAL);

        // We have created an annual subscription that start on 2025-01-01.
        // We move forward the clock to 2025-02-16 and we add an AddOn product.
        // We would expect an invoice with this items:
        //    type=[RECURRING] startDate=[2025-02-16] endDate=[2026-01-01] amount=[69.88]

        // But we have:
        //    type=[RECURRING] startDate=[2025-02-16] endDate=[2026-02-01] amount=[76.71]
        //
        // 2025-02-16
        //

        clock.setDay(new LocalDate(2025, 2, 16));

        addAOEntitlementAndCheckForCompletion(bpEntitlement.getBundleId(),
                                              "VOIP",
                                              ProductCategory.ADD_ON,
                                              BillingPeriod.ANNUAL,
                                              NextEvent.CREATE,
                                              NextEvent.BLOCK);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT_ERROR);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2025, 2, 16),
                                             new LocalDate(2026, 1, 1),
                                             InvoiceItemType.RECURRING,
                                             new BigDecimal("69.88")));

        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);


        checkNoMoreInvoiceToGenerate(account);
    }
}
