/*
 * Copyright 2020 The Billing Project, LLC
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
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import static org.testng.Assert.assertNotNull;

public class TestCatalogWithEffectiveDateForExistingSubscriptions2 extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogWithEffectiveDateForExistingSubscriptions2");
        return super.getConfigSource(null, allExtraProperties);
    }



    // Similar test than TestCatalogWithEffectiveDateForExistingSubscriptions#testUsagePlan but with a slightly
    // different catalog to address issue #1329
    @Test(groups = "slow", description = "See https://github.com/killbill/killbill/issues/1329")
    public void testUsagePlan() throws Exception {

        final LocalDate today = new LocalDate(2018, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("water-monthly");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        recordUsageData(entitlementId, "t1", "liter", new LocalDate(2018, 1, 1), 10L, callContext);
        recordUsageData(entitlementId, "t2", "liter", new LocalDate(2018, 1, 23), 10L, callContext);

        // Catalog v2 with price increase is on 2018-04-01 but because we have an effectiveDateForExistingSubscriptions set to 2018-05-01
        // we don't see any change until 5-1
        //
        // 2018-2-1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 2, 1), InvoiceItemType.USAGE, new BigDecimal("30.00")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("t1", "t2"), internalCallContext);


        recordUsageData(entitlementId, "t3", "liter", new LocalDate(2018, 2, 15), 20L, callContext);
        // 2018-3-1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2018, 2, 1), new LocalDate(2018, 3, 1), InvoiceItemType.USAGE, new BigDecimal("30.00")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("t3"), internalCallContext);


        recordUsageData(entitlementId, "t4", "liter", new LocalDate(2018, 3, 18), 20L, callContext);
        // 2018-4-1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2018, 3, 1), new LocalDate(2018, 4, 1), InvoiceItemType.USAGE, new BigDecimal("30.00")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("t4"), internalCallContext);


        recordUsageData(entitlementId, "t5", "liter", new LocalDate(2018, 4, 28), 20L, callContext);

        // 2018-5-1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2018, 4, 1), new LocalDate(2018, 5, 1), InvoiceItemType.USAGE, new BigDecimal("30.00")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("t5"), internalCallContext);


        recordUsageData(entitlementId, "t6", "liter", new LocalDate(2018, 5, 22), 20L, callContext);

        // 2018-6-1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 5, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2018, 5, 1), new LocalDate(2018, 6, 1), InvoiceItemType.USAGE, new BigDecimal("30.00")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("t6"), internalCallContext);



        recordUsageData(entitlementId, "t7", "liter", new LocalDate(2018, 6, 29), 20L, callContext);

        // 2018-7-1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 6, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2018, 6, 1), new LocalDate(2018, 7, 1), InvoiceItemType.USAGE, new BigDecimal("30.00")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("t7"), internalCallContext);



        recordUsageData(entitlementId, "t8", "liter", new LocalDate(2018, 7, 13), 20L, callContext);

        // Catalog v3 with price increase is on 2018-07-01 but because we have an effectiveDateForExistingSubscriptions set to 2018-08-01
        // we don't see any change until 8-1
        // 2018-8-1
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 7, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2018, 7, 1), new LocalDate(2018, 8, 1), InvoiceItemType.USAGE, new BigDecimal("30.00")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("t8"), internalCallContext);


        recordUsageData(entitlementId, "t9", "liter", new LocalDate(2018, 8, 13), 20L, callContext);

        // Check we see the new price for catalog version v3
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 8, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2018, 8, 1), new LocalDate(2018, 9, 1), InvoiceItemType.USAGE, new BigDecimal("70.00")));
        invoiceChecker.checkTrackingIds(curInvoice, ImmutableSet.of("t9"), internalCallContext);

    }

}
