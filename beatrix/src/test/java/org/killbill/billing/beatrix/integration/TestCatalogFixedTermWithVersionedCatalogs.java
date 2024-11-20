/*******************************************************************************
 *   Copyright 2020-2021 Equinix, Inc
 *   Copyright 2014-2021 The Billing Project, LLC
 *  
 *   The Billing Project licenses this file to you under the Apache License, version 2.0
 *   (the "License"); you may not use this file except in compliance with the
 *   License.  You may obtain a copy of the License at:
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *   License for the specific language governing permissions and limitations
 *   under the License.
 *******************************************************************************/
package org.killbill.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.callcontext.CallContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestCatalogFixedTermWithVersionedCatalogs extends TestIntegrationBase{
	
	private CallContext testCallContext;
    private Account account;
    
    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogFixedTerm/versionedCatalogs");
        return super.getConfigSource(null, allExtraProperties);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Setup tenant
        clock.setTime(new DateTime("2021-01-01T12:56:02"));
        testCallContext = setupTenant();

        // Setup account in right tenant
        account = setupAccount(testCallContext);
        
    }


    @Test(groups = "slow")
    public void testVersionedCatalogsEffectiveDateBetweenFixedTerm() throws Exception {

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
                                      NextEvent.PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("plumber-insurance-monthly", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), testCallContext);
        assertNotNull(entitlementId);
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.ACTIVE);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 01, 01), new LocalDate(2021, 02, 01), InvoiceItemType.RECURRING, new BigDecimal("40.00")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); //2021-02-01
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.ACTIVE);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 02, 01), new LocalDate(2021, 02, 15), InvoiceItemType.RECURRING, new BigDecimal("20.00")));

        //price change takes effect
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(15); //2021-02-15
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.ACTIVE);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 02, 15), new LocalDate(2021, 03, 01), InvoiceItemType.RECURRING, new BigDecimal("30.00")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(15); //2021-03-01
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.ACTIVE);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 4, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 03, 01), new LocalDate(2021, 04, 01), InvoiceItemType.RECURRING, new BigDecimal("60.00")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); //2021-04-01
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.ACTIVE);
        assertListenerStatus();
        //Fixed term duration of 100 days ends on 2021-04-11
        invoiceChecker.checkInvoice(account.getId(), 5, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 04, 01), new LocalDate(2021, 04, 11), InvoiceItemType.RECURRING, new BigDecimal("20.00")));

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE, NextEvent.EXPIRED);
        clock.addMonths(1);//2021-05-01
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.EXPIRED);
        assertListenerStatus();

        checkNoMoreInvoiceToGenerate(account.getId(), testCallContext);

    }    
    
    @Test(groups = "slow")
    public void testVersionedCatalogsEffectiveDateAfterEndOfFixedTerm() throws Exception {

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
                NextEvent.PAYMENT);        
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("plumber-insurance-monthly2", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, Collections.emptyList(), testCallContext);
        assertNotNull(entitlementId);
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.ACTIVE);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 01, 01), new LocalDate(2021, 02, 01), InvoiceItemType.RECURRING, new BigDecimal("40.00")));
        
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); //2021-02-01
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.ACTIVE);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 02, 01), new LocalDate(2021, 03, 01), InvoiceItemType.RECURRING, new BigDecimal("40.00")));
        
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); //2021-03-01
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.ACTIVE);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 03, 01), new LocalDate(2021, 04, 01), InvoiceItemType.RECURRING, new BigDecimal("40.00")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1); //2021-04-01
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.ACTIVE);
        assertListenerStatus();
        //Fixed term duration of 100 days ends on 2021-04-11
        invoiceChecker.checkInvoice(account.getId(), 4, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 04, 01), new LocalDate(2021, 04, 11), InvoiceItemType.RECURRING, new BigDecimal("13.33")));

        busHandler.pushExpectedEvents(NextEvent.EXPIRED, NextEvent.NULL_INVOICE);
        clock.addDays(10);
        assertListenerStatus();
        assertEquals(entitlementApi.getEntitlementForId(entitlementId, false, testCallContext).getState(), EntitlementState.EXPIRED);

        checkNoMoreInvoiceToGenerate(account.getId(), testCallContext);

    }        

}
