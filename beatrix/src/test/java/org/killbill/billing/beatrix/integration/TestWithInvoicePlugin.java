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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.awaitility.Awaitility;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.invoice.model.TaxInvoiceItem;
import org.killbill.billing.invoice.notification.DefaultNextBillingDateNotifier;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApiRetryException;
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
        testInvoicePluginApi.additionalInvoiceItem = null;
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
                                                                                   "My charge",
                                                                                   clock.getUTCToday(),
                                                                                   null,
                                                                                   BigDecimal.TEN,
                                                                                   null,
                                                                                   Currency.USD,
                                                                                   pluginLinkedItemId,
                                                                                   null);

        // Create original subscription (Trial PHASE) -> $0 invoice but plugin added one item
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.EXTERNAL_CHARGE, BigDecimal.TEN));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

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
    }

    @Test(groups = "slow")
    public void testBasicAdditionalItemAdjustment() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create original subscription (Trial PHASE) -> $0 invoice.
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        // Move to Evergreen PHASE
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), null, InvoiceItemType.TAX, new BigDecimal("1.0")));

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
    public void testWithRetries() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Make invoice plugin fail
        testInvoicePluginApi.shouldThrowException = true;

        // Create original subscription (Trial PHASE)
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK);
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        // Invoice failed to generate
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false,  callContext).size(), 0);

        // Verify bus event has moved to the retry service (can't easily check the timestamp unfortunately)
        // No future notification at this point (FIXED item, the PHASE event is the trigger for the next one)
        checkRetryBusEvents(1, 0);

        // Add 5'
        clock.addDeltaFromReality(5 * 60 * 1000);
        checkRetryBusEvents(2, 0);

        // Fix invoice plugin
        testInvoicePluginApi.shouldThrowException = false;

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDeltaFromReality(10 * 60 * 1000);
        assertListenerStatus();
        // No notification in the main queue at this point (the PHASE event is the trigger for the next one)
        checkNotificationsNoRetry(0);

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

        // Invoice failed to generate
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false,  callContext).size(), 2);

        // Verify notification has moved to the retry service
        checkRetryNotifications("2012-06-01T00:05:00", 1);

        // Add 5'
        clock.addDeltaFromReality(5 * 60 * 1000);
        // Verify there are no notification duplicates
        checkRetryNotifications("2012-06-01T00:15:00", 1);

        // Fix invoice plugin
        testInvoicePluginApi.shouldThrowException = false;

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDeltaFromReality(10 * 60 * 1000);
        assertListenerStatus();
        checkNotificationsNoRetry(1);

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

        // Invoice failed to generate
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false,  callContext).size(), 3);

        // Verify notification has moved to the retry service
        checkRetryNotifications("2012-07-01T00:05:00", 1);

        testInvoicePluginApi.shouldThrowException = false;

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDeltaFromReality(5 * 60 * 1000);
        assertListenerStatus();
        checkNotificationsNoRetry(1);

        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 4);
    }

    private void checkRetryBusEvents(final int retryNb, final int expectedFutureInvoiceNotifications) throws NoSuchNotificationQueue {
        // Verify notification(s) moved to the retry queue
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
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
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
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

        @Override
        public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice invoice, final boolean isDryRun, final Iterable<PluginProperty> pluginProperties, final CallContext callContext) {
            if (shouldThrowException) {
                throw new InvoicePluginApiRetryException();
            } else if (additionalInvoiceItem != null) {
                return ImmutableList.<InvoiceItem>of(additionalInvoiceItem);
            } else {
                return ImmutableList.<InvoiceItem>of(createTaxInvoiceItem(invoice));
            }
        }

        private InvoiceItem createTaxInvoiceItem(final Invoice invoice) {
            return new TaxInvoiceItem(invoice.getId(), invoice.getAccountId(), null, "Tax Item", clock.getUTCNow().toLocalDate(), BigDecimal.ONE, invoice.getCurrency());
        }
    }
}
