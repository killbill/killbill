/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.osgi.api.OSGIServiceDescriptor;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.UsageApiException;
import org.killbill.billing.usage.api.svcs.DefaultRawUsage;
import org.killbill.billing.usage.plugin.api.UsagePluginApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

import static org.testng.Assert.assertEquals;

public class TestWithUsagePlugin extends TestIntegrationBase {

    @Inject
    private OSGIServiceRegistration<UsagePluginApi> pluginRegistry;

    private TestUsagePluginApi testUsagePluginApi;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeClass();

        this.testUsagePluginApi = new TestUsagePluginApi();
        pluginRegistry.registerService(new OSGIServiceDescriptor() {
            @Override
            public String getPluginSymbolicName() {
                return "TestUsagePluginApi";
            }

            @Override
            public String getPluginName() {
                return "TestUsagePluginApi";
            }

            @Override
            public String getRegistrationName() {
                return "TestUsagePluginApi";
            }
        }, testUsagePluginApi);
    }

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        if (hasFailed()) {
            return;
        }
        testUsagePluginApi.clearUsageData();
    }

    @Test(groups = "slow")
    public void testSimple() throws Exception {

        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.ANNUAL, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));

        //
        // ADD ADD_ON ON THE SAME DAY
        //
        final DefaultEntitlement aoSubscription = addAOEntitlementAndCheckForCompletion(bpSubscription.getBundleId(), "Bullets", ProductCategory.ADD_ON, BillingPeriod.NO_BILLING_PERIOD, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);

        testUsagePluginApi.recordUsageData(aoSubscription.getId(), "tracking-1", "bullets", new LocalDate(2012, 4, 1), 99L, callContext);
        testUsagePluginApi.recordUsageData(aoSubscription.getId(), "tracking-2", "bullets", new LocalDate(2012, 4, 15), 100L, callContext);

        // Wrong subscription - should be ignored...
        testUsagePluginApi.recordUsageData(UUID.randomUUID(), "tracking-3", "bullets", new LocalDate(2012, 4, 5), 100L, callContext);

        // Wrong unit - should be ignored...
        testUsagePluginApi.recordUsageData(aoSubscription.getId(), "tracking-3", "bullets2", new LocalDate(2012, 4, 6), 200L, callContext);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2013, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("2399.95")),
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), InvoiceItemType.USAGE, new BigDecimal("5.90")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("tracking-1", "tracking-2"), internalCallContext);

        final RolledUpUsage result1 = usageUserApi.getUsageForSubscription(aoSubscription.getId(), "bullets", new LocalDate(2012, 4, 1), new LocalDate(2012, 4, 15), callContext);
        assertEquals(result1.getSubscriptionId(), aoSubscription.getId());
        assertEquals(result1.getRolledUpUnits().size(), 1);
        assertEquals(result1.getRolledUpUnits().get(0).getUnitType(), "bullets");
        assertEquals(result1.getRolledUpUnits().get(0).getAmount().longValue(), 99L);

        final List<LocalDate> transitionDates = new ArrayList<>();
        transitionDates.add(new LocalDate(2012, 4, 1));
        transitionDates.add(new LocalDate(2012, 4, 5));
        transitionDates.add(new LocalDate(2012, 4, 6));
        transitionDates.add(new LocalDate(2012, 4, 15));
        transitionDates.add(new LocalDate(2012, 4, 17));

        final List<RolledUpUsage> result2 = usageUserApi.getAllUsageForSubscription(aoSubscription.getId(), transitionDates, callContext);
        assertEquals(result2.size(), 4);

        assertEquals(result2.get(0).getSubscriptionId(), aoSubscription.getId());
        assertEquals(result2.get(0).getStart(), new LocalDate(2012, 4, 1));
        assertEquals(result2.get(0).getEnd(), new LocalDate(2012, 4, 5));
        assertEquals(result2.get(0).getRolledUpUnits().size(), 1);
        assertEquals(result2.get(0).getRolledUpUnits().get(0).getUnitType(), "bullets");
        assertEquals(result2.get(0).getRolledUpUnits().get(0).getAmount().longValue(), 99L);

        // Usage was for wrong subscriptionId
        assertEquals(result2.get(1).getSubscriptionId(), aoSubscription.getId());
        assertEquals(result2.get(1).getStart(), new LocalDate(2012, 4, 5));
        assertEquals(result2.get(1).getEnd(), new LocalDate(2012, 4, 6));
        assertEquals(result2.get(1).getRolledUpUnits().size(), 0);

        // We see the wrong bullet2 unit
        assertEquals(result2.get(2).getSubscriptionId(), aoSubscription.getId());
        assertEquals(result2.get(2).getStart(), new LocalDate(2012, 4, 6));
        assertEquals(result2.get(2).getEnd(), new LocalDate(2012, 4, 15));
        assertEquals(result2.get(2).getRolledUpUnits().size(), 1);
        assertEquals(result2.get(2).getRolledUpUnits().get(0).getUnitType(), "bullets2");
        assertEquals(result2.get(2).getRolledUpUnits().get(0).getAmount().longValue(), 200L);

        assertEquals(result2.get(3).getSubscriptionId(), aoSubscription.getId());
        assertEquals(result2.get(3).getStart(), new LocalDate(2012, 4, 15));
        assertEquals(result2.get(3).getEnd(), new LocalDate(2012, 4, 17));
        assertEquals(result2.get(3).getRolledUpUnits().size(), 1);
        assertEquals(result2.get(3).getRolledUpUnits().get(0).getUnitType(), "bullets");
        assertEquals(result2.get(3).getRolledUpUnits().get(0).getAmount().longValue(), 100L);

    }

    public static class TestUsagePluginApi implements UsagePluginApi {

        private final SortedMap<LocalDate, List<RawUsageRecord>> usageData;

        public TestUsagePluginApi() {
            this.usageData = new TreeMap<>();
        }

        @Override
        public List<RawUsageRecord> getUsageForAccount(final LocalDate startDate, final LocalDate endDate, final TenantContext tenantContext) {

            final List<RawUsageRecord> result = new LinkedList<>();
            for (final LocalDate curDate : usageData.keySet()) {
                if (curDate.compareTo(startDate) >= 0 && curDate.compareTo(endDate) < 0) {
                    final List<RawUsageRecord> rawUsageRecords = usageData.get(curDate);
                    if (rawUsageRecords != null && !rawUsageRecords.isEmpty()) {
                        result.addAll(rawUsageRecords);
                    }
                }
            }
            return result;
        }

        @Override
        public List<RawUsageRecord> getUsageForSubscription(final UUID subscriptionId, final LocalDate startDate, final LocalDate endDate, final TenantContext tenantContext) {

            final List<RawUsageRecord> result = new ArrayList<>();
            final List<RawUsageRecord> usageForAccount = getUsageForAccount(startDate, endDate, tenantContext);
            for (final RawUsageRecord cur : usageForAccount) {
                if (cur.getSubscriptionId().equals(subscriptionId)) {
                    result.add(cur);
                }
            }
            return result;
        }

        public void recordUsageData(final UUID subscriptionId, final String trackingId, final String unitType, final LocalDate startDate, final Long amount, final CallContext context) throws UsageApiException {

            List<RawUsageRecord> record = usageData.get(startDate);
            if (record == null) {
                record = new LinkedList<>();
                usageData.put(startDate, record);
            }
            record.add(new DefaultRawUsage(subscriptionId, startDate, unitType, amount, trackingId));
        }

        public void clearUsageData() {
            usageData.clear();
        }
    }

}
