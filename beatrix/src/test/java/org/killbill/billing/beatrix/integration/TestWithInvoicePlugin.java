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
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.awaitility.Awaitility;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.invoice.model.TaxInvoiceItem;
import org.killbill.billing.invoice.notification.DefaultNextBillingDateNotifier;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApiRetryException;
import org.killbill.billing.invoice.plugin.api.OnFailureInvoiceResult;
import org.killbill.billing.invoice.plugin.api.OnSuccessInvoiceResult;
import org.killbill.billing.invoice.plugin.api.PriorInvoiceResult;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.killbill.queue.retry.RetryNotificationEvent;
import org.killbill.queue.retry.RetryableService;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestWithInvoicePlugin extends TestIntegrationBase {

    @Inject
    private OSGIServiceRegistration<InvoicePluginApi> pluginRegistry;

    @Inject
    private NotificationQueueService notificationQueueService;

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
                return "TestInvoicePluginApi";
            }

            @Override
            public String getPluginName() {
                return "TestInvoicePluginApi";
            }

            @Override
            public String getRegistrationName() {
                return "TestInvoicePluginApi";
            }
        }, testInvoicePluginApi);
    }

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        if (hasFailed()) {
            return;
        }

        testInvoicePluginApi.additionalInvoiceItem = null;
        testInvoicePluginApi.shouldAddTaxItem = true;
        testInvoicePluginApi.isAborted = false;
        testInvoicePluginApi.shouldUpdateDescription = false;
        testInvoicePluginApi.rescheduleDate = null;
        testInvoicePluginApi.wasRescheduled = false;
        testInvoicePluginApi.invocationCount = 0;
    }

    @Test(groups = "slow")
    public void testBasicAdditionalExternalChargeItem() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final UUID pluginInvoiceItemId = UUID.randomUUID();
        final UUID pluginLinkedItemId = UUID.randomUUID();
        testInvoicePluginApi.additionalInvoiceItem = new ExternalChargeInvoiceItem(pluginInvoiceItemId,
                                                                                   clock.getUTCNow(),
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
                                                                                   "My charge",
                                                                                   clock.getUTCToday(),
                                                                                   null,
                                                                                   BigDecimal.TEN,
                                                                                   null,
                                                                                   Currency.USD,
                                                                                   pluginLinkedItemId,
                                                                                   null);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 0);

        // Create original subscription (Trial PHASE) -> $0 invoice but plugin added one item
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.EXTERNAL_CHARGE, BigDecimal.TEN));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 1);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);
        final List<InvoiceItem> invoiceItems = invoices.get(0).getInvoiceItems();
        final InvoiceItem externalCharge = Iterables.tryFind(invoiceItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getInvoiceItemType() == InvoiceItemType.EXTERNAL_CHARGE;
            }
        }).orNull();
        assertNotNull(externalCharge);
        // verify the ID is the one passed by the plugin #818
        assertEquals(externalCharge.getId(), pluginInvoiceItemId);
        // verify the ID is the one passed by the plugin #887
        assertEquals(externalCharge.getLinkedItemId(), pluginLinkedItemId);


        // On next invoice we will update the amount and the description of the previously inserted EXTERNAL_CHARGE item
        testInvoicePluginApi.additionalInvoiceItem = new ExternalChargeInvoiceItem(pluginInvoiceItemId,
                                                                                   clock.getUTCNow(),
                                                                                   invoices.get(0).getId(),
                                                                                   account.getId(),
                                                                                   null,
                                                                                   null,
                                                                                   null,
                                                                                   null,
                                                                                   null,
                                                                                   null,
                                                                                   null,
                                                                                   null,
                                                                                   "Update Description",
                                                                                   clock.getUTCToday(),
                                                                                   null,
                                                                                   BigDecimal.ONE,
                                                                                   null,
                                                                                   Currency.USD,
                                                                                   pluginLinkedItemId,
                                                                                   null);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));


        final List<Invoice> invoices2 = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        final List<InvoiceItem> invoiceItems2 = invoices2.get(0).getInvoiceItems();
        final InvoiceItem externalCharge2 = Iterables.tryFind(invoiceItems2, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getInvoiceItemType() == InvoiceItemType.EXTERNAL_CHARGE;
            }
        }).orNull();
        assertNotNull(externalCharge2);

        assertEquals(externalCharge2.getAmount().compareTo(BigDecimal.ONE), 0);
    }

    @Test(groups = "slow")
    public void testBasicAdditionalItemAdjustment() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 0);

        // Create original subscription (Trial PHASE) -> $0 invoice.
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 1);

        // Move to Evergreen PHASE
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 2);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        final InvoiceItem recurringItem = Iterables.find(invoices.get(1).getInvoiceItems(),
                                                         new Predicate<InvoiceItem>() {
                                                             @Override
                                                             public boolean apply(final InvoiceItem input) {
                                                                 return InvoiceItemType.RECURRING.equals(input.getInvoiceItemType());
                                                             }
                                                         });

        // Item adjust the recurring item from the plugin
        final UUID pluginInvoiceItemId = UUID.randomUUID();
        final String itemDetails = "{\n" +
                                   "  \"user\": \"admin\",\n" +
                                   "  \"reason\": \"SLA not met\"\n" +
                                   "}";
        testInvoicePluginApi.additionalInvoiceItem = new ItemAdjInvoiceItem(pluginInvoiceItemId,
                                                                            clock.getUTCNow(),
                                                                            recurringItem.getInvoiceId(),
                                                                            account.getId(),
                                                                            clock.getUTCToday(),
                                                                            "My charge",
                                                                            BigDecimal.TEN.negate(),
                                                                            Currency.USD,
                                                                            recurringItem.getId(),
                                                                            itemDetails);

        // Move one month
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.ITEM_ADJ, BigDecimal.TEN.negate()),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 6, 1), InvoiceItemType.CBA_ADJ, BigDecimal.TEN));

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 6, 1), InvoiceItemType.CBA_ADJ, BigDecimal.TEN.negate()));

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 3);

        final List<Invoice> refreshedInvoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        final List<InvoiceItem> invoiceItems = refreshedInvoices.get(1).getInvoiceItems();
        final InvoiceItem invoiceItemAdjustment = Iterables.tryFind(invoiceItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getInvoiceItemType() == InvoiceItemType.ITEM_ADJ;
            }
        }).orNull();
        assertNotNull(invoiceItemAdjustment);
        // verify the ID is the one passed by the plugin #818
        assertEquals(invoiceItemAdjustment.getId(), pluginInvoiceItemId);
        // verify the details are passed by the plugin #888
        assertEquals(invoiceItemAdjustment.getItemDetails(), itemDetails);
    }

    @Test(groups = "slow")
    public void testAborted() throws Exception {
        testInvoicePluginApi.shouldAddTaxItem = false;

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 0);

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 1);

        // Abort invoice runs
        testInvoicePluginApi.isAborted = true;

        // Move to Evergreen PHASE
        busHandler.pushExpectedEvents(NextEvent.PHASE);
        clock.addDays(30);
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 2);

        // No notification, so by default, the account will not be re-invoiced
        clock.addMonths(1);
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 2);

        // No notification, so by default, the account will not be re-invoiced
        clock.addMonths(1);
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 2);

        // Re-enable invoicing
        testInvoicePluginApi.isAborted = false;

        // Trigger a manual invoice run
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), callContext);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 3);

        // Invoicing resumes
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 8, 1), new LocalDate(2012, 9, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 4);
    }

    @Test(groups = "slow")
    public void testUpdateDescription() throws Exception {
        testInvoicePluginApi.shouldAddTaxItem = false;
        testInvoicePluginApi.shouldUpdateDescription = true;

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice but plugin added one item
        final Entitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final Invoice firstInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        checkInvoiceDescriptions(firstInvoice);

        // Move to Evergreen PHASE
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        final Invoice secondInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                                  new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        checkInvoiceDescriptions(secondInvoice);

        // Cancel START_OF_TERM to make sure odd items like CBA are updated too
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        bpSubscription.cancelEntitlementWithPolicyOverrideBillingPolicy(EntitlementActionPolicy.IMMEDIATE,
                                                                        BillingActionPolicy.START_OF_TERM,
                                                                        ImmutableList.<PluginProperty>of(),
                                                                        callContext);
        assertListenerStatus();
        final Invoice thirdInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("29.95").negate()),
                                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), InvoiceItemType.CBA_ADJ, new BigDecimal("29.95")));
        checkInvoiceDescriptions(thirdInvoice);
    }

    private void checkInvoiceDescriptions(final Invoice invoice) {
        for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            assertEquals(invoiceItem.getDescription(), String.format("[plugin] %s", invoiceItem.getId()));
        }
    }

    @Test(groups = "slow")
    public void testRescheduledViaNotification() throws Exception {
        testInvoicePluginApi.shouldAddTaxItem = false;

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 0);

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 1);
        Assert.assertFalse(testInvoicePluginApi.wasRescheduled);

        // Reschedule invoice generation
        final DateTime utcNow = clock.getUTCNow();
        testInvoicePluginApi.rescheduleDate = new DateTime(2012, 5, 2, utcNow.getHourOfDay(), utcNow.getMinuteOfHour(), utcNow.getSecondOfMinute(), DateTimeZone.UTC);

        // Move to Evergreen PHASE
        busHandler.pushExpectedEvents(NextEvent.PHASE);
        clock.addDays(30);
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 2);
        Assert.assertFalse(testInvoicePluginApi.wasRescheduled);

        // PHASE invoice has been rescheduled, reset rescheduleDate
        testInvoicePluginApi.rescheduleDate = null;

        // Move one day
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(1);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 3);
        Assert.assertTrue(testInvoicePluginApi.wasRescheduled);

        // Invoicing resumes as expected
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 4);
        Assert.assertFalse(testInvoicePluginApi.wasRescheduled);
    }

    @Test(groups = "slow")
    public void testRescheduledViaAPI() throws Exception {
        testInvoicePluginApi.shouldAddTaxItem = false;

        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 0);

        // Create original subscription (Trial PHASE) -> $0 invoice
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 1);

        // Reschedule invoice generation at the time of the PHASE event
        testInvoicePluginApi.rescheduleDate = new DateTime(clock.getUTCNow()).plusDays(30);

        try {
            invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), callContext);
            Assert.fail();
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }
        Assert.assertEquals(testInvoicePluginApi.invocationCount, 2);
        Assert.assertFalse(testInvoicePluginApi.wasRescheduled);

        // Let the next invoice go through
        testInvoicePluginApi.rescheduleDate = null;

        // Move to Evergreen PHASE: two invoice runs will be triggers, one by SubscriptionNotificationKey (PHASE event) and one by NextBillingDateNotificationKey (reschedule)
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.NULL_INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 4);
        // Cannot check wasRescheduled flag, as it would be true only for one of the runs

        // Reschedule next invoice one month in the future
        testInvoicePluginApi.rescheduleDate = clock.getUTCNow().plusMonths(1);
        try {
            invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), callContext);
            Assert.fail();
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }
        Assert.assertEquals(testInvoicePluginApi.invocationCount, 5);
        Assert.assertFalse(testInvoicePluginApi.wasRescheduled);

        // Let the next invoice go through
        testInvoicePluginApi.rescheduleDate = null;

        // Move one month ahead: no NULL_INVOICE this time: since there is already a notification for that date, the reschedule is a no-op (and we keep the isRescheduled flag to false)
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 6);
        Assert.assertFalse(testInvoicePluginApi.wasRescheduled);
    }

    @Test(groups = "slow")
    public void testWithRetries() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 0);

        // Make invoice plugin fail
        testInvoicePluginApi.shouldThrowException = true;

        // Create original subscription (Trial PHASE)
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK);
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        // Invoice failed to generate
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false,  callContext).size(), 0);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 1);

        // Verify bus event has moved to the retry service (can't easily check the timestamp unfortunately)
        // No future notification at this point (FIXED item, the PHASE event is the trigger for the next one)
        checkRetryBusEvents(1, 0);

        // Add 5'
        clock.addDeltaFromReality(5 * 60 * 1000);
        checkRetryBusEvents(2, 0);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 2);

        // Fix invoice plugin
        testInvoicePluginApi.shouldThrowException = false;

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDeltaFromReality(10 * 60 * 1000);
        assertListenerStatus();
        // No notification in the main queue at this point (the PHASE event is the trigger for the next one)
        checkNotificationsNoRetry(0);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 3);

        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false,  callContext).size(), 1);
        invoiceChecker.checkInvoice(account.getId(),
                                    1,
                                    callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate("2012-05-01"));
        assertListenerStatus();
        checkNotificationsNoRetry(1);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 4);

        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false,  callContext).size(), 2);
        invoiceChecker.checkInvoice(account.getId(),
                                    2,
                                    callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));

        // Make invoice plugin fail again
        testInvoicePluginApi.shouldThrowException = true;

        clock.addMonths(1);
        assertListenerStatus();

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 5);

        // Invoice failed to generate
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false,  callContext).size(), 2);

        // Verify notification has moved to the retry service
        checkRetryNotifications("2012-06-01T00:05:00", 1);

        // Add 5'
        clock.addDeltaFromReality(5 * 60 * 1000);
        // Verify there are no notification duplicates
        checkRetryNotifications("2012-06-01T00:15:00", 1);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 6);

        // Fix invoice plugin
        testInvoicePluginApi.shouldThrowException = false;

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDeltaFromReality(10 * 60 * 1000);
        assertListenerStatus();
        checkNotificationsNoRetry(1);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 7);

        // Invoice was generated
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false,  callContext).size(), 3);
        invoiceChecker.checkInvoice(account.getId(),
                                    3,
                                    callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));

        // Make invoice plugin fail again
        testInvoicePluginApi.shouldThrowException = true;

        clock.setTime(new DateTime("2012-07-01T00:00:00"));
        assertListenerStatus();

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 8);

        // Invoice failed to generate
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false,  callContext).size(), 3);

        // Verify notification has moved to the retry service
        checkRetryNotifications("2012-07-01T00:05:00", 1);

        testInvoicePluginApi.shouldThrowException = false;

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDeltaFromReality(5 * 60 * 1000);
        assertListenerStatus();
        checkNotificationsNoRetry(1);

        Assert.assertEquals(testInvoicePluginApi.invocationCount, 9);

        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 4);
    }

    private void checkRetryBusEvents(final int retryNb, final int expectedFutureInvoiceNotifications) throws NoSuchNotificationQueue {
        // Verify notification(s) moved to the retry queue
        Awaitility.await().atMost(15, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final List<NotificationEventWithMetadata> futureInvoiceRetryableBusEvents = getFutureInvoiceRetryableBusEvents();
                return futureInvoiceRetryableBusEvents.size() == 1 && ((RetryNotificationEvent) futureInvoiceRetryableBusEvents.get(0).getEvent()).getRetryNb() == retryNb;
            }
        });
        assertEquals(getFutureInvoiceNotifications().size(), expectedFutureInvoiceNotifications);
    }

    private void checkRetryNotifications(final String retryDateTime, final int expectedFutureInvoiceNotifications) throws NoSuchNotificationQueue {
        // Verify notification(s) moved to the retry queue
        Awaitility.await().atMost(15, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final List<NotificationEventWithMetadata> futureInvoiceRetryableNotifications = getFutureInvoiceRetryableNotifications();
                return futureInvoiceRetryableNotifications.size() == 1 && futureInvoiceRetryableNotifications.get(0).getEffectiveDate().compareTo(new DateTime(retryDateTime, DateTimeZone.UTC)) == 0;
            }
        });
        assertEquals(getFutureInvoiceNotifications().size(), expectedFutureInvoiceNotifications);
    }

    private void checkNotificationsNoRetry(final int main) throws NoSuchNotificationQueue {
        assertEquals(getFutureInvoiceRetryableNotifications().size(), 0);
        assertEquals(getFutureInvoiceNotifications().size(), main);
    }

    private List<NotificationEventWithMetadata> getFutureInvoiceNotifications() throws NoSuchNotificationQueue {
        final NotificationQueue notificationQueue = notificationQueueService.getNotificationQueue(DefaultInvoiceService.INVOICE_SERVICE_NAME, DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);
        return ImmutableList.<NotificationEventWithMetadata>copyOf(notificationQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId()));
    }

    private List<NotificationEventWithMetadata> getFutureInvoiceRetryableNotifications() throws NoSuchNotificationQueue {
        final NotificationQueue notificationQueue = notificationQueueService.getNotificationQueue(RetryableService.RETRYABLE_SERVICE_NAME, DefaultNextBillingDateNotifier.NEXT_BILLING_DATE_NOTIFIER_QUEUE);
        return ImmutableList.<NotificationEventWithMetadata>copyOf(notificationQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId()));
    }

    private List<NotificationEventWithMetadata> getFutureInvoiceRetryableBusEvents() throws NoSuchNotificationQueue {
        final NotificationQueue notificationQueue = notificationQueueService.getNotificationQueue(RetryableService.RETRYABLE_SERVICE_NAME, "invoice-listener");
        return ImmutableList.<NotificationEventWithMetadata>copyOf(notificationQueue.getFutureNotificationForSearchKeys(internalCallContext.getAccountRecordId(), internalCallContext.getTenantRecordId()));
    }

    public class TestInvoicePluginApi implements InvoicePluginApi {

        boolean shouldThrowException = false;
        InvoiceItem additionalInvoiceItem;
        boolean shouldAddTaxItem = true;
        boolean isAborted = false;
        boolean shouldUpdateDescription = false;
        DateTime rescheduleDate;
        boolean wasRescheduled = false;
        int invocationCount = 0;

        @Override
        public PriorInvoiceResult priorCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> iterable) {
            invocationCount++;
            wasRescheduled = invoiceContext.isRescheduled();
            return new PriorInvoiceResult() {

                @Override
                public boolean isAborted() {
                    return isAborted;
                }

                @Override
                public DateTime getRescheduleDate() {
                    return rescheduleDate;
                }
            };
        }

        @Override
        public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice invoice, final boolean isDryRun, final Iterable<PluginProperty> pluginProperties, final CallContext callContext) {
            if (shouldThrowException) {
                throw new InvoicePluginApiRetryException();
            } else if (additionalInvoiceItem != null) {
                return ImmutableList.<InvoiceItem>of(additionalInvoiceItem);
            } else if (shouldAddTaxItem) {
                return ImmutableList.<InvoiceItem>of(createTaxInvoiceItem(invoice));
            } else if (shouldUpdateDescription) {
                final List<InvoiceItem> updatedInvoiceItems = new LinkedList<InvoiceItem>();
                for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
                    final InvoiceItem updatedInvoiceItem = Mockito.spy(invoiceItem);
                    Mockito.when(updatedInvoiceItem.getDescription()).thenReturn(String.format("[plugin] %s", invoiceItem.getId()));
                    updatedInvoiceItems.add(updatedInvoiceItem);
                }
                return updatedInvoiceItems;
            } else {
                return ImmutableList.<InvoiceItem>of();
            }
        }

        @Override
        public OnSuccessInvoiceResult onSuccessCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> iterable) {
            return null;
        }

        @Override
        public OnFailureInvoiceResult onFailureCall(final InvoiceContext invoiceContext, final Iterable<PluginProperty> iterable) {
            return null;
        }

        private InvoiceItem createTaxInvoiceItem(final Invoice invoice) {
            return new TaxInvoiceItem(invoice.getId(), invoice.getAccountId(), null, "Tax Item", clock.getUTCNow().toLocalDate(), BigDecimal.ONE, invoice.getCurrency());
        }
    }
}
