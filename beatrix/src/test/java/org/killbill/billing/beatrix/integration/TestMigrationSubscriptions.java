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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.tag.ControlTagType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertNotNull;

//
// These scenarios emulate commons migrations problems (they go on verifying proper entitlement startDate, and proper billing startDate along with invoices, ..)
//
// Note that all events associated to entitlement are called 'BLOCK' because of the underlying `BlockingState` mechanism used to implement those
// (See http://killbill.io/blog/blockingstate-abstractions/)
//
public class TestMigrationSubscriptions extends TestIntegrationBase {

    //
    // Scenario: On 2016-1-1, we decide to migrate a subscription with a cutOverDate of 2016-1-10 (10 days in the future) and a billing date of 2016-1-31
    // (note that 2016-1-31 + 30 days trial = 2016-03-01, which aligns well with the BCD=1 we set on our test account)
    //
    //
    @Test(groups = "slow")
    public void testSimpleMigrationBP() throws Exception {

        clock.setDay(new LocalDate(2016, 1, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);

        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();

        // We set both entitlement and billing date with desired value
        final LocalDate entitlementMigrationDate = new LocalDate(2016, 1, 10);
        final LocalDate billingMigrationDate = new LocalDate(2016, 1, 31);

        // Entitlement wil be created in PENDING state
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), spec, "bundleKey", null, entitlementMigrationDate, billingMigrationDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        Assert.assertEquals(entitlement.getState(), EntitlementState.PENDING);

        // Move clock to entitlementMigrationDate (migration cutOverDate), and expect the associated event
        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(10);
        assertListenerStatus();

        final Entitlement activeEntitlement = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        Assert.assertEquals(activeEntitlement.getState(), EntitlementState.ACTIVE);

        // Move clock to billingMigrationDate and expect the CREATE event along a $0 invoice for the trial
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        clock.addDays(21);
        assertListenerStatus();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(billingMigrationDate, null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, expectedInvoices);
        expectedInvoices.clear();

        // Move clock next month for first RECURRING invoice
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final LocalDate startDate = billingMigrationDate.plusDays(30);
        final LocalDate endDate = startDate.plusMonths(1);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(startDate, endDate, InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, expectedInvoices);
        expectedInvoices.clear();
    }

    //
    // Scenario: On 2016-1-1, we decide to migrate a subscription with a cutOverDate of 2015-12-20 (12 days in the past) and a billing date of 2016-2-01, we we want to skip the trial
    // (note that since we skip the trial billingDate = 2016-2-01 aligns well with the BCD=1 we set on our test account)
    //
    //
    @Test(groups = "slow")
    public void testSimpleMigrationBPSkipTrial() throws Exception {

        clock.setDay(new LocalDate(2016, 1, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);

        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();

        // We set both entitlement and billing date with desired value
        final LocalDate entitlementMigrationDate = new LocalDate(2015, 12, 20);
        final LocalDate billingMigrationDate = new LocalDate(2016, 2, 1);

        // Entitlement wil be created in ACTIVE state because entitlementMigrationDate was set in the past
        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), spec, "bundleKey", null, entitlementMigrationDate, billingMigrationDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        Assert.assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        // Move clock next month for first RECURRING invoice (note that TRIAL was correctly skipped, we directly start RECURRING on billingMigrationDate
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final LocalDate startDate = billingMigrationDate;
        final LocalDate endDate = startDate.plusMonths(1);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(startDate, endDate, InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, expectedInvoices);
        expectedInvoices.clear();

    }

