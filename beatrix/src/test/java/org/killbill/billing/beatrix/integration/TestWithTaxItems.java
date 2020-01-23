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

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.TaxInvoiceItem;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.plugin.api.OnFailureInvoiceResult;
import org.killbill.billing.invoice.plugin.api.OnSuccessInvoiceResult;
import org.killbill.billing.invoice.plugin.api.PriorInvoiceResult;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestWithTaxItems extends TestIntegrationBase {

    @Inject
    private OSGIServiceRegistration<InvoicePluginApi> pluginRegistry;

    private TestInvoicePluginApi testInvoicePluginApi;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();

        this.testInvoicePluginApi = new TestInvoicePluginApi();
        pluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return "TaxInvoicePluginApi";
            }

            @Override
            public String getPluginName() {
                return "TaxInvoicePluginApi";
            }

            @Override
            public String getRegistrationName() {
                return "TaxInvoicePluginApi";
            }
        }, testInvoicePluginApi);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        testInvoicePluginApi.reset();
    }

    @Test(groups = "slow")
    public void testBasicTaxItems() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // Create original subscription (Trial PHASE) -> $0 invoice.
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        // Move to Evergreen PHASE, but add AUTO_INVOICING_OFF => No invoice
        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(30);
        assertListenerStatus();

        // Add Cleaning ADD_ON => No Invoice
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Cleaning", ProductCategory.ADD_ON, BillingPeriod.MONTHLY);
        assertListenerStatus();

        // Make sure TestInvoicePluginApi will return an additional TAX item
        final UUID pluginInvoiceItemId = UUID.randomUUID();
        testInvoicePluginApi.addTaxItem(new TaxInvoiceItem(pluginInvoiceItemId, null, account.getId(), null, "Tax Item", new LocalDate(2012, 5, 1), BigDecimal.ONE, account.getCurrency()));

        // Remove AUTO_INVOICING_OFF => Invoice + Payment
        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("2.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        final List<InvoiceItem> invoiceItems = invoices.get(1).getInvoiceItems();
        final InvoiceItem taxItem  = Iterables.tryFind(invoiceItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getInvoiceItemType() == InvoiceItemType.TAX;
            }
        }).orNull();
        assertNotNull(taxItem);
        // verify the ID is the one passed by the plugin #818
        assertEquals(taxItem.getId(), pluginInvoiceItemId);

        // Add AUTO_INVOICING_OFF and change to a higher plan on the same day that already include the 'Cleaning' ADD_ON, so it gets cancelled
        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.CANCEL, NextEvent.BLOCK);
        changeEntitlementAndCheckForCompletion(bpSubscription, "Shotgun", BillingPeriod.MONTHLY, BillingActionPolicy.IMMEDIATE);
        assertListenerStatus();

        // Make sure TestInvoicePluginApi will return an additional TAX item
        testInvoicePluginApi.addTaxItem(new TaxInvoiceItem(UUID.randomUUID(), null, account.getId(), null, "Tax Item", new LocalDate(2012, 5, 1), BigDecimal.ONE, account.getCurrency()));

        // Remove AUTO_INVOICING_OFF => Invoice + Payment
        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("2.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));

        // Add AUTO_INVOICING_OFF and change to a higher plan on the same day
        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        busHandler.pushExpectedEvent(NextEvent.CHANGE);
        changeEntitlementAndCheckForCompletion(bpSubscription, "Assault-Rifle", BillingPeriod.MONTHLY, BillingActionPolicy.IMMEDIATE);
        assertListenerStatus();

        // Make sure TestInvoicePluginApi will return an additional TAX item
        // Verify we support passing extra items for a null invoiceItemId #1182 (as advertised in our doc)
        testInvoicePluginApi.addTaxItem(new TaxInvoiceItem(null, null, account.getId(), null, "Tax Item", new LocalDate(2012, 5, 1), BigDecimal.ONE, account.getCurrency()));

        // Remove AUTO_INVOICING_OFF => Invoice + Payment
        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-2.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("599.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-249.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));

    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/637")
    public void testDryRunTaxItemsWithCredits() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice.
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE);

        final InvoiceItem inputCredit = new CreditAdjInvoiceItem(null, account.getId(), clock.getUTCToday(), "VIP", new BigDecimal("100"), account.getCurrency(), null);
        invoiceUserApi.insertCredits(account.getId(), clock.getUTCToday(), ImmutableList.of(inputCredit), true, null, callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 4, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("100")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 4, 1), InvoiceItemType.CREDIT_ADJ, new BigDecimal("-100")));

        // Make sure TestInvoicePluginApi will return an additional TAX item
        testInvoicePluginApi.addTaxItem(new TaxInvoiceItem(UUID.randomUUID(), null, account.getId(), null, "Tax Item", new LocalDate(2012, 4, 1), BigDecimal.ONE, account.getCurrency()));

        // Verify dry-run scenario
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), new LocalDate(2012, 5, 1), new TestDryRunArguments(DryRunType.TARGET_DATE), callContext);
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice,
                                            ImmutableList.<ExpectedInvoiceItemCheck>of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                                                                       new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")),
                                                                                       new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 4, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-30.95"))));

        // Make sure TestInvoicePluginApi will return an additional TAX item
        testInvoicePluginApi.addTaxItem(new TaxInvoiceItem(UUID.randomUUID(), null, account.getId(), null, "Tax Item", new LocalDate(2012, 5, 1), BigDecimal.ONE, account.getCurrency()));

        // Move to Evergreen PHASE to verify non-dry-run scenario
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-30.95")));
    }

    @Test(groups = "slow")
    public void testWithAdjustments() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice.
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        // Make sure TestInvoicePluginApi will return an additional TAX item
        testInvoicePluginApi.addTaxItem(new TaxInvoiceItem(UUID.randomUUID(), null, account.getId(), null, "Tax Item", new LocalDate(2012, 5, 1), new BigDecimal("3.00"), account.getCurrency()));

        // Move to Evergreen PHASE to verify non-dry-run scenario
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        final Invoice secondInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                                  new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                                                  new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("3.00")));

        final InvoiceItem recurringItemId = Iterables.find(secondInvoice.getInvoiceItems(), new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getInvoiceItemType() == InvoiceItemType.RECURRING;
            }
        });

        testInvoicePluginApi.addTaxItem(new TaxInvoiceItem(UUID.randomUUID(), null, account.getId(), null, "Tax Item (refund)", new LocalDate(2012, 5, 8), new BigDecimal("-1.00"), account.getCurrency()));

        clock.addDays(7);
        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.insertInvoiceItemAdjustment(account.getId(), secondInvoice.getId(), recurringItemId.getId(), new LocalDate(2012, 5, 8), new BigDecimal("10"), account.getCurrency(), "", "", ImmutableList.of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 5, 8), InvoiceItemType.ITEM_ADJ, new BigDecimal("-10.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), new LocalDate(2012, 5, 8), InvoiceItemType.CBA_ADJ, new BigDecimal("11.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("3.00")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 8), null, InvoiceItemType.TAX, new BigDecimal("-1.00")));
    }



    @Test(groups = "slow")
    public void testTaxRateStartAndEndDate() throws Exception {
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2017, 11, 15));

        final AccountData accountData = getAccountData(null);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice.
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(
                account.getId(),
                "bundleKey",
                "Pistol",
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                NextEvent.CREATE,
                NextEvent.BLOCK,
                NextEvent.INVOICE
                                                                                            );
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2017, 11, 15), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        final InvoiceItem inputCredit = new CreditAdjInvoiceItem(null, account.getId(), clock.getUTCToday(), "VIP", new BigDecimal("100"), account.getCurrency(), null);
        invoiceUserApi.insertCredits(account.getId(), clock.getUTCToday(), ImmutableList.of(inputCredit), true, null, callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 11, 15), new LocalDate(2017, 11, 15), InvoiceItemType.CBA_ADJ, new BigDecimal("100")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 11, 15), new LocalDate(2017, 11, 15), InvoiceItemType.CREDIT_ADJ, new BigDecimal("-100")));


        // Make sure TestInvoicePluginApi will return an additional TAX item
        testInvoicePluginApi.addTaxItem(
                new TaxInvoiceItem(
                        UUID.randomUUID(),
                        null,
                        null,
                        account.getId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new LocalDate(2017, 12, 15),
                        new LocalDate(2017, 12, 31),
                        "Tax Item 2017",
                        BigDecimal.ONE,
                        account.getCurrency(),
                        null,
                        null
                )
        );
        testInvoicePluginApi.addTaxItem(
                new TaxInvoiceItem(
                        UUID.randomUUID(),
                        null,
                        null,
                        account.getId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new LocalDate(2018, 1, 1),
                        new LocalDate(2018, 1, 15),
                        "Tax Item 2018",
                        BigDecimal.TEN,
                        account.getCurrency(),
                        null,
                        null
                )
        );

        // Move to Evergreen PHASE to verify non-dry-run scenario
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 15), new LocalDate(2018, 1, 15), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 15), new LocalDate(2017, 12, 31), InvoiceItemType.TAX, new BigDecimal("1.0")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 1, 15), InvoiceItemType.TAX, new BigDecimal("10.0")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 15), new LocalDate(2017, 12, 15), InvoiceItemType.CBA_ADJ, new BigDecimal("-40.95")));
    }

    @Test(groups = "slow")
    public void testUpdateTaxItems() throws Exception {

        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);

        // Create original subscription (Trial PHASE) -> $0 invoice.
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        // Add tags to keep DRAFT invoices and reuse them
        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);
        add_AUTO_INVOICING_REUSE_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        // Make sure TestInvoicePluginApi will return an additional TAX item
        final UUID invoiceTaxItemId = UUID.randomUUID();
        testInvoicePluginApi.addTaxItem(new TaxInvoiceItem(invoiceTaxItemId, null, account.getId(), null, "Tax Item", new LocalDate(2012, 4, 1), BigDecimal.ONE, account.getCurrency()));

        // Insert external charge autoCommit = false => Invoice will be in DRAFT
        invoiceUserApi.insertExternalCharges(account.getId(), clock.getUTCNow().toLocalDate(), ImmutableList.<InvoiceItem>of(new ExternalChargeInvoiceItem(null, account.getId(), null, "foo", new LocalDate(2012, 4, 1), null, new BigDecimal("33.80"),
                                                                                                                                                           account.getCurrency(), null)), false, null, callContext);

        // Make sure TestInvoicePluginApi **update** the original TAX item
        testInvoicePluginApi.addTaxItem(new TaxInvoiceItem(invoiceTaxItemId, null, account.getId(), null, "Tax Item", new LocalDate(2012, 4, 1), new BigDecimal("12.45"), account.getCurrency()));

        // Move to Evergreen PHASE, but invoice remains in DRAFT mode
        busHandler.pushExpectedEvents(NextEvent.PHASE /*, NextEvent.INVOICE */);
        clock.addDays(30);
        assertListenerStatus();

        final List<Invoice> accountInvoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(accountInvoices.size(), 2);

        // Commit invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.commitInvoice(accountInvoices.get(1).getId(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("33.80")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.TAX, new BigDecimal("12.45")));

    }

    public class TestInvoicePluginApi implements InvoicePluginApi {

        private final List<TaxInvoiceItem> taxItems;

        public TestInvoicePluginApi() {
            taxItems = new ArrayList<TaxInvoiceItem>();
        }

        @Override
        public PriorInvoiceResult priorCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> pluginProperties) {
            return null;
        }

        @Override
        public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice invoice, final boolean isDryRun, final Iterable<PluginProperty> pluginProperties, final CallContext callContext) {
            final List<InvoiceItem> result = new ArrayList<InvoiceItem>();
            for (final TaxInvoiceItem item : taxItems) {
                final String description;
                if (item.getDescription() != null) {
                    description = item.getDescription();
                } else {
                    description = "Tax Item";
                }

                result.add(
                        new TaxInvoiceItem(
                                item.getId(),
                                item.getCreatedDate(),
                                invoice.getId(),
                                invoice.getAccountId(),
                                item.getBundleId(),
                                item.getSubscriptionId(),
                                item.getProductName(),
                                item.getPlanName(),
                                item.getPhaseName(),
                                item.getUsageName(),
                                null,
                                item.getPrettyProductName(),
                                item.getPrettyPlanName(),
                                item.getPrettyPhaseName(),
                                item.getPrettyUsageName(),
                                item.getStartDate(),
                                item.getEndDate(),
                                description,
                                item.getAmount(),
                                invoice.getCurrency(),
                                item.getLinkedItemId(),
                                item.getItemDetails())
                          );
            }
            return result;
        }

        @Override
        public OnSuccessInvoiceResult onSuccessCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> pluginProperties) {
            Assert.assertFalse(invoiceContext.isRescheduled());

            final Invoice invoice = invoiceContext.getInvoice();
            if (invoice != null) {
                for (final TaxInvoiceItem taxInvoiceItem : taxItems) {
                    // If we specified the invoiceItemId, we should be able to find such tax item
                    if (taxInvoiceItem.getId() != null) {
                        final InvoiceItem createdTaxInvoiceItem = Iterables.<InvoiceItem>find(invoice.getInvoiceItems(),
                                                                                              new Predicate<InvoiceItem>() {
                                                                                                  @Override
                                                                                                  public boolean apply(final InvoiceItem invoiceItem) {
                                                                                                      return invoiceItem.getId().compareTo(taxInvoiceItem.getId()) == 0;
                                                                                                  }
                                                                                              });
                        Assert.assertNotNull(createdTaxInvoiceItem);
                    }
                    Assert.assertEquals(taxInvoiceItem.getAccountId(), taxInvoiceItem.getAccountId());
                }

            }
            reset();

            return null;
        }

        @Override
        public OnFailureInvoiceResult onFailureCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> pluginProperties) {
            Assert.fail();
            return null;
        }

        public void reset() {
            taxItems.clear();
        }

        public void addTaxItem(final TaxInvoiceItem item) {
            taxItems.add(item);
        }
    }
}
