/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.Collection;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;

public class TestInvoiceSystemDisabling extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testInvoiceSystemDisablingBasic() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        Assert.assertFalse(parkedAccountsManager.isParked(internalCallContext));

        // Stop invoicing system
        invoiceConfig.setInvoicingSystemEnabled(false);

        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(),
                                                                                             "bundleKey",
                                                                                             "Shotgun",
                                                                                             ProductCategory.BASE,
                                                                                             BillingPeriod.MONTHLY,
                                                                                             NextEvent.CREATE,
                                                                                             NextEvent.BLOCK,
                                                                                             NextEvent.TAG);

        Assert.assertTrue(parkedAccountsManager.isParked(internalCallContext));
        Collection<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 0);

        // Move to end of trial =>  2012, 5, 1
        addDaysAndCheckForCompletion(30, NextEvent.PHASE);

        Assert.assertTrue(parkedAccountsManager.isParked(internalCallContext));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 0);

        // Dry-run generation
        Invoice invoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), clock.getUTCToday(), new TestDryRunArguments(DryRunType.TARGET_DATE), callContext);
        assertListenerStatus();
        final ImmutableList<ExpectedInvoiceItemCheck> expected = ImmutableList.<ExpectedInvoiceItemCheck>of(new ExpectedInvoiceItemCheck(new LocalDate(2012, 4, 1), null, InvoiceItemType.FIXED, BigDecimal.ZERO),
                                                                                                            new ExpectedInvoiceItemCheck(new LocalDate(2012, 5, 1), new LocalDate(2012, 6, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoiceChecker.checkInvoiceNoAudits(invoice, expected);

        // Still parked
        Assert.assertTrue(parkedAccountsManager.isParked(internalCallContext));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 0);

        // Non dry-run generation
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        invoice = invoiceUserApi.triggerInvoiceGeneration(account.getId(), clock.getUTCToday(), callContext);
        assertListenerStatus();

        // Now unparked
        Assert.assertFalse(parkedAccountsManager.isParked(internalCallContext));
        invoiceChecker.checkInvoice(invoice, callContext, expected);
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, expected);

        // Restart invoicing system and verify next notification
        invoiceConfig.setInvoicingSystemEnabled(true);
        addDaysAndCheckForCompletion(31, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        invoiceChecker.checkInvoice(account.getId(),
                                    2,
                                    callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2012, 6, 1), new LocalDate(2012, 7, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
    }
}