    //
    // Scenario: On 2016-1-1, we decide to migrate a subscription with a cutOverDate of 2015-12-20 (12 days in the past) and a billing date of 2016-2-01, we we want to skip the trial.
    //           In addition we subscription needs to be future cancelled (2016-2-15) at the time we migrate it
    // (note that since we skip the trial billingDate = 2016-2-01 aligns well with the BCD=1 we set on our test account)
    //
    //
    @Test(groups = "slow")
    public void testSimpleMigrationBPSkipTrialWithPendingCancellation() throws Exception {

        clock.setDay(new LocalDate(2016, 1, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);

        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();

        final LocalDate entitlementMigrationDate = new LocalDate(2015, 12, 20);

        // We set both entitlement and billing date with desired value
        final LocalDate billingMigrationDate = new LocalDate(2016, 2, 1);
        final LocalDate effectiveCancellationDate = new LocalDate(2016, 2, 15);

        // Entitlement wil be created in ACTIVE state because entitlementMigrationDate was set in the past
        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), spec, "bundleKey", null, entitlementMigrationDate, billingMigrationDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        Assert.assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        // Perform the cancellation (we did not move the clock, the is is future cancellation done at the time we decide to migrate)
        entitlement.cancelEntitlementWithDate(effectiveCancellationDate, true, ImmutableList.<PluginProperty>of(), callContext);

        // Billing starts straight on EVERGREEN
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // The invoice will be pro-rated up to the cancellation date
        final LocalDate startDate = billingMigrationDate;
        final LocalDate endDate = effectiveCancellationDate;
        expectedInvoices.add(new ExpectedInvoiceItemCheck(startDate, endDate, InvoiceItemType.RECURRING, new BigDecimal("120.67")));
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, expectedInvoices);
        expectedInvoices.clear();

