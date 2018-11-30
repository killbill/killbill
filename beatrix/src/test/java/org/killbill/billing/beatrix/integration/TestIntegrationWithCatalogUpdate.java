/*
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
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.DefaultPlanPhasePriceOverride;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.SimplePlanDescriptor;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.catalog.api.user.DefaultSimplePlanDescriptor;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.tenant.api.DefaultTenant;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestIntegrationWithCatalogUpdate extends TestIntegrationBase {

    @Inject
    private CatalogUserApi catalogUserApi;

    private Tenant tenant;
    private CallContext testCallContext;
    private Account account;

    private DateTime init;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Set original time
        clock.setDay(new LocalDate(2016, 6, 1));
        init = clock.getUTCNow();

        // Setup tenant
        setupTenant();

        // Setup account in right tenant
        setupAccount();
    }

    @Test(groups = "slow")
    public void testBasic() throws Exception {

        // Create a per-tenant catalog with one plan
        final SimplePlanDescriptor desc1 = new DefaultSimplePlanDescriptor("foo-monthly", "Foo", ProductCategory.BASE, account.getCurrency(), BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(desc1, init, testCallContext);
        StaticCatalog catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().size(), 1);

        final Entitlement baseEntitlement = createEntitlement("foo-monthly", true);

        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 7, 1), InvoiceItemType.RECURRING, BigDecimal.TEN));

        // Add another Plan in the catalog
        final SimplePlanDescriptor desc2 = new DefaultSimplePlanDescriptor("superfoo-monthly", "SuperFoo", ProductCategory.BASE, account.getCurrency(), new BigDecimal("20.00"), BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(desc2, init, testCallContext);
        catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().size(), 2);

        // Change Plan to the newly added Plan and verify correct default rules behavior (IMMEDIATE change)
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("SuperFoo", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME);
        baseEntitlement.changePlan(new DefaultEntitlementSpecifier(spec), ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, testCallContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("20.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 7, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-10.00")));
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testWithMultiplePlansForOneProduct() throws CatalogApiException, EntitlementApiException {

        // Create a per-tenant catalog with one plan
        final SimplePlanDescriptor desc1 = new DefaultSimplePlanDescriptor("xxx-monthly", "XXX", ProductCategory.BASE, account.getCurrency(), BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(desc1, init, testCallContext);
        StaticCatalog catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentProducts().size(), 1);
        assertEquals(catalog.getCurrentPlans().size(), 1);

        final Entitlement baseEntitlement1 = createEntitlement("xxx-monthly", true);

        // Add a second plan for same product but with a 14 days trial
        final SimplePlanDescriptor desc2 = new DefaultSimplePlanDescriptor("xxx-14-monthly", "XXX", ProductCategory.BASE, account.getCurrency(), BigDecimal.TEN, BillingPeriod.MONTHLY, 14, TimeUnit.DAYS, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(desc2, init, testCallContext);
        catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentProducts().size(), 1);
        assertEquals(catalog.getCurrentPlans().size(), 2);

        final Entitlement baseEntitlement2 = createEntitlement("xxx-14-monthly", false);

        // Add a second plan for same product but with a 30 days trial
        final SimplePlanDescriptor desc3 = new DefaultSimplePlanDescriptor("xxx-30-monthly", "XXX", ProductCategory.BASE, account.getCurrency(), BigDecimal.TEN, BillingPeriod.MONTHLY, 30, TimeUnit.DAYS, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(desc3, init, testCallContext);
        catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentProducts().size(), 1);
        assertEquals(catalog.getCurrentPlans().size(), 3);

        final Entitlement baseEntitlement3 = createEntitlement("xxx-30-monthly", false);

        // Move clock 14 days
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(14);
        assertListenerStatus();

        // Move clock 16 days
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.NULL_INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(16);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testError_CAT_MULTIPLE_MATCHING_PLANS_FOR_PRICELIST() throws Exception {

        // Create a per-tenant catalog with one plan
        final SimplePlanDescriptor desc1 = new DefaultSimplePlanDescriptor("zoe-monthly", "Zoe", ProductCategory.BASE, account.getCurrency(), BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(desc1, init, testCallContext);
        StaticCatalog catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().size(), 1);

        final SimplePlanDescriptor desc2 = new DefaultSimplePlanDescriptor("zoe-14-monthly", "Zoe", ProductCategory.BASE, account.getCurrency(), BigDecimal.TEN, BillingPeriod.MONTHLY, 14, TimeUnit.DAYS, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(desc2, init, testCallContext);
        catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().size(), 2);

        try {
            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Zoe", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
            entitlementApi.createBaseEntitlement(account.getId(),  new DefaultEntitlementSpecifier(spec), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), testCallContext);
            fail("Creating entitlement should fail");
        } catch (final EntitlementApiException e) {
            assertEquals(e.getCode(), ErrorCode.CAT_MULTIPLE_MATCHING_PLANS_FOR_PRICELIST.getCode());
        }
    }

    @Test(groups = "slow")
    public void testWithPriceOverride() throws Exception {

        // Create a per-tenant catalog with one plan
        final SimplePlanDescriptor desc1 = new DefaultSimplePlanDescriptor("bar-monthly", "Bar", ProductCategory.BASE, account.getCurrency(), BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(desc1, init, testCallContext);
        StaticCatalog catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().size(), 1);

        final Plan plan = catalog.getCurrentPlans().iterator().next();
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("bar-monthly", null);

        final List<PlanPhasePriceOverride> overrides = new ArrayList<PlanPhasePriceOverride>();
        overrides.add(new DefaultPlanPhasePriceOverride(plan.getFinalPhase().getName(), account.getCurrency(), null, BigDecimal.ONE, null));
        final Entitlement baseEntitlement = createEntitlement(spec, overrides, true);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, testCallContext);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getChargedAmount().compareTo(BigDecimal.ONE), 0);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, testCallContext);
        assertEquals(invoices.size(), 2);
        assertEquals(invoices.get(1).getChargedAmount().compareTo(BigDecimal.ONE), 0);

        // Change plan to original (non overridden plan)
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        baseEntitlement.changePlan( new DefaultEntitlementSpecifier(spec), ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, testCallContext);
        assertEquals(invoices.size(), 3);
        assertEquals(invoices.get(2).getChargedAmount().compareTo(new BigDecimal("9.00")), 0); // 10 (recurring) - 1 (repair)
    }

    // Use custom plan definition to create a THIRTY_DAYS plan with no trial and test issue #598
    @Test(groups = "slow")
    public void testWithThirtyDaysPlan() throws Exception {

        // Create a per-tenant catalog with one plan
        final SimplePlanDescriptor desc1 = new DefaultSimplePlanDescriptor("thirty-monthly", "Thirty", ProductCategory.BASE, account.getCurrency(), BigDecimal.TEN, BillingPeriod.THIRTY_DAYS, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(desc1, init, testCallContext);
        StaticCatalog catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().size(), 1);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("thirty-monthly", null);

        createEntitlement(spec, null, true);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, testCallContext);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getChargedAmount().compareTo(BigDecimal.TEN), 0);
        assertEquals(invoices.get(0).getInvoiceItems().size(), 1);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 1), new LocalDate(2016, 7, 1), InvoiceItemType.RECURRING, BigDecimal.TEN));
        invoiceChecker.checkInvoiceNoAudits(invoices.get(0), expectedInvoices);

        int invoiceSize = 2;
        LocalDate startDate = new LocalDate(2016, 7, 1);
        for (int i = 0; i < 14; i++) {

            expectedInvoices.clear();

            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
            clock.addDays(30);
            assertListenerStatus();

            LocalDate endDate = startDate.plusDays(30);
            invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, testCallContext);
            assertEquals(invoices.size(), invoiceSize);

            expectedInvoices.add(new ExpectedInvoiceItemCheck(startDate, endDate, InvoiceItemType.RECURRING, BigDecimal.TEN));
            invoiceChecker.checkInvoiceNoAudits(invoices.get(invoices.size() - 1), expectedInvoices);

            startDate = endDate;
            invoiceSize++;
        }
    }

    @Test(groups = "slow")
    public void testWith$0RecurringPlan() throws Exception {

        // Create a per-tenant catalog with one plan
        final SimplePlanDescriptor zeroDesc = new DefaultSimplePlanDescriptor("zeroDesc-monthly", "Zero", ProductCategory.BASE, account.getCurrency(), BigDecimal.ZERO, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(zeroDesc, init, testCallContext);
        StaticCatalog catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().size(), 1);

        final PlanPhaseSpecifier specZero = new PlanPhaseSpecifier("zeroDesc-monthly", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(),  new DefaultEntitlementSpecifier(specZero), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, testCallContext);

        Subscription refreshedBaseEntitlement = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement.getChargedThroughDate(), new LocalDate(2016, 7, 1));

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        refreshedBaseEntitlement = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement.getChargedThroughDate(), new LocalDate(2016, 8, 1));

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addMonths(1);
        assertListenerStatus();

        refreshedBaseEntitlement = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement.getChargedThroughDate(), new LocalDate(2016, 9, 1));

        // Add another Plan in the catalog
        final SimplePlanDescriptor descNonZero = new DefaultSimplePlanDescriptor("superfoo-monthly", "SuperFoo", ProductCategory.BASE, account.getCurrency(), new BigDecimal("20.00"), BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(descNonZero, init, testCallContext);
        catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().size(), 2);

        final PlanPhaseSpecifier specNonZero = new PlanPhaseSpecifier("superfoo-monthly", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID baseEntitlement2Id = entitlementApi.createBaseEntitlement(account.getId(),  new DefaultEntitlementSpecifier(specNonZero), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();
        final Entitlement baseEntitlement2 = entitlementApi.getEntitlementForId(baseEntitlement2Id, testCallContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        refreshedBaseEntitlement = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement.getChargedThroughDate(), new LocalDate(2016, 10, 1));

        Subscription refreshedBaseEntitlement2 = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement2.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement2.getChargedThroughDate(), new LocalDate(2016, 10, 1));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        refreshedBaseEntitlement = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement.getChargedThroughDate(), new LocalDate(2016, 11, 1));

        refreshedBaseEntitlement2 = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement2.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement2.getChargedThroughDate(), new LocalDate(2016, 11, 1));

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        baseEntitlement.cancelEntitlementWithDateOverrideBillingPolicy(clock.getUTCToday(), BillingActionPolicy.END_OF_TERM, ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        refreshedBaseEntitlement = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement.getChargedThroughDate(), new LocalDate(2016, 11, 1));

        refreshedBaseEntitlement2 = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement2.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement2.getChargedThroughDate(), new LocalDate(2016, 12, 1));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        refreshedBaseEntitlement = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement.getChargedThroughDate(), new LocalDate(2016, 11, 1));

        refreshedBaseEntitlement2 = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement2.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement2.getChargedThroughDate(), new LocalDate(2017, 1, 1));
    }


    @Test(groups = "slow")
    public void testWithWeeklyTrials() throws Exception {

        // Create a per-tenant catalog with one plan
        final SimplePlanDescriptor simplePlanDescriptor = new DefaultSimplePlanDescriptor("hello-monthly", "Hello", ProductCategory.BASE, account.getCurrency(), BigDecimal.ONE, BillingPeriod.MONTHLY, 1, TimeUnit.WEEKS, ImmutableList.<String>of());
        catalogUserApi.addSimplePlan(simplePlanDescriptor, init, testCallContext);
        StaticCatalog catalog = catalogUserApi.getCurrentCatalog("dummy", testCallContext);
        assertEquals(catalog.getCurrentPlans().size(), 1);

        final PlanPhaseSpecifier planPhaseSpec = new PlanPhaseSpecifier("hello-monthly", null);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(),  new DefaultEntitlementSpecifier(planPhaseSpec), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, testCallContext);

        Subscription refreshedBaseEntitlement = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement.getChargedThroughDate(), new LocalDate(2016, 6, 1));

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addWeeks(1);
        assertListenerStatus();

        refreshedBaseEntitlement = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement.getChargedThroughDate(), new LocalDate(2016, 7, 8));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();


        refreshedBaseEntitlement = subscriptionApi.getSubscriptionForEntitlementId(baseEntitlement.getId(), testCallContext);
        assertEquals(refreshedBaseEntitlement.getChargedThroughDate(), new LocalDate(2016, 8, 8));
    }


    private Entitlement createEntitlement(final String planName, final boolean expectPayment) throws EntitlementApiException {
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(planName, null);
        return createEntitlement(spec, null, expectPayment);
    }

    private Entitlement createEntitlement(final PlanPhaseSpecifier spec, final List<PlanPhasePriceOverride> overrides, final boolean expectPayment) throws EntitlementApiException {
        if (expectPayment) {
            busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        } else {
            busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        }
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, overrides), UUID.randomUUID().toString(), null, null, false, true, ImmutableList.<PluginProperty>of(), testCallContext);
        assertListenerStatus();
        return entitlementApi.getEntitlementForId(entitlementId, testCallContext);
    }

    private void setupTenant() throws TenantApiException {
        final UUID uuid = UUID.randomUUID();
        final String externalKey = uuid.toString();
        final String apiKey = externalKey + "-Key";
        final String apiSecret = externalKey + "-$3cr3t";
        // Only place where we use callContext
        tenant = tenantUserApi.createTenant(new DefaultTenant(uuid, init, init, externalKey, apiKey, apiSecret), callContext);

        testCallContext = new DefaultCallContext(null, tenant.getId(), "tester", CallOrigin.EXTERNAL, UserType.TEST,
                                                 "good reason", "trust me", uuid, clock);
    }

    private void setupAccount() throws Exception {

        final AccountData accountData = getAccountData(0);
        account = accountUserApi.createAccount(accountData, testCallContext);
        assertNotNull(account);

        final PaymentMethodPlugin info = createPaymentMethodPlugin();
        paymentApi.addPaymentMethod(account, UUID.randomUUID().toString(), BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME, true, info, PLUGIN_PROPERTIES, testCallContext);
    }

}
