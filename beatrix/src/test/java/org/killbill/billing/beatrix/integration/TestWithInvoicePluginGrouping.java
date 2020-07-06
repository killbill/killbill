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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.plugin.api.OnFailureInvoiceResult;
import org.killbill.billing.invoice.plugin.api.OnSuccessInvoiceResult;
import org.killbill.billing.invoice.plugin.api.PriorInvoiceResult;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestWithInvoicePluginGrouping extends TestIntegrationBase {

    @Inject
    private OSGIServiceRegistration<InvoicePluginApi> pluginRegistry;

    private TestInvoicePluginApiWithGrouping testInvoicePluginApiWithGrouping;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();

        this.testInvoicePluginApiWithGrouping = new TestInvoicePluginApiWithGrouping();
        pluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return "TestInvoicePluginApiWithGrouping";
            }

            @Override
            public String getPluginName() {
                return "TestInvoicePluginApiWithGrouping";
            }

            @Override
            public String getRegistrationName() {
                return "TestInvoicePluginApiWithGrouping";
            }
        }, testInvoicePluginApiWithGrouping);
    }

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        if (hasFailed()) {
            return;
        }

        testInvoicePluginApiWithGrouping.reset();
    }

    @Test(groups = "slow")
    public void testGroupEachChargeItemOnSeparateInvoice() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final InvoiceItem externalCharge1 = new ExternalChargeInvoiceItem(UUID.randomUUID(),
                                                                          null,
                                                                          null,
                                                                          account.getId(),
                                                                          null,
                                                                          UUID.randomUUID(),
                                                                          null,
                                                                          null,
                                                                          null,
                                                                          null,
                                                                          null,
                                                                          null,
                                                                          null,
                                                                          new LocalDate(2012, 4, 1),
                                                                          null,
                                                                          new BigDecimal("33.80"),
                                                                          null,
                                                                          account.getCurrency(),
                                                                          null,
                                                                          null);
        final InvoiceItem externalCharge2 = new ExternalChargeInvoiceItem(UUID.randomUUID(),
                                                                          null,
                                                                          null,
                                                                          account.getId(),
                                                                          null,
                                                                          UUID.randomUUID(),
                                                                          null,
                                                                          null,
                                                                          null,
                                                                          null,
                                                                          null,
                                                                          null,
                                                                          null,
                                                                          new LocalDate(2012, 4, 1),
                                                                          null,
                                                                          new BigDecimal("33.80"),
                                                                          null,
                                                                          account.getCurrency(),
                                                                          null,
                                                                          null);

        // Put each charge on its own invoice
        testInvoicePluginApiWithGrouping.subscriptionToGroup.put(externalCharge1.getSubscriptionId(), 0);
        testInvoicePluginApiWithGrouping.subscriptionToGroup.put(externalCharge2.getSubscriptionId(), 1);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT,
                                      NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final List<InvoiceItem> invoiceItems = invoiceUserApi.insertExternalCharges(account.getId(), clock.getUTCNow().toLocalDate(), ImmutableList.<InvoiceItem>of(externalCharge1, externalCharge2), true, null, callContext);
        assertListenerStatus();

        assertEquals(invoiceItems.size(), 2);
        assertNotEquals(invoiceItems.get(0).getInvoiceId(), invoiceItems.get(1).getInvoiceId());

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 2);
        testInvoicePluginApiWithGrouping.reset();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("33.80")));
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.EXTERNAL_CHARGE, new BigDecimal("33.80")));
    }

    @Test(groups = "slow")
    public void testGroupEachItemOnSeparateInvoice() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription1 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey1", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription1.getId(), internalCallContext);

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey2", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription2.getId(), internalCallContext);

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);

        // Put each subscription on its own invoice
        testInvoicePluginApiWithGrouping.subscriptionToGroup.put(bpSubscription1.getId(), 0);
        testInvoicePluginApiWithGrouping.subscriptionToGroup.put(bpSubscription2.getId(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE,
                                      NextEvent.NULL_INVOICE,
                                      NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT,
                                      NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 3);
        testInvoicePluginApiWithGrouping.reset();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 4);

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
    }

    @Test(groups = "slow")
    public void testGroupEachItemOnSeparateInvoiceWithCBAOnFirstInvoice() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription1 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey1", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription1.getId(), internalCallContext);

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey2", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription2.getId(), internalCallContext);

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        final InvoiceItem inputCredit = new CreditAdjInvoiceItem(null, account.getId(), clock.getUTCToday(), "VIP", new BigDecimal("10"), account.getCurrency(), null);
        invoiceUserApi.insertCredits(account.getId(), clock.getUTCToday(), ImmutableList.of(inputCredit), true, null, callContext);
        assertListenerStatus();

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);

        // Put each subscription on its own invoice
        testInvoicePluginApiWithGrouping.subscriptionToGroup.put(bpSubscription1.getId(), 0);
        testInvoicePluginApiWithGrouping.subscriptionToGroup.put(bpSubscription2.getId(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE,
                                      NextEvent.NULL_INVOICE,
                                      NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT,
                                      NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 3);
        testInvoicePluginApiWithGrouping.reset();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 5);

        final int invWithCredit = invoices.get(3).getCreditedAmount().compareTo(BigDecimal.ZERO) == 0 ? 5 : 4;

        invoiceChecker.checkInvoice(account.getId(), invWithCredit, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-10.00")));

        invoiceChecker.checkInvoice(account.getId(), invWithCredit == 4 ? 5 : 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
    }

    @Test(groups = "slow")
    public void testGroupEachItemOnSeparateInvoiceWithExactCBAOnBothInvoices() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription1 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey1", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription1.getId(), internalCallContext);

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey2", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription2.getId(), internalCallContext);

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        final InvoiceItem inputCredit = new CreditAdjInvoiceItem(null, account.getId(), clock.getUTCToday(), "VIP", new BigDecimal("59.90"), account.getCurrency(), null);
        invoiceUserApi.insertCredits(account.getId(), clock.getUTCToday(), ImmutableList.of(inputCredit), true, null, callContext);
        assertListenerStatus();

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);

        // Put each subscription on its own invoice
        testInvoicePluginApiWithGrouping.subscriptionToGroup.put(bpSubscription1.getId(), 0);
        testInvoicePluginApiWithGrouping.subscriptionToGroup.put(bpSubscription2.getId(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE,
                                      NextEvent.NULL_INVOICE,
                                      NextEvent.INVOICE,
                                      NextEvent.INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 3);
        testInvoicePluginApiWithGrouping.reset();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 5);

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-29.95")));

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-29.95")));

        assertEquals(invoiceUserApi.getAccountCBA(account.getId(), callContext).compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testGroupEachItemOnSeparateInvoiceWithExtraCBAOnBothInvoices() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription1 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey1", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription1.getId(), internalCallContext);

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey2", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription2.getId(), internalCallContext);

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        final InvoiceItem inputCredit = new CreditAdjInvoiceItem(null, account.getId(), clock.getUTCToday(), "VIP", new BigDecimal("100"), account.getCurrency(), null);
        invoiceUserApi.insertCredits(account.getId(), clock.getUTCToday(), ImmutableList.of(inputCredit), true, null, callContext);
        assertListenerStatus();

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 1);
        testInvoicePluginApiWithGrouping.reset();

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);

        // Put each subscription on its own invoice
        testInvoicePluginApiWithGrouping.subscriptionToGroup.put(bpSubscription1.getId(), 0);
        testInvoicePluginApiWithGrouping.subscriptionToGroup.put(bpSubscription2.getId(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE,
                                      NextEvent.NULL_INVOICE,
                                      NextEvent.INVOICE,
                                      NextEvent.INVOICE);
        clock.addDays(30);
        assertListenerStatus();

        assertEquals(testInvoicePluginApiWithGrouping.successCalls, 3);
        testInvoicePluginApiWithGrouping.reset();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 5);

        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-29.95")));

        invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("-29.95")));

        assertEquals(invoiceUserApi.getAccountCBA(account.getId(), callContext).compareTo(new BigDecimal("40.10")), 0);
    }

    public static class TestInvoicePluginApiWithGrouping implements InvoicePluginApi {

        Map<UUID, Integer> subscriptionToGroup = new HashMap<UUID, Integer>();
        int successCalls = 0;

        @Override
        public PriorInvoiceResult priorCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> iterable) {
            assertNull(invoiceContext.getInvoice());

            return new PriorInvoiceResult() {

                @Override
                public boolean isAborted() {
                    return false;
                }

                @Override
                public DateTime getRescheduleDate() {
                    return null;
                }
            };
        }

        @Override
        public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice invoice,
                                                           final boolean isDryRun,
                                                           final Iterable<PluginProperty> pluginProperties,
                                                           final CallContext callContext) {
            assertNotNull(invoice);

            final List<InvoiceItem> updatedInvoiceItems = new LinkedList<InvoiceItem>();
            if (subscriptionToGroup.isEmpty()) {
                return updatedInvoiceItems;
            }

            final Map<Integer, UUID> groupToInvoiceId = new HashMap<Integer, UUID>();
            for (int i = 0; i <= Collections.max(subscriptionToGroup.values()); i++) {
                groupToInvoiceId.put(i, UUID.randomUUID());
            }
            log.info("Dispatching items from {} to {}", invoice.getId(), groupToInvoiceId.values());

            for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
                final InvoiceItem updatedInvoiceItem = Mockito.spy(invoiceItem);
                final UUID newInvoiceId = groupToInvoiceId.get(subscriptionToGroup.get(invoiceItem.getSubscriptionId()));
                Mockito.when(updatedInvoiceItem.getInvoiceId()).thenReturn(newInvoiceId == null ? invoiceItem.getInvoiceId() : newInvoiceId);
                updatedInvoiceItems.add(updatedInvoiceItem);
            }
            return updatedInvoiceItems;
        }

        @Override
        public OnSuccessInvoiceResult onSuccessCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> iterable) {
            successCalls += 1;
            return null;
        }

        @Override
        public OnFailureInvoiceResult onFailureCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> iterable) {
            fail();
            return null;
        }

        public void reset() {
            subscriptionToGroup.clear();
            successCalls = 0;
        }
    }
}
