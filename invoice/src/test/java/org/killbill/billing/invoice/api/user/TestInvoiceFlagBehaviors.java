/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.invoice.api.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.util.tag.ControlTagType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;

public class TestInvoiceFlagBehaviors extends InvoiceTestSuiteWithEmbeddedDB {

    private UUID accountId;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        final Account account = invoiceUtil.createAccount(callContext);
        accountId = account.getId();
    }

    @Test(groups = "slow", description = "Verify invoice/account balance with a WRITTEN_OFF invoice. Verify account credit is not used against such invoice")
    public void testWrittenOffInvoiceBeforeAccountCredit() throws Exception {

        // Create new invoice with one charge and expect account credit to be used
        final List<InvoiceItem> items = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(new ExternalChargeInvoiceItem(UUID.randomUUID(), clock.getUTCNow(), null, accountId, null, null, null, null, BigDecimal.TEN, accountCurrency, null)), true, callContext);
        assertEquals(items.size(), 1);

        // Check both invoice and account balance is 10.00
        final UUID invoiceId = items.get(0).getInvoiceId();

        final Invoice invoice0 = invoiceUserApi.getInvoice(invoiceId, callContext);
        assertEquals(invoice0.getBalance().compareTo(BigDecimal.TEN), 0);
        final BigDecimal accountBalance0 = invoiceUserApi.getAccountBalance(accountId, callContext);
        assertEquals(accountBalance0.compareTo(BigDecimal.TEN), 0);

        final BigDecimal accountCBA0 = invoiceUserApi.getAccountCBA(accountId, callContext);
        assertEquals(accountCBA0.compareTo(BigDecimal.ZERO), 0);

        // Tag invoice with WRITTEN_OFF and expect balance to now show as Zero
        tagUserApi.addTag(invoiceId, ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.getId(), callContext);

        // Check both invoice and account balance is NOW  0
        final Invoice invoice1 = invoiceUserApi.getInvoice(invoiceId, callContext);
        assertEquals(invoice1.getBalance().compareTo(BigDecimal.ZERO), 0);

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(accountId, callContext);
        assertEquals(accountBalance1.compareTo(BigDecimal.ZERO), 0);

        final BigDecimal accountCBA1 = invoiceUserApi.getAccountCBA(accountId, callContext);
        assertEquals(accountCBA1.compareTo(BigDecimal.ZERO), 0);

        // Add credit on the account
        invoiceUserApi.insertCredit(accountId, BigDecimal.TEN, null, accountCurrency, true, null, null, callContext);

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoiceId, callContext);
        assertEquals(invoice2.getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoice2.getInvoiceItems().size(), 1);

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(accountId, callContext);
        assertEquals(accountBalance2.compareTo(new BigDecimal("-10.00")), 0);

        final BigDecimal accountCBA2 = invoiceUserApi.getAccountCBA(accountId, callContext);
        assertEquals(accountCBA2.compareTo(BigDecimal.TEN), 0);


    }

    @Test(groups = "slow", description = "Verify invoice/account balance with a WRITTEN_OFF invoice. Verify behavior when WRITTEN_OFF tag is added after credit was added to invoice" )
    public void testWrittenOffInvoiceWithAccountCredit() throws Exception {

        // Add credit on the account
        invoiceUserApi.insertCredit(accountId, BigDecimal.TEN, null, accountCurrency, true, null, null, callContext);

        final BigDecimal accountBalance0 = invoiceUserApi.getAccountBalance(accountId, callContext);
        assertEquals(accountBalance0.compareTo(new BigDecimal("-10.0")), 0);

        final BigDecimal accountCBA0 = invoiceUserApi.getAccountCBA(accountId, callContext);
        assertEquals(accountCBA0.compareTo(BigDecimal.TEN), 0);

        // Create new invoice with one charge and expect account credit to be used
        final List<InvoiceItem> items = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(new ExternalChargeInvoiceItem(UUID.randomUUID(), clock.getUTCNow(), null, accountId, null, null, null, null, new BigDecimal("13.5"), accountCurrency, null)), true, callContext);
        assertEquals(items.size(), 1);

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(accountId, callContext);
        assertEquals(accountBalance1.compareTo(new BigDecimal("3.5")), 0);

        final BigDecimal accountCBA1 = invoiceUserApi.getAccountCBA(accountId, callContext);
        assertEquals(accountCBA1.compareTo(BigDecimal.ZERO), 0);

        // Tag invoice with WRITTEN_OFF and expect balance to now show as Zero
        tagUserApi.addTag(items.get(0).getInvoiceId(), ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.getId(), callContext);

        final BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(accountId, callContext);
        assertEquals(accountBalance2.compareTo(BigDecimal.ZERO), 0);

        final BigDecimal accountCBA2 = invoiceUserApi.getAccountCBA(accountId, callContext);
        assertEquals(accountCBA2.compareTo(BigDecimal.ZERO), 0);
    }


    @Test(groups = "slow", description = "Verify invoice/account balance with migrated invoice. Verify account credit is not consumed and that invoice/account balance does not take into account migrated invoice.")
    public void testMigratedInvoiceWithAccountCredit() throws Exception {

        // Add credit on the account
        invoiceUserApi.insertCredit(accountId, BigDecimal.TEN, null, accountCurrency, true, null, null, callContext);

        final UUID invoiceId = invoiceUserApi.createMigrationInvoice(accountId, null, ImmutableList.<InvoiceItem>of(new FixedPriceInvoiceItem(UUID.randomUUID(), clock.getUTCNow(), null, accountId, null, null, null, "foo", "bar", null, null, BigDecimal.ONE, accountCurrency)), callContext);

        final Invoice invoice1 = invoiceUserApi.getInvoice(invoiceId, callContext);
        assertEquals(invoice1.getBalance().compareTo(BigDecimal.ZERO), 0);

        // Verify credit is **not applied** against migration invoice
        final BigDecimal accountBalance0 = invoiceUserApi.getAccountBalance(accountId, callContext);
        assertEquals(accountBalance0.compareTo(new BigDecimal("-10.0")), 0);

        final BigDecimal accountCBA0 = invoiceUserApi.getAccountCBA(accountId, callContext);
        assertEquals(accountCBA0.compareTo(BigDecimal.TEN), 0);

    }

    @Test(groups = "slow", description = "Verify invoice/account balance with DRAFT invoice. Verify that invoice/account balance are ZERO in DRAFT mode but becomes visible after it hasa been COMMITTED." )
    public void testDraftInvoiceWithAccountCredit() throws Exception {

        // Add credit on the account
        invoiceUserApi.insertCredit(accountId, BigDecimal.TEN, null, accountCurrency, true, null, null, callContext);

        // Create new invoice with one charge and expect account credit to be used
        final List<InvoiceItem> items = invoiceUserApi.insertExternalCharges(accountId, clock.getUTCToday(), ImmutableList.<InvoiceItem>of(new ExternalChargeInvoiceItem(UUID.randomUUID(), clock.getUTCNow(), null, accountId, null, null, null, null, new BigDecimal("4.0"), accountCurrency, null)), false, callContext);
        assertEquals(items.size(), 1);

        final UUID invoiceId = items.get(0).getInvoiceId();

        final Invoice invoice1 = invoiceUserApi.getInvoice(invoiceId, callContext);
        assertEquals(invoice1.getStatus(), InvoiceStatus.DRAFT);

        // Verify CBA was *NOT* applied against DRAFT invoice
        assertEquals(invoice1.getInvoiceItems().size(), 1);
        // And balance is ZERO because DRAFT mode
        assertEquals(invoice1.getBalance().compareTo(BigDecimal.ZERO), 0);

        // Verify credit is not applied against migration invoice
        final BigDecimal accountBalance0 = invoiceUserApi.getAccountBalance(accountId, callContext);
        assertEquals(accountBalance0.compareTo(new BigDecimal("-10.0")), 0);

        final BigDecimal accountCBA0 = invoiceUserApi.getAccountCBA(accountId, callContext);
        assertEquals(accountCBA0.compareTo(BigDecimal.TEN), 0);

        invoiceUserApi.commitInvoice(invoiceId, callContext);

        final Invoice invoice2 = invoiceUserApi.getInvoice(invoiceId, callContext);
        assertEquals(invoice2.getStatus(), InvoiceStatus.COMMITTED);

        // Verify this time credit was applied against COMMITTED invoice
        assertEquals(invoice2.getBalance().compareTo(BigDecimal.ZERO), 0);

        final BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(accountId, callContext);
        assertEquals(accountBalance1.compareTo(new BigDecimal("-6.0")), 0);

        final BigDecimal accountCBA1 = invoiceUserApi.getAccountCBA(accountId, callContext);
        assertEquals(accountCBA1.compareTo(new BigDecimal("6.0")), 0);
    }

}
