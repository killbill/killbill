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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionEvent;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import static org.testng.Assert.assertNotNull;

public class TestCatalogWithEvents extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogWithEvents");
        return super.getConfigSource(null, allExtraProperties);
    }


    @Test(groups = "slow")
    public void testChangeWithUsagePlan() throws Exception {

        final LocalDate today = new LocalDate(2020, 1, 1);
        clock.setDay(today);

        final VersionedCatalog catalog = catalogUserApi.getCatalog("foo", callContext);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("water-monthly", null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID subscriptionId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, null), UUID.randomUUID().toString(), clock.getUTCToday(), clock.getUTCToday(), false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        recordUsageData(subscriptionId, "t1", "liter", new LocalDate(2020, 1, 1), 10L, callContext);
        recordUsageData(subscriptionId, "t2", "liter", new LocalDate(2020, 1, 23), 10L, callContext);

        // 2020-2-1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        final Invoice invoice1 = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                             new ExpectedInvoiceItemCheck(new LocalDate(2020, 1, 1), new LocalDate(2020, 2, 1), InvoiceItemType.USAGE, new BigDecimal("30.00")));
        invoiceChecker.checkTrackingIds(invoice1, ImmutableSet.of("t1", "t2"), internalCallContext);


        Assert.assertTrue(invoice1.getInvoiceItems().get(0).getCatalogEffectiveDate().toDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()) == 0);

        final Subscription subscription1 = subscriptionApi.getSubscriptionForEntitlementId(subscriptionId, callContext);
        final List<SubscriptionEvent> events1 = subscription1.getSubscriptionEvents();
        Assert.assertEquals(events1.size(), 2);
        Assert.assertTrue(events1.get(0).getNextPlan().getCatalog().getEffectiveDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()) == 0);

        // 2020-2-16 (V2 effDt = 2020-2-15)
        clock.addDays(15);

        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        subscription1.changePlanWithDate(new DefaultEntitlementSpecifier(spec),  clock.getUTCToday(), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        final Subscription subscription2 = subscriptionApi.getSubscriptionForEntitlementId(subscriptionId, callContext);
        final List<SubscriptionEvent> events2 = subscription2.getSubscriptionEvents();
        Assert.assertEquals(events2.size(), 3);
        Assert.assertTrue(events2.get(0).getNextPlan().getCatalog().getEffectiveDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()) == 0);


        // 2020-03-01
        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        clock.addDays(14);
        assertListenerStatus();

        // 2020-3-16 (V3 effDt = 2020-3-15)
        clock.addDays(15);

        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE);
        subscription1.changePlanWithDate(new DefaultEntitlementSpecifier(spec),  clock.getUTCToday(), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();



        final Subscription subscription3 = subscriptionApi.getSubscriptionForEntitlementId(subscriptionId, callContext);
        final List<SubscriptionEvent> events3 = subscription3.getSubscriptionEvents();
        Assert.assertEquals(events3.size(), 4);
        Assert.assertTrue(events3.get(0).getNextPlan().getCatalog().getEffectiveDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()) == 0);
        Assert.assertTrue(events3.get(1).getNextPlan().getCatalog().getEffectiveDate().compareTo(catalog.getVersions().get(0).getEffectiveDate()) == 0);
        // Change catalog V2
        Assert.assertTrue(events3.get(2).getNextPlan().getCatalog().getEffectiveDate().compareTo(catalog.getVersions().get(1).getEffectiveDate()) == 0);
        // Change catalog V3
        Assert.assertTrue(events3.get(3).getNextPlan().getCatalog().getEffectiveDate().compareTo(catalog.getVersions().get(2).getEffectiveDate()) == 0);

    }
}