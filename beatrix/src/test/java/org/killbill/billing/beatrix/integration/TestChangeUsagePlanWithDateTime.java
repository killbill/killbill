/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

public class TestChangeUsagePlanWithDateTime extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testChangeUsagePlanWithDateTime");
        return super.getConfigSource(null, allExtraProperties);
    }

    
    @Test(groups = "slow")
    public void testChangePlanOnOnNextDayAndRecordUsage() throws Exception {

        final LocalDate today = new LocalDate(2018, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("server-monthly-standard");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //record usage for original plan
        recordUsageData(entitlementId, "t1", "server-hourly-type-1", clock.getUTCNow(), BigDecimal.valueOf(10L), callContext);
        
        // Move clock by 1 day and change plan
        clock.addDays(1); 
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        final PlanPhaseSpecifier newSpec = new PlanPhaseSpecifier("server-monthly-premium");
        DefaultEntitlementSpecifier defaultEntitlementSpecifier = new DefaultEntitlementSpecifier(newSpec);
        //Since plan change is done the next day, it results in an invoice generation
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlement.changePlanWithDate(defaultEntitlementSpecifier, clock.getUTCToday(), Collections.emptyList(), callContext);
        assertListenerStatus();
        
        //invoice generated for t1
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
        		new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 1, 2), InvoiceItemType.USAGE, new BigDecimal("10.00")));
        		invoiceChecker.checkTrackingIds(curInvoice, Set.of("t1"), internalCallContext);          
        
        //record usage for new plan
        recordUsageData(entitlementId, "t2", "server-hourly-type-1", clock.getUTCNow(), BigDecimal.valueOf(20L), callContext);
        
        //move clock by 1 month, results in invoice generation
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // invoice generated for t2
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                       new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 2), new LocalDate(2018, 2, 1), InvoiceItemType.USAGE, new BigDecimal("40.00")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t2"), internalCallContext);        
    }    
    
    @Test(groups = "slow")
    public void testChangePlanOnSameDayAndRecordUsage() throws Exception {

        final LocalDate today = new LocalDate(2018, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier specStandard = new PlanPhaseSpecifier("server-monthly-standard");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(specStandard), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Record usage for original plan
        recordUsageData(entitlementId, "t1", "server-hourly-type-1", clock.getUTCNow(), BigDecimal.valueOf(10L), callContext);

        // Move clock by few hours and change plan to premium
        final DateTime changeTime1 = clock.getUTCNow().plusHours(2);
        clock.setTime(changeTime1);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        final PlanPhaseSpecifier specPremium = new PlanPhaseSpecifier("server-monthly-premium");
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(specPremium), changeTime1, Collections.emptyList(), callContext);
        assertListenerStatus();


        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 1, 1), InvoiceItemType.USAGE, new BigDecimal("10.00")));

        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t1"), internalCallContext);

        // Record usage for new plan premium
        recordUsageData(entitlementId, "t2", "server-hourly-type-1", clock.getUTCNow(), BigDecimal.valueOf(20L), callContext);

        // Move clock a few days ahead and change plan back to standard
        // 2018-1-11
        final DateTime changeTime2 = clock.getUTCNow().plusDays(10);
        clock.setTime(changeTime2);

        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(specStandard), changeTime2, Collections.emptyList(), callContext);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 1, 11), InvoiceItemType.USAGE, new BigDecimal("40.00")));

        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t2"), internalCallContext);


        // Record usage for plan standard
        recordUsageData(entitlementId, "t3", "server-hourly-type-1", clock.getUTCNow(), BigDecimal.valueOf(10L), callContext);

        // Move clock by few hours and change plan back to premium
        final DateTime changeTime3 = clock.getUTCNow().plusHours(2);
        clock.setTime(changeTime3);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlement.changePlanWithDate(new DefaultEntitlementSpecifier(specPremium), changeTime3, Collections.emptyList(), callContext);
        assertListenerStatus();


        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 11), new LocalDate(2018, 1, 11), InvoiceItemType.USAGE, new BigDecimal("10.00")));

        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t3"), internalCallContext);

        // Record usage for new plan premium
        recordUsageData(entitlementId, "t4", "server-hourly-type-1", clock.getUTCNow(), BigDecimal.valueOf(20L), callContext);


        // Move clock to next BCD
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(21);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 11), new LocalDate(2018, 2, 1), InvoiceItemType.USAGE, new BigDecimal("40.00")));

        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t4"), internalCallContext);

        checkNoMoreInvoiceToGenerate(account.getId());

    }      

    @Test(groups = "slow")
    public void testChangePlanOnSameDayAndRecordUsageForDifferentUnit() throws Exception {

        final LocalDate today = new LocalDate(2018, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("server-monthly-standard");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //record usage for original plan
        recordUsageData(entitlementId, "t1", "server-hourly-type-1", clock.getUTCNow(), BigDecimal.valueOf(10L), callContext);
        
        // Move clock by few hours and change plan
        final DateTime changeTime = clock.getUTCNow().plusHours(2);
        clock.setTime(changeTime);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        final PlanPhaseSpecifier newSpec = new PlanPhaseSpecifier("server-monthly-standard-bandwidth");
        DefaultEntitlementSpecifier defaultEntitlementSpecifier = new DefaultEntitlementSpecifier(newSpec);
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlement.changePlanWithDate(defaultEntitlementSpecifier, changeTime, Collections.emptyList(), callContext);
        assertListenerStatus();

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 1, 1), InvoiceItemType.USAGE, new BigDecimal("10.00")));

        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t1"), internalCallContext);

        // Record usage for new plan
        recordUsageData(entitlementId, "t2", "bandwidth-type-1", clock.getUTCNow(), BigDecimal.valueOf(20L), callContext);
        
        // Move clock by 1 month, results in invoice generation
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                     new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 2, 1), InvoiceItemType.USAGE, new BigDecimal("100.00")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t2"), internalCallContext);

        checkNoMoreInvoiceToGenerate(account.getId());

    }      

}
