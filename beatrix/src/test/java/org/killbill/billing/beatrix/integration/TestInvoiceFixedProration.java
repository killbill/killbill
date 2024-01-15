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

import static org.testng.Assert.assertEquals;

public class TestInvoiceFixedProration extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.invoice.proration.fixed.days", "30");
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/default");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow")
    public void testInvoiceLeadingProration1() throws Exception {
        final LocalDate initialDate = new LocalDate(2023, 5, 15);
        clock.setDay(initialDate);

        //Set BCD=23
        final Account account1 = createAccountWithNonOsgiPaymentMethod(getAccountData(23));
        //Create subscription on 2023-05-15
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.createBaseEntitlement(account1.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"), null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Prorated invoice generated for 2023-05-15 to 2023-05-23 for 5.32
        final List<Invoice> invoices1 = invoiceUserApi.getInvoicesByAccount(account1.getId(), false, false, true, callContext);
        assertEquals(invoices1.size(), 1);
        final List<ExpectedInvoiceItemCheck> toBeChecked1 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 15), new LocalDate(2023, 5, 23), InvoiceItemType.RECURRING, new BigDecimal("5.32")));
        invoiceChecker.checkInvoice(invoices1.get(0).getId(), callContext, toBeChecked1);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1); //2023-06-15
        assertListenerStatus();

        //Set BCD=23
        final Account account2 = createAccountWithNonOsgiPaymentMethod(getAccountData(23));
        //Create subscription on 2023-06-15
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.createBaseEntitlement(account2.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"), null, null, null, null), "externalKey2", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Prorated invoice generated for 2023-06-15 to 2023-06-23 for 5.32
        final List<Invoice> invoices2 = invoiceUserApi.getInvoicesByAccount(account2.getId(), false, false, true, callContext);
        assertEquals(invoices2.size(), 1);
        final List<ExpectedInvoiceItemCheck> toBeChecked2 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 15), new LocalDate(2023, 6, 23), InvoiceItemType.RECURRING, new BigDecimal("5.32")));

        invoiceChecker.checkInvoice(invoices2.get(0).getId(), callContext, toBeChecked2);
    }

    @Test(groups = "slow")
    public void testInvoiceLeadingProration2() throws Exception {
        final LocalDate initialDate = new LocalDate(2023, 5, 23);
        clock.setDay(initialDate);

        //Set BCD=15
        final Account account1 = createAccountWithNonOsgiPaymentMethod(getAccountData(15));
        //Create subscription on 2023-05-23
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.createBaseEntitlement(account1.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"), null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Prorated invoice generated for 2023-05-23 to 2023-06-15 for 14.63
        final List<Invoice> invoices1 = invoiceUserApi.getInvoicesByAccount(account1.getId(), false, false, true, callContext);
        assertEquals(invoices1.size(), 1);
        final List<ExpectedInvoiceItemCheck> toBeChecked1 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 23), new LocalDate(2023, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("14.63")));
        invoiceChecker.checkInvoice(invoices1.get(0).getId(), callContext, toBeChecked1);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1); //2023-06-23
        assertListenerStatus();

        //Set BCD=15
        final Account account2 = createAccountWithNonOsgiPaymentMethod(getAccountData(15));
        //Create subscription on 2023-06-23
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.createBaseEntitlement(account2.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"), null, null, null, null), "externalKey2", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Prorated invoice generated for 2023-06-23 to 2023-07-15 for 14.63
        final List<Invoice> invoices2 = invoiceUserApi.getInvoicesByAccount(account2.getId(), false, false, true, callContext);
        assertEquals(invoices2.size(), 1);
        final List<ExpectedInvoiceItemCheck> toBeChecked2 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 23), new LocalDate(2023, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("14.63")));

        invoiceChecker.checkInvoice(invoices2.get(0).getId(), callContext, toBeChecked2);
    }

    @Test(groups = "slow")
    public void testInvoiceTrailingProration1() throws Exception {
        final LocalDate initialDate = new LocalDate(2023, 4, 23);
        clock.setDay(initialDate);

        //Set BCD=23
        final Account account1 = createAccountWithNonOsgiPaymentMethod(getAccountData(23));
        //Create subscription on 2023-04-23
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId1 = entitlementApi.createBaseEntitlement(account1.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"), null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Full invoice generated for 2023-04-23 to 2023-05-23 for 19.95
        List<Invoice> invoices1 = invoiceUserApi.getInvoicesByAccount(account1.getId(), false, false, true, callContext);
        assertEquals(invoices1.size(), 1);
        List<ExpectedInvoiceItemCheck> toBeChecked1 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 4, 23), new LocalDate(2023, 5, 23), InvoiceItemType.RECURRING, new BigDecimal("19.95")));
        invoiceChecker.checkInvoice(invoices1.get(0).getId(), callContext, toBeChecked1);

        //Cancel on 2023-05-15
        LocalDate cancelDate = new LocalDate(2023, 5, 15);
        final Entitlement entitlement1 = entitlementApi.getEntitlementForId(entitlementId1, false, callContext);
        entitlement1.cancelEntitlementWithDate(cancelDate, true, Collections.emptyList(), callContext);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        clock.setDay(cancelDate);
        assertListenerStatus();

        //Prorated invoice (REPAIR_ADJ) generated for 2023-05-15 to 2023-05-23 for 5.32
        invoices1 = invoiceUserApi.getInvoicesByAccount(account1.getId(), false, false, true, callContext);
        assertEquals(invoices1.size(), 2);
        toBeChecked1 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 15), new LocalDate(2023, 5, 23), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-5.32")),
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 15), new LocalDate(2023, 5, 15), InvoiceItemType.CBA_ADJ, new BigDecimal("5.32")));
        invoiceChecker.checkInvoice(invoices1.get(1).getId(), callContext, toBeChecked1);

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 5, 23));
        assertListenerStatus();

        //Set BCD=23
        final Account account2 = createAccountWithNonOsgiPaymentMethod(getAccountData(23));
        //Create subscription on 2023-05-23
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId2 = entitlementApi.createBaseEntitlement(account2.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"), null, null, null, null), "externalKey2", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Full invoice generated for 2023-05-23 to 2023-06-23 for 19.95
        List<Invoice> invoices2 = invoiceUserApi.getInvoicesByAccount(account2.getId(), false, false, true, callContext);
        assertEquals(invoices2.size(), 1);
        List<ExpectedInvoiceItemCheck> toBeChecked2 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 23), new LocalDate(2023, 6, 23), InvoiceItemType.RECURRING, new BigDecimal("19.95")));
        invoiceChecker.checkInvoice(invoices2.get(0).getId(), callContext, toBeChecked2);

        //Cancel on 2023-06-15
        cancelDate = new LocalDate(2023, 6, 15);
        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlementId2, false, callContext);
        entitlement2.cancelEntitlementWithDate(cancelDate, true, Collections.emptyList(), callContext);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        clock.setDay(cancelDate);
        assertListenerStatus();

        //Prorated invoice (REPAIR_ADJ) generated for 2023-06-15 to 2023-06-23 for 5.32
        invoices2 = invoiceUserApi.getInvoicesByAccount(account2.getId(), false, false, true, callContext);
        assertEquals(invoices2.size(), 2);
        toBeChecked2 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 15), new LocalDate(2023, 6, 23), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-5.32")),
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 15), new LocalDate(2023, 6, 15), InvoiceItemType.CBA_ADJ, new BigDecimal("5.32")));
        invoiceChecker.checkInvoice(invoices2.get(1).getId(), callContext, toBeChecked2);

    }

    @Test(groups = "slow")
    public void testInvoiceTrailingProration2() throws Exception {
        final LocalDate initialDate = new LocalDate(2023, 5, 15);
        clock.setDay(initialDate);

        //Set BCD=15
        final Account account1 = createAccountWithNonOsgiPaymentMethod(getAccountData(15));
        //Create subscription on 2023-05-15
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId1 = entitlementApi.createBaseEntitlement(account1.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"), null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Full invoice generated for 2023-05-15 to 2023-06-15 for 19.95
        List<Invoice> invoices1 = invoiceUserApi.getInvoicesByAccount(account1.getId(), false, false, true, callContext);
        assertEquals(invoices1.size(), 1);
        List<ExpectedInvoiceItemCheck> toBeChecked1 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 15), new LocalDate(2023, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("19.95")));
        invoiceChecker.checkInvoice(invoices1.get(0).getId(), callContext, toBeChecked1);

        //Cancel on 2023-05-23
        LocalDate cancelDate = new LocalDate(2023, 5, 23);
        final Entitlement entitlement1 = entitlementApi.getEntitlementForId(entitlementId1, false, callContext);
        entitlement1.cancelEntitlementWithDate(cancelDate, true, Collections.emptyList(), callContext);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        clock.setDay(cancelDate);
        assertListenerStatus();

        //Prorated invoice (REPAIR_ADJ) generated for 2023-05-23 to 2023-06-15 for 14.63
        invoices1 = invoiceUserApi.getInvoicesByAccount(account1.getId(), false, false, true, callContext);
        assertEquals(invoices1.size(), 2);
        toBeChecked1 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 23), new LocalDate(2023, 6, 15), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-14.63")),
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 23), new LocalDate(2023, 5, 23), InvoiceItemType.CBA_ADJ, new BigDecimal("14.63")));
        invoiceChecker.checkInvoice(invoices1.get(1).getId(), callContext, toBeChecked1);

        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.setDay(new LocalDate(2023, 6, 15));
        assertListenerStatus();

        //Set BCD=15
        final Account account2 = createAccountWithNonOsgiPaymentMethod(getAccountData(15));
        //Create subscription on 2023-06-15
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId2 = entitlementApi.createBaseEntitlement(account2.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"), null, null, null, null), "externalKey2", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Full invoice generated for 2023-06-15 to 2023-07-15 for 19.95
        List<Invoice> invoices2 = invoiceUserApi.getInvoicesByAccount(account2.getId(), false, false, true, callContext);
        assertEquals(invoices2.size(), 1);
        List<ExpectedInvoiceItemCheck> toBeChecked2 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 15), new LocalDate(2023, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("19.95")));
        invoiceChecker.checkInvoice(invoices2.get(0).getId(), callContext, toBeChecked2);

        //Cancel on 2023-06-23
        cancelDate = new LocalDate(2023, 6, 23);
        final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlementId2, false, callContext);
        entitlement2.cancelEntitlementWithDate(cancelDate, true, Collections.emptyList(), callContext);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        clock.setDay(cancelDate);
        assertListenerStatus();

        //Prorated invoice (REPAIR_ADJ) generated for 2023-06-23 to 2023-07-15 for 14.63
        invoices2 = invoiceUserApi.getInvoicesByAccount(account2.getId(), false, false, true, callContext);
        assertEquals(invoices2.size(), 2);
        toBeChecked2 = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 23), new LocalDate(2023, 7, 15), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-14.63")),
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 23), new LocalDate(2023, 6, 23), InvoiceItemType.CBA_ADJ, new BigDecimal("14.63")));
        invoiceChecker.checkInvoice(invoices2.get(1).getId(), callContext, toBeChecked2); //Test fails here

    }

    @Test(groups = "slow")
    public void testInvoiceLeadingAndTrailingProration1() throws Exception {
        final LocalDate initialDate = new LocalDate(2023, 5, 15);
        clock.setDay(initialDate);

        //Set BCD=23
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(23));
        //Create subscription on 2023-05-15
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"), null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Prorated invoice generated for 2023-05-15 to 2023-05-23 for 5.32
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 1);
        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 15), new LocalDate(2023, 5, 23), InvoiceItemType.RECURRING, new BigDecimal("5.32")));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 5, 23)); //2023-05-23
        assertListenerStatus();

        //Full Invoice generated for 2023-05-23 to 2023-06-23
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);
        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 23), new LocalDate(2023, 6, 23), InvoiceItemType.RECURRING, new BigDecimal("19.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //Cancel on 2023-06-15
        LocalDate cancelDate = new LocalDate(2023, 6, 15);
        final Entitlement entitlement1 = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        entitlement1.cancelEntitlementWithDate(cancelDate, true, Collections.emptyList(), callContext);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        clock.setDay(cancelDate);
        assertListenerStatus();

        //Prorated invoice generated for 2023-06-15 to 2023-06-23 for 5.32
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 15), new LocalDate(2023, 6, 23), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-5.32")),
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 15), new LocalDate(2023, 6, 15), InvoiceItemType.CBA_ADJ, new BigDecimal("5.32")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);
    }

    @Test(groups = "slow")
    public void testInvoiceLeadingAndTrailingProration2() throws Exception {
        final LocalDate initialDate = new LocalDate(2023, 5, 23);
        clock.setDay(initialDate);

        //Set BCD=15
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(15));
        //Create subscription on 2023-05-23
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(new PlanPhaseSpecifier("pistol-monthly-notrial"), null, null, null, null), "externalKey", null, null, false, false, Collections.emptyList(), callContext);
        assertListenerStatus();

        //Prorated invoice generated for 2023-05-23 to 2023-06-15 for 14.63
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 1);
        List<ExpectedInvoiceItemCheck> toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 5, 23), new LocalDate(2023, 6, 15), InvoiceItemType.RECURRING, new BigDecimal("14.63")));
        invoiceChecker.checkInvoice(invoices.get(0).getId(), callContext, toBeChecked);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.setDay(new LocalDate(2023, 6, 15)); //2023-06-15
        assertListenerStatus();

        //Full Invoice generated for 2023-06-15 to 2023-07-15
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 2);
        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 15), new LocalDate(2023, 7, 15), InvoiceItemType.RECURRING, new BigDecimal("19.95")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);

        //Cancel on 2023-06-23
        LocalDate cancelDate = new LocalDate(2023, 6, 23);
        final Entitlement entitlement1 = entitlementApi.getEntitlementForId(entitlementId, false, callContext);
        entitlement1.cancelEntitlementWithDate(cancelDate, true, Collections.emptyList(), callContext);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.CANCEL, NextEvent.INVOICE);
        clock.setDay(cancelDate);
        assertListenerStatus();

        //Prorated invoice generated for 2023-06-23 to 2023-07-15 for 14.63
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, true, callContext);
        assertEquals(invoices.size(), 3);
        toBeChecked = List.of(
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 23), new LocalDate(2023, 7, 15), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-14.63")),
                new ExpectedInvoiceItemCheck(new LocalDate(2023, 6, 23), new LocalDate(2023, 6, 23), InvoiceItemType.CBA_ADJ, new BigDecimal("14.63")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);
    }


}