        // Move to cancellation date
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE);
        clock.addMonths(14);
        assertListenerStatus();

        final Entitlement cancelledEntitlement = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        Assert.assertEquals(cancelledEntitlement.getState(), EntitlementState.CANCELLED);
    }


    //
    // Scenario: On 2016-1-1, we decide to migrate a bundle (one BP and one AO). We migrate straight to EVERGREEN phase. The scenario is very similar to previous one
    //           but with an additional AO (by using the createBaseEntitlementWithAddOns api).
    //
    //  Note that while convenient to migrate a bundle at once (BP + AOS), one could do several calls for each subscription. The advantage of the later approach is that
    //  the granularity in terms of alignments for when things start and which phase are skipped can be better controlled.
    //
    @Test(groups = "slow")
    public void testSimpleMigrationBundle() throws Exception {

        clock.setDay(new LocalDate(2016, 1, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);

        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // We set both entitlement and billing date with desired value
        final LocalDate entitlementMigrationDate = new LocalDate(2015, 12, 20);
        final LocalDate billingMigrationDate = new LocalDate(2016, 2, 1);

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
        final PlanPhaseSpecifier addOnSpec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);

        final String externalKey = "baseExternalKey";
        EntitlementSpecifier baseEntitlementSpecifier = new DefaultEntitlementSpecifier(baseSpec, null);
        EntitlementSpecifier addOnEntitlementSpecifier1 = new DefaultEntitlementSpecifier(addOnSpec1, null);

        final List<EntitlementSpecifier> specifierList = new ArrayList<EntitlementSpecifier>();
        specifierList.add(baseEntitlementSpecifier);
        specifierList.add(addOnEntitlementSpecifier1);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK);
        BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifier = buildBaseEntitlementWithAddOnsSpecifier(entitlementMigrationDate, billingMigrationDate, externalKey, specifierList);
        List<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementWithAddOnsSpecifier);

        final List<UUID> baseEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(),
                                                                                                   baseEntitlementWithAddOnsSpecifierList,
                                                                                                   true,
                                                                                                   ImmutableList.<PluginProperty>of(),
                                                                                                   callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(baseEntitlements.get(0), callContext);
        Assert.assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

        // Billing starts straight on EVERGREEN
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.CREATE, NextEvent.INVOICE, NextEvent.NULL_INVOICE,
                                      NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();
    }


    @Test(groups = "slow")
    public void testMigrationWithMultipleBundlesAndDifferentDates() throws Exception {

        clock.setDay(new LocalDate(2016, 1, 1));
        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // We set both entitlement and billing date with desired value
        final LocalDate entitlementMigrationDateBundle1 = new LocalDate(2015, 12, 20);
        final LocalDate billingMigrationDateBundle1 = new LocalDate(2016, 2, 1);

        // We set both entitlement and billing date with desired value
        final LocalDate entitlementMigrationDateBundle2 = new LocalDate(2015, 12, 20);
        final LocalDate billingMigrationDateBundle2 = new LocalDate(2016, 3, 1);

        final PlanPhaseSpecifier baseSpec = new PlanPhaseSpecifier("Shotgun", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);
        final PlanPhaseSpecifier addOnSpec1 = new PlanPhaseSpecifier("Telescopic-Scope", BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.EVERGREEN);

        final String externalKey = "baseExternalKey";
        EntitlementSpecifier baseEntitlementSpecifier = new DefaultEntitlementSpecifier(baseSpec, null);
        EntitlementSpecifier addOnEntitlementSpecifier1 = new DefaultEntitlementSpecifier(addOnSpec1, null);

        final List<EntitlementSpecifier> specifierList = new ArrayList<EntitlementSpecifier>();
        specifierList.add(baseEntitlementSpecifier);
        specifierList.add(addOnEntitlementSpecifier1);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK, NextEvent.BLOCK);
        BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifierBundle1 =
                buildBaseEntitlementWithAddOnsSpecifier(entitlementMigrationDateBundle1, billingMigrationDateBundle1, externalKey, specifierList);
        List<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifierList = new ArrayList<BaseEntitlementWithAddOnsSpecifier>();
        BaseEntitlementWithAddOnsSpecifier baseEntitlementWithAddOnsSpecifierBundle2 =
                buildBaseEntitlementWithAddOnsSpecifier(entitlementMigrationDateBundle2, billingMigrationDateBundle2, externalKey, specifierList);
        baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementWithAddOnsSpecifierBundle1);
        baseEntitlementWithAddOnsSpecifierList.add(baseEntitlementWithAddOnsSpecifierBundle2);

        final List<UUID> baseEntitlements = entitlementApi.createBaseEntitlementsWithAddOns(account.getId(),
                                                                                            baseEntitlementWithAddOnsSpecifierList,
                                                                                            true,
                                                                                            ImmutableList.<PluginProperty>of(),
                                                                                            callContext);
        assertListenerStatus();
        Assert.assertEquals(entitlementApi.getEntitlementForId(baseEntitlements.get(0), callContext).getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(entitlementApi.getEntitlementForId(baseEntitlements.get(1), callContext).getState(), EntitlementState.ACTIVE);

        // Billing starts straight on EVERGREEN for Bundle 1 after 1 month
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.CREATE, NextEvent.INVOICE, NextEvent.NULL_INVOICE,
                                      NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();

        // Billing starts straight on EVERGREEN for Bundle 2 after 2 months
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.CREATE, NextEvent.INVOICE,
                                      NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE,
                                      NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();

        // Next month we should still get one single invoice and payment / invoice payment
        clock.addMonths(1);
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        assertListenerStatus();
    }

    // Not exactly migration tests, but verify correct behavior when using BlockingState (see https://github.com/killbill/killbill/issues/744)

    @Test(groups = "slow")
    public void testBlockingStatesV1() throws Exception {
        final DateTime initialDate = new DateTime(2017, 3, 1, 0, 1, 35, 0, DateTimeZone.UTC);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState blockingState1 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "state1", "Service", false, false, true, null);
        subscriptionApi.addBlockingState(blockingState1, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        clock.addDays(1);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        entitlementApi.createBaseEntitlement(account.getId(), spec, "bundleExternalKey", ImmutableList.<PlanPhasePriceOverride>of(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        clock.addMonths(1);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final BlockingState blockingState2 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "state2", "Service", false, false, false, null);
        subscriptionApi.addBlockingState(blockingState2, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testBlockingStatesV2() throws Exception {
        final DateTime initialDate = new DateTime(2017, 3, 1, 0, 1, 35, 0, DateTimeZone.UTC);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        final BlockingState blockingState1 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "state1", "Service", false, false, true, null);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial", null);

        // Unlike the previous scenario, we create the subscription and set the blocking state at the same time
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CREATE, NextEvent.BLOCK);
        subscriptionApi.addBlockingState(blockingState1, null, ImmutableList.<PluginProperty>of(), callContext);
        entitlementApi.createBaseEntitlement(account.getId(), spec, "bundleExternalKey", ImmutableList.<PlanPhasePriceOverride>of(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        clock.addMonths(1);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final BlockingState blockingState2 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "state2", "Service", false, false, false, null);
        subscriptionApi.addBlockingState(blockingState2, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testBlockingStatesV3() throws Exception {
        final DateTimeZone timeZone = DateTimeZone.forID("America/Los_Angeles");

        // 2017-03-12 00:01:35 (change to DST happens at 2am on that day)
        final DateTime initialDate = new DateTime(2017, 3, 12, 0, 1, 35, 0, timeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        // Account in PDT
        final AccountData accountData = new MockAccountBuilder().currency(Currency.USD)
                                                                .timeZone(timeZone)
                                                                // Fixed reference time, regardless of current DST
                                                                .referenceTime(clock.getUTCNow())
                                                                .build();

        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        assertNotNull(account);

        busHandler.pushExpectedEvent(NextEvent.TAG);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        assertListenerStatus();

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        entitlementApi.createBaseEntitlement(account.getId(), spec, "bundleExternalKey", ImmutableList.<PlanPhasePriceOverride>of(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Add less than a day between the CREATE and the BLOCK, to verify invoicing behavior
        clock.setTime(initialDate.plusHours(23).plusMinutes(30));

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState blockingState1 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "state1", "Service", false, false, true, null);
        subscriptionApi.addBlockingState(blockingState1, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.NULL_INVOICE);
        tagUserApi.removeTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        assertListenerStatus();

        clock.addMonths(1);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final BlockingState blockingState2 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "state2", "Service", false, false, false, null);
        subscriptionApi.addBlockingState(blockingState2, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testBlockingStatesV4() throws Exception {
        final DateTime initialDate = new DateTime(2017, 3, 1, 0, 1, 35, 0, DateTimeZone.UTC);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState blockingState1 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "state1", "Service", false, false, true, null);
        subscriptionApi.addBlockingState(blockingState1, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        clock.addDays(1);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID baseEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), spec, "bundleExternalKey", ImmutableList.<PlanPhasePriceOverride>of(), null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement baseEntitlement = entitlementApi.getEntitlementForId(baseEntitlementId, callContext);

        clock.addDays(1);

        // Add an add-on while bundle is already blocked
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("cleaning-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        entitlementApi.addEntitlement(baseEntitlement.getBundleId(), spec2, ImmutableList.<PlanPhasePriceOverride>of(), null, null, false, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        clock.addMonths(1);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final BlockingState blockingState2 = new DefaultBlockingState(account.getId(), BlockingStateType.ACCOUNT, "state2", "Service", false, false, false, null);
        subscriptionApi.addBlockingState(blockingState2, null, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
    }

    private BaseEntitlementWithAddOnsSpecifier buildBaseEntitlementWithAddOnsSpecifier(final LocalDate entitlementMigrationDate, final LocalDate billingMigrationDate, final String externalKey, final List<EntitlementSpecifier> specifierList) {
        return new BaseEntitlementWithAddOnsSpecifier() {
                @Override
                public UUID getBundleId() {
                    return null;
                }
                @Override
                public String getExternalKey() {
                    return externalKey;
                }
                @Override
                public Iterable<EntitlementSpecifier> getEntitlementSpecifier() {
                    return specifierList;
                }
                @Override
                public LocalDate getEntitlementEffectiveDate() {
                    return entitlementMigrationDate;
                }
                @Override
                public LocalDate getBillingEffectiveDate() {
                    return billingMigrationDate;
                }
                @Override
                public boolean isMigrated() {
                    return false;
                }
            };
    }
}
