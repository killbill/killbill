/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestIntegrationWithAutoInvoiceDraft extends TestIntegrationBase {

    @Inject
    private InvoiceUserApi invoiceApi;

    @Inject
    private TagUserApi tagApi;

    private Account account;
    private String productName;
    private BillingPeriod term;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));
        assertNotNull(account);
        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;
    }


    @Test(groups = "slow")
    public void testAutoInvoicingDraftBasic() throws Exception {
        clock.setTime(new DateTime(2017, 6, 16, 18, 24, 42, 0));
        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK);
        assertNotNull(bpEntitlement);

        List<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);
        final Invoice trialInvoice = invoices.get(0);
        assertEquals(trialInvoice.getStatus(), InvoiceStatus.DRAFT);

        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        invoiceApi.commitInvoice(trialInvoice.getId(), callContext);
        assertListenerStatus();

        // Move out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE);
        clock.addDays(30);
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);

        final Invoice firstNonTrialInvoice = invoices.get(1);
        assertEquals(firstNonTrialInvoice.getStatus(), InvoiceStatus.DRAFT);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceApi.commitInvoice(firstNonTrialInvoice.getId(), callContext);
        assertListenerStatus();


        remove_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);


        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 4);
    }


    @Test(groups = "slow")
    public void testWithExistingDraftInvoice() throws Exception {
        clock.setTime(new DateTime(2017, 6, 16, 18, 24, 42, 0));
        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK);
        assertNotNull(bpEntitlement);

        List<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);
        final Invoice trialInvoice = invoices.get(0);
        assertEquals(trialInvoice.getStatus(), InvoiceStatus.DRAFT);

        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        invoiceApi.commitInvoice(trialInvoice.getId(), callContext);
        assertListenerStatus();

        // Move out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE);
        clock.addDays(30);
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);

        // Check firstNonTrialInvoice  is still in DRAFT
        final Invoice firstNonTrialInvoice = invoices.get(1);
        assertEquals(firstNonTrialInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertEquals(firstNonTrialInvoice.getInvoiceDate(), new LocalDate(2017, 07, 16));


        final List<ExpectedInvoiceItemCheck> toBeChecked = new ArrayList<ExpectedInvoiceItemCheck>();
        toBeChecked.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 7, 16), new LocalDate(2017, 7, 25), InvoiceItemType.RECURRING, new BigDecimal("74.99")));
        invoiceChecker.checkInvoice(invoices.get(1).getId(), callContext, toBeChecked);
        toBeChecked.clear();

        // Check account balance is still reflected as Zero
        final BigDecimal accountBalance = invoiceApi.getAccountBalance(account.getId(), callContext);
        assertEquals(accountBalance.compareTo(BigDecimal.ZERO), 0);

        remove_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3);

        // Check prev invoice is still in DRAFT
        assertEquals(invoices.get(1).getStatus(), InvoiceStatus.DRAFT);

        // Check most recent invoice is COMMITTED
        assertEquals(invoices.get(2).getStatus(), InvoiceStatus.COMMITTED);

        // Verify most recent invoice *only* contains the items for the period 2017-07-25 -> 2017-08-25  (and not 2017-07-16 -> 2017-07-25 from the DRAFT invoice)
        toBeChecked.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 7, 25), new LocalDate(2017, 8, 25), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoice(invoices.get(2).getId(), callContext, toBeChecked);
        toBeChecked.clear();

        // Finally commit second invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        invoiceApi.commitInvoice(firstNonTrialInvoice.getId(), callContext);
        assertListenerStatus();

        final BigDecimal accountBalance2 = invoiceApi.getAccountBalance(account.getId(), callContext);
        assertEquals(accountBalance2.compareTo(BigDecimal.ZERO), 0);

    }


    @Test(groups = "slow")
    public void testAutoInvoicingReuseDraftBasic() throws Exception {
        clock.setTime(new DateTime(2017, 6, 16, 18, 24, 42, 0));

        // Set both AUTO_INVOICING_DRAFT and AUTO_INVOICING_REUSE_DRAFT
        add_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);
        add_AUTO_INVOICING_REUSE_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        // Create initial DRAFt invoice that will be reused by the system
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = startDate.plusDays(5);
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, account.getId(), null, "Initial external charge", startDate, endDate, BigDecimal.TEN, Currency.USD, null);
        invoiceUserApi.insertExternalCharges(account.getId(), clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), false, callContext).get(0);

        List<Invoice> invoices;
        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getInvoiceItems().size(), 1);
        assertEquals(invoices.get(0).getStatus(), InvoiceStatus.DRAFT);

        final UUID invoiceId = invoices.get(0).getId();

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK);
        assertNotNull(bpEntitlement);

        // Verify we see the new item on our existing DRAFT invoice
        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getId(), invoiceId);
        assertEquals(invoices.get(0).getInvoiceItems().size(), 2);
        assertEquals(invoices.get(0).getStatus(), InvoiceStatus.DRAFT);


        // Move out of TRIAL
        busHandler.pushExpectedEvents(NextEvent.PHASE);
        clock.addDays(30);
        assertListenerStatus();

        // Verify again we see the new item on our existing DRAFT invoice
        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getId(), invoiceId);
        assertEquals(invoices.get(0).getInvoiceItems().size(), 3);
        assertEquals(invoices.get(0).getStatus(), InvoiceStatus.DRAFT);

        // Remove AUTO_INVOICING_DRAFT, so next invoicing should commit DRAFt invoice
        remove_AUTO_INVOICING_DRAFT_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // Verify again we see the new item and this time invoice is in COMMITTED
        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getId(), invoiceId);
        assertEquals(invoices.get(0).getInvoiceItems().size(), 4);
        assertEquals(invoices.get(0).getStatus(), InvoiceStatus.COMMITTED);


        // Verify we see a new invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        assertEquals(invoices.get(1).getInvoiceItems().size(), 1);
        assertEquals(invoices.get(1).getStatus(), InvoiceStatus.COMMITTED);
    }


}
