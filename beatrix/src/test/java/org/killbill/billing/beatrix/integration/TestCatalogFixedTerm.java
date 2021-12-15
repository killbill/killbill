/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

public class TestCatalogFixedTerm extends TestIntegrationBase {

    private CallContext testCallContext;
    private Account account;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Setup tenant
        clock.setTime(new DateTime("2021-12-01T12:56:02"));
        testCallContext = setupTenant();

        // Setup account in right tenant
        account = setupAccount(testCallContext);
        
        //upload catalog
        uploadCatalog("WeaponsHireSmall.xml");
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1533")
    public void testFixedTermPhaseInAdvanceBilling() throws Exception {

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
                                      NextEvent.PAYMENT);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-biennial-inadvance", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), testCallContext);
        assertNotNull(entitlementId);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 12, 01), new LocalDate(2023, 12, 01), InvoiceItemType.RECURRING, new BigDecimal("40.00")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT); 
        clock.addYears(2);//2023-12-01
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 12, 01), new LocalDate(2024, 12, 01), InvoiceItemType.RECURRING, new BigDecimal("20.03")));

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addYears(1);//2024-12-01
        assertListenerStatus();
        
        checkNoMoreInvoiceToGenerate(account.getId(), testCallContext);

    }
    
    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1533")
    public void testFixedTermPhaseInArrearBilling() throws Exception {

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-biennial-inarrear", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), testCallContext);
        assertNotNull(entitlementId);
        assertListenerStatus();
        
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT); 
        clock.addYears(2);//2023-12-01
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 12, 01), new LocalDate(2023, 12, 01), InvoiceItemType.RECURRING, new BigDecimal("40.00")));
        
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT); 
        clock.addYears(2);//2025-12-01
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 12, 01), new LocalDate(2024, 12, 01), InvoiceItemType.RECURRING, new BigDecimal("20.03")));
        
        checkNoMoreInvoiceToGenerate(account.getId(), testCallContext);

    }
    
    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1533")
    public void testDiscountAndRecurringPhaseInAdvanceBilling() throws Exception {

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT,
                                      NextEvent.PAYMENT);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-biennial-inadvance-discountandrecurring", null);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec, null, UUID.randomUUID().toString(), null), "something", null, null, false, true, ImmutableList.<PluginProperty>of(), testCallContext);
        assertNotNull(entitlementId);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 1, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2021, 12, 01), new LocalDate(2023, 12, 01), InvoiceItemType.RECURRING, new BigDecimal("40.00")));

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT); 
        clock.addYears(2); //2023-12-01
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2023, 12, 01), new LocalDate(2024, 12, 01), InvoiceItemType.RECURRING, new BigDecimal("20.03")));

        busHandler.pushExpectedEvents(NextEvent.PHASE,NextEvent.INVOICE, NextEvent.NULL_INVOICE,NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT); 
        clock.addYears(1); //2024-12-01
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 3, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2024, 12, 01), new LocalDate(2026, 12, 01), InvoiceItemType.RECURRING, new BigDecimal("100.00")));
        
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addYears(2); //2026-12-01
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 4, testCallContext, new ExpectedInvoiceItemCheck(new LocalDate(2026, 12, 01), new LocalDate(2028, 12, 01), InvoiceItemType.RECURRING, new BigDecimal("100.00")));

    }
    

    private void uploadCatalog(final String name) throws CatalogApiException, IOException {
        catalogUserApi.uploadCatalog(Resources
                                             .asCharSource(Resources.getResource("catalogs/testCatalogFixedTerm/" + name), Charsets.UTF_8)
                                             .read(), testCallContext);
    }

}
