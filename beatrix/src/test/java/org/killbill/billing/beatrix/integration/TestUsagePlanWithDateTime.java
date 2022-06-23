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

public class TestUsagePlanWithDateTime extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testChangeUsagePlanWithDateTime");
        return super.getConfigSource(null, allExtraProperties);
    }

    
    @Test(groups = "slow") 
    public void testChangePlanOnOnNextDayAndRecordUsage() throws Exception { //works as expected

        final LocalDate today = new LocalDate(2018, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("server-monthly-standard");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //record usage for original plan
        recordUsageData(entitlementId, "t1", "server-hourly-type-1", new DateTime(2018, 1, 1, 10, 30), BigDecimal.valueOf(10L), callContext);
        
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
        recordUsageData(entitlementId, "t2", "server-hourly-type-1", new DateTime(2018, 1, 2, 11, 45), BigDecimal.valueOf(20L), callContext);
        
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
    public void testChangePlanOnSameDayAndRecordUsage() throws Exception { //does not work as expected

        final LocalDate today = new LocalDate(2018, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("server-monthly-standard");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //record usage for original plan
        recordUsageData(entitlementId, "t1", "server-hourly-type-1", new DateTime(2018, 1, 1, 8, 30), BigDecimal.valueOf(10L), callContext);
        
        //Move clock by few hours and change plan
        final DateTime changeTime = clock.getUTCNow().plusHours(2);
        clock.setTime(changeTime);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        final PlanPhaseSpecifier newSpec = new PlanPhaseSpecifier("server-monthly-premium");
        DefaultEntitlementSpecifier defaultEntitlementSpecifier = new DefaultEntitlementSpecifier(newSpec);
        //No invoice generated since plan change is on the same day
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.NULL_INVOICE);
        entitlement.changePlanWithDate(defaultEntitlementSpecifier, changeTime, Collections.emptyList(), callContext);
        assertListenerStatus();
        
        //record usage for new plan
        recordUsageData(entitlementId, "t2", "server-hourly-type-1", new DateTime(2018, 1, 1, 11, 45), BigDecimal.valueOf(20L), callContext);
        
        //move clock by 1 month, results in invoice generation
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        //Both usage records are billed as per the second plan as against the expected behavior where t1 should be billed as per the original plan and t2 should be billed as per the new plan
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                     new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 2, 1), InvoiceItemType.USAGE, new BigDecimal("60.00")));

        // Expected behavior
//        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
//                new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 2, 1), InvoiceItemType.USAGE, new BigDecimal("50.00")));
        
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t1","t2"), internalCallContext);
        
        
        
        
    }      

    @Test(groups = "slow") 
    public void testChangePlanOnSameDayAndRecordUsageForDifferentUnit() throws Exception { //this is the scenario that Stephane mentioned, this does not work as expected

        final LocalDate today = new LocalDate(2018, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("server-monthly-standard");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //record usage for original plan
        recordUsageData(entitlementId, "t1", "server-hourly-type-1", new LocalDate(2018, 1, 1), BigDecimal.valueOf(10L), callContext);
        
        //Move clock by few hours and change plan
        final DateTime changeTime = clock.getUTCNow().plusHours(2);
        clock.setTime(changeTime);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        final PlanPhaseSpecifier newSpec = new PlanPhaseSpecifier("server-monthly-standard-bandwidth");
        DefaultEntitlementSpecifier defaultEntitlementSpecifier = new DefaultEntitlementSpecifier(newSpec);
        //No invoice generated since plan change is on the same day
        busHandler.pushExpectedEvents(NextEvent.CHANGE, NextEvent.NULL_INVOICE); 
        entitlement.changePlanWithDate(defaultEntitlementSpecifier, changeTime, Collections.emptyList(), callContext);
        assertListenerStatus();
        
        //record usage for new plan
        recordUsageData(entitlementId, "t2", "bandwidth-type-1", new LocalDate(2018, 1, 1), BigDecimal.valueOf(20L), callContext);
        
        //move clock by 1 month, results in invoice generation
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Only t2 usage record is billed, t1 usage record is not billed
        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                     new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 2, 1), InvoiceItemType.USAGE, new BigDecimal("100.00")));
        invoiceChecker.checkTrackingIds(curInvoice, Set.of("t2"), internalCallContext);        

        //Expected behavior
//        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
//                new ExpectedInvoiceItemCheck(new LocalDate(2018, 1, 1), new LocalDate(2018, 2, 1), InvoiceItemType.USAGE, new BigDecimal("110.00")));
        
      
    }      
    	

}
