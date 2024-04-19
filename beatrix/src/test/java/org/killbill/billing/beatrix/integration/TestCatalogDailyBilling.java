/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestCatalogDailyBilling extends TestIntegrationBase  {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testCatalogDailyBilling");
        return super.getConfigSource(null, allExtraProperties);
    }


    @Test(description="https://github.com/killbill/killbill/issues/2005")
    public void testDailyBillingNYTimezone() throws Exception {
        final DateTime initialDateTime = new DateTime("2024-04-12T08:22:28");
        clock.setTime(initialDateTime);

        final DateTimeZone timezone = DateTimeZone.forID("America/New_York");
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0, timezone, initialDateTime));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("plan-b");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Invoice corresponding to TRIAL phase
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 1);

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 12), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        // Move clock to 4/13 - Invoice generated
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setTime(new DateTime("2024-04-13T08:22:28"));
        assertListenerStatus();

        // Total 2 invoices
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);

        //Invoice with startDate=2024-04-13 and endDate=2024-04-14
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 13), new LocalDate(2024, 4, 14), InvoiceItemType.RECURRING, BigDecimal.ONE));

        //Trigger invoice run with target date = 2024-04-13 - No invoice generated
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        try {
            final LocalDate targetDate = new LocalDate("2024-04-13");
            invoiceUserApi.triggerInvoiceGeneration(account.getId(), targetDate, Collections.emptyList(), callContext);
        }
        catch(InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }
        assertListenerStatus();

        // Move clock to 4/14 - Invoice generated
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setTime(new DateTime("2024-04-14T08:22:28"));
        assertListenerStatus();

        // Total 3 invoices
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);

        //Invoice with startDate=2024-04-14 and endDate=2024-04-15
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 14), new LocalDate(2024, 4, 15), InvoiceItemType.RECURRING, BigDecimal.ONE));
    }

    @Test(description="https://github.com/killbill/killbill/issues/2005")
    public void testDailyBillingUTCTimezone() throws Exception {
        final DateTime initialDateTime = new DateTime("2024-04-12T08:22:28");
        clock.setTime(initialDateTime);

        final DateTimeZone timezone = DateTimeZone.UTC;
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0, timezone, initialDateTime));

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("plan-b");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID createdEntitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), account.getExternalKey(), null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        // Invoice corresponding to TRIAL phase
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 1);

        Invoice curInvoice = invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                                         new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 12), null, InvoiceItemType.FIXED, BigDecimal.ZERO));

        // Move clock to 4/13 - Invoice generated
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setTime(new DateTime("2024-04-13T08:22:28"));
        assertListenerStatus();

        // Total 2 invoices
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);


        //Invoice with startDate=2024-04-13 and endDate=2024-04-14
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 13), new LocalDate(2024, 4, 14), InvoiceItemType.RECURRING, BigDecimal.ONE));

        //Trigger invoice run with target date = 2024-04-13 - No invoice generated
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        try {
            final LocalDate targetDate = new LocalDate("2024-04-13");
            invoiceUserApi.triggerInvoiceGeneration(account.getId(), targetDate, Collections.emptyList(), callContext);
        }
        catch(InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }
        assertListenerStatus();

        // Move clock to 4/14 - Invoice generated
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setTime(new DateTime("2024-04-14T08:22:28"));
        assertListenerStatus();

        // Total 3 invoices
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);


        //Invoice with startDate=2024-04-14 and endDate=2024-04-15
        curInvoice = invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                                 new ExpectedInvoiceItemCheck(new LocalDate(2024, 4, 14), new LocalDate(2024, 4, 15), InvoiceItemType.RECURRING, BigDecimal.ONE));
    }

}
