/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import java.util.List;

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestInvoicePayment extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testPartialPayments() throws Exception {

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        clock.setDay(new LocalDate(2012, 4, 1));

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(null, account.getId(), null, "Initial external charge", clock.getUTCToday(), BigDecimal.TEN, Currency.USD);
        final InvoiceItem item1 = invoiceUserApi.insertExternalCharges(account.getId(), clock.getUTCToday(), ImmutableList.<InvoiceItem>of(externalCharge), callContext).get(0);
        assertListenerStatus();

        final Invoice invoice = invoiceUserApi.getInvoice(item1.getInvoiceId(), callContext);
        final Payment payment1 = createPaymentAndCheckForCompletion(account, invoice, new BigDecimal("4.00"), account.getCurrency(),  NextEvent.PAYMENT);

        Invoice invoice1 = invoiceUserApi.getInvoice(item1.getInvoiceId(), callContext);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("6.00")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(new BigDecimal("4.00")) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(BigDecimal.TEN) == 0);

        BigDecimal accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance.compareTo(new BigDecimal("6.00")) == 0);

        final Payment payment2 = createPaymentAndCheckForCompletion(account, invoice, new BigDecimal("6.00"), account.getCurrency(),  NextEvent.PAYMENT);

        invoice1 = invoiceUserApi.getInvoice(item1.getInvoiceId(), callContext);
        assertTrue(invoice1.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.TEN) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(BigDecimal.TEN) == 0);

        accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance.compareTo(BigDecimal.ZERO) == 0);

/*
        This does not work since item is paid across multiple payments and so the mount is bigger than the payment.

        // Now, issue refund with item adjustment on first invoice/item
        paymentApi.createRefundWithItemsAdjustments(account, payment1.getId(), Sets.<UUID>newHashSet(item1.getId()), callContext);

        invoice1 = invoiceUserApi.getInvoice(item1.getInvoiceId(), callContext);
        assertTrue(invoice1.getBalance().compareTo(BigDecimal.ZERO) == 0);

        accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance.compareTo(BigDecimal.ZERO) == 0);

*/

        refundPaymentAndCheckForCompletion(account, payment1, NextEvent.PAYMENT, NextEvent.INVOICE_ADJUSTMENT);

        invoice1 = invoiceUserApi.getInvoice(item1.getInvoiceId(), callContext);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("4.00")) == 0);

        accountBalance = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance.compareTo(new BigDecimal("4.00")) == 0);

    }

    //

    @Test(groups = "slow")
    public void testWithPaymentFailure() throws Exception {

        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        paymentPlugin.makeNextPaymentFailWithError();

        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.INVOICE);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);
        clock.addDays(30);
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2);

        final Invoice invoice1 = invoices.get(0).getInvoiceItems().get(0).getInvoiceItemType() == InvoiceItemType.RECURRING ?
                           invoices.get(0) : invoices.get(1);
        assertTrue(invoice1.getBalance().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice1.getPaidAmount().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice1.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice1.getPayments().size(), 1);
        assertFalse(invoice1.getPayments().get(0).isSuccess());

        BigDecimal accountBalance1 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance1.compareTo(new BigDecimal("249.95")) == 0);

        final List<Payment> payments = paymentApi.getAccountPayments(account.getId(), false, ImmutableList.<PluginProperty>of(), callContext);
        assertEquals(payments.size(), 1);

        // Trigger the payment retry
        busHandler.pushExpectedEvents(NextEvent.PAYMENT);
        clock.addDays(8);
        assertListenerStatus();

        Invoice invoice2 = invoiceUserApi.getInvoice(invoice1.getId(), callContext);
        assertTrue(invoice2.getBalance().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(invoice2.getPaidAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertTrue(invoice2.getChargedAmount().compareTo(new BigDecimal("249.95")) == 0);
        assertEquals(invoice2.getPayments().size(), 1);
        assertTrue(invoice2.getPayments().get(0).isSuccess());

        BigDecimal accountBalance2 = invoiceUserApi.getAccountBalance(account.getId(), callContext);
        assertTrue(accountBalance2.compareTo(BigDecimal.ZERO) == 0);
    }


}
