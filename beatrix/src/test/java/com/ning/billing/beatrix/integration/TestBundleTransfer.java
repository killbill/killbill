/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
package com.ning.billing.beatrix.integration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import junit.framework.Assert;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;

@Guice(modules = {BeatrixModule.class})
public class TestBundleTransfer extends TestIntegrationBase {


    @Test(groups = "slow")
    public void testBundleTransferWithBPAnnualOnly() throws Exception {

        final Account account = createAccountWithPaymentMethod(getAccountData(3));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC

        final DateTime initialDate = new DateTime(2012, 4, 1, 0, 15, 42, 0, testTimeZone);

        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "mycutebundle", context);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final PlanPhaseSpecifier bpPlanPhaseSpecifier = new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null);
        final SubscriptionData bpSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                       bpPlanPhaseSpecifier,
                                                                                                                       null,
                                                                                                                       context));
        assertNotNull(bpSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId()).size(), 1);

        assertEquals(entitlementUserApi.getSubscriptionFromId(bpSubscription.getId()).getCurrentPlan().getBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDays(40);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        // BUNDLE TRANSFER
        final Account newAccount = createAccountWithPaymentMethod(getAccountData(17));

        busHandler.pushExpectedEvent(NextEvent.TRANSFER);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        transferApi.transferBundle(account.getId(), newAccount.getId(), "mycutebundle", clock.getUTCNow(), false, context);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        List<Invoice> invoices =invoiceUserApi.getInvoicesByAccount(newAccount.getId());
        assertEquals(invoices.size(), 1);

        final List<InvoiceItem> invoiceItems = invoices.get(0).getInvoiceItems();
        assertEquals(invoiceItems.size(), 1);
        InvoiceItem theItem = invoiceItems.get(0);
        Assert.assertTrue(theItem.getStartDate().compareTo(new LocalDate(2012,05,11)) == 0);
        Assert.assertTrue(theItem.getEndDate().compareTo(new LocalDate(2013,05,11)) == 0);
        Assert.assertTrue(theItem.getAmount().compareTo(new BigDecimal("2399.9500")) == 0);
    }

    @Test(groups = "slow")
    public void testBundleTransferWithBPMonthlyOnly() throws Exception {

        final Account account = createAccountWithPaymentMethod(getAccountData(9));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC

        final DateTime initialDate = new DateTime(2012, 4, 1, 0, 15, 42, 0, testTimeZone);

        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "mycutebundle", context);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final PlanPhaseSpecifier bpPlanPhaseSpecifier = new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null);
        final SubscriptionData bpSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                       bpPlanPhaseSpecifier,
                                                                                                                       null,
                                                                                                                       context));
        assertNotNull(bpSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId()).size(), 1);

        assertEquals(entitlementUserApi.getSubscriptionFromId(bpSubscription.getId()).getCurrentPlan().getBillingPeriod(), BillingPeriod.MONTHLY);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDays(32);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        // BUNDLE TRANSFER
        final Account newAccount = createAccountWithPaymentMethod(getAccountData(15));

        busHandler.pushExpectedEvent(NextEvent.TRANSFER);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        transferApi.transferBundle(account.getId(), newAccount.getId(), "mycutebundle", clock.getUTCNow(), false, context);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        List<Invoice> invoices =invoiceUserApi.getInvoicesByAccount(newAccount.getId());
        assertEquals(invoices.size(), 1);

        final List<InvoiceItem> invoiceItems = invoices.get(0).getInvoiceItems();
        assertEquals(invoiceItems.size(), 1);
        InvoiceItem theItem = invoiceItems.get(0);
        Assert.assertTrue(theItem.getStartDate().compareTo(new LocalDate(2012,05,03)) == 0);
        Assert.assertTrue(theItem.getEndDate().compareTo(new LocalDate(2012,05,15)) == 0);
        Assert.assertTrue(theItem.getAmount().compareTo(new BigDecimal("99.98")) == 0);
    }
}
