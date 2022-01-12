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

package org.killbill.billing.beatrix.integration.usage;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoiceTrackingModelDao;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

import static org.testng.Assert.assertEquals;

public class TestWithoutZeroUsageItems extends TestIntegrationBase {


    @Inject
    protected InvoiceDao invoiceDao;

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.putAll(DEFAULT_BEATRIX_PROPERTIES);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testWithoutZeroUsageItems");
        return getConfigSource(null, allExtraProperties);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }
        super.beforeMethod();
        invoiceConfig.setZeroAmountUsageDisabled(true);
        invoiceConfig.setItemResultBehaviorMode(UsageDetailMode.DETAIL);

    }

    @Test(groups = "slow")
    public void testCapacityWithBlankPeriods() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Create subscription
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Trebuchet", ProductCategory.BASE, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);

        Assert.assertNull(bpSubscription.getSubscriptionBase().getChargedThroughDate());

        // Record usage for first month
        recordUsageData(bpSubscription.getId(), "xxx-1", "stones", new LocalDate(2012, 4, 5), 85L, callContext);
        recordUsageData(bpSubscription.getId(), "xxx-2", "stones", new LocalDate(2012, 4, 15), 150L, callContext);

        // 2012-5-1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("1000")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("xxx-1", "xxx-2"), internalCallContext);

        final DateTime firstExpectedCTD = account.getReferenceTime().withMonthOfYear(5).withDayOfMonth(1);
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(bpSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(firstExpectedCTD), 0);

        // Beause we don't have $0 usage item we see a null invoice
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();


        final DateTime secondExpectedCTD = account.getReferenceTime().withMonthOfYear(6).withDayOfMonth(1);
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(bpSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(secondExpectedCTD), 0);

        // Record usage for third month (verify invoicing resumes)
        recordUsageData(bpSubscription.getId(), "xxx-3", "stones", new LocalDate(2012, 6, 5), 25L, callContext);
        recordUsageData(bpSubscription.getId(), "xxx-4", "stones", new LocalDate(2012, 6, 15), 50L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("100")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("xxx-3", "xxx-4"), internalCallContext);

        final DateTime thirdExpectedCTD = account.getReferenceTime().withMonthOfYear(7).withDayOfMonth(1);
        assertEquals(subscriptionBaseInternalApiApi.getSubscriptionFromId(bpSubscription.getId(), internalCallContext).getChargedThroughDate().compareTo(thirdExpectedCTD), 0);
    }

    @Test(groups = "slow")
    public void testConsumableInArrearWithExtraZeroItems() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", new LocalDate(2012, 4, 1), 99L, callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2012, 4, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-1", "tracking-2"), internalCallContext);

        // No $0 invoice
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addMonths(1);
        assertListenerStatus();


        // Insert Manually $0 USAGE items for the period 2012-5-1 -> 2012-6-1 to check this does not cause any issues
        final DateTime catalogEffectiveDate = curInvoice.getInvoiceItems().get(0).getCatalogEffectiveDate();
        final InvoiceModelDao invoiceForPreviousBehavior = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2012, 5, 1), new LocalDate(2012, 5, 1), Currency.USD, false, InvoiceStatus.COMMITTED, false);

        invoiceForPreviousBehavior.addInvoiceItem(new InvoiceItemModelDao(UUID.randomUUID(), clock.getUTCNow(), InvoiceItemType.USAGE, invoiceForPreviousBehavior.getId(), account.getId(), null, aoSubscription.getBundleId(), aoSubscription.getId(), "",
                                                                          "Bullets", "bullets-usage-in-arrear", "bullets-usage-in-arrear-evergreen", "bullets-usage-in-arrear-usage", catalogEffectiveDate,
                                                                          new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1),
                                                                          BigDecimal.ZERO, BigDecimal.ZERO, Currency.USD, null, 0, "{\"tier\":1,\"tierUnit\":\"bullets\",\"tierPrice\":2.95,\"tierBlockSize\":100,\"quantity\":0,\"amount\":5.90}"));
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        insertInvoiceItems(invoiceForPreviousBehavior);
        assertListenerStatus();


        recordUsageData(aoSubscription.getId(), "tracking-3", "bullets", new LocalDate(2012, 6, 1), 50L, callContext);
        recordUsageData(aoSubscription.getId(), "tracking-4", "bullets", new LocalDate(2012, 6, 16), 300L, callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.USAGE, new BigDecimal("11.80")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-3", "tracking-4"), internalCallContext);

        // Should be ignored because this is outside of optimization range (org.killbill.invoice.readMaxRawUsagePreviousPeriod = 2) => we will only look for items > 2012-7-1 - 2 months = 2012-5-1
        recordUsageData(aoSubscription.getId(), "tracking-5", "bullets", new LocalDate(2012, 4, 30), 100L, callContext);

        // Should be invoiced from past period
        recordUsageData(aoSubscription.getId(), "tracking-6", "bullets", new LocalDate(2012, 5, 1), 199L, callContext);

        // New usage for this past period
        recordUsageData(aoSubscription.getId(), "tracking-7", "bullets", new LocalDate(2012, 7, 1), 50L, callContext);
        recordUsageData(aoSubscription.getId(), "tracking-8", "bullets", new LocalDate(2012, 7, 16), 300L, callContext);

        // Remove old data, should be ignored by the system because readMaxRawUsagePreviousPeriod = 2, so:
        // * Last endDate invoiced is 2012-7-1 => Anything 2 period prior that will be ignored => Anything prior 2012-5-1 should be ignored
        removeUsageData(aoSubscription.getId(), "bullets", new LocalDate(2012, 4, 15));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")),
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2012, 7, 1), new LocalDate(2012, 8, 1), InvoiceItemType.USAGE, new BigDecimal("11.80")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-6", "tracking-7", "tracking-8"), internalCallContext);

    }



    @Test(groups = "slow")
    public void testConsumableInArrearWithNonNullQuantity() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Pellets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        recordUsageData(aoSubscription.getId(), "tracking-1", "pellets", new LocalDate(2012, 4, 1), 99L, callContext);
        recordUsageData(aoSubscription.getId(), "tracking-2", "pellets", new LocalDate(2012, 4, 15), 100L, callContext);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        // Code will generate a $0 USAGE with a non null quantity
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("0.00")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-1", "tracking-2"), internalCallContext);


    }

        private void insertInvoiceItems(final InvoiceModelDao invoice) {
        final FutureAccountNotifications callbackDateTimePerSubscriptions = new FutureAccountNotifications();
        invoiceDao.createInvoice(invoice, null, ImmutableSet.<InvoiceTrackingModelDao>of(), callbackDateTimePerSubscriptions, null, internalCallContext);
    }

}
