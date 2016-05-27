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
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class TestWithBCDUpdate extends TestIntegrationBase {

    @Inject
    protected SubscriptionBaseInternalApi subscriptionBaseInternalApi;

    @Test(groups = "slow")
    public void testBCDChangeForSubscriptionBillingAlignment() throws Exception {

        final DateTime initialDate = new DateTime(2016, 4, 1, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        // BP creation : Will set Account BCD to the first (2016-4-1 + 30 days = 2016-5-1)
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final DefaultEntitlement baseEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);

        // 2016-4-4 : (BP still in TRIAL)
        // Laser-Scope is SUBSCRIPTION aligned with a 1 month DISCOUNT
        clock.addDays(3);
        final DefaultEntitlement aoEntitlement = addAOEntitlementAndCheckForCompletion(baseEntitlement.getBundleId(), "Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY,
                                                                                       NextEvent.CREATE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // 2016-5-1 : BP out of TRIAL + AO
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(27);
        assertListenerStatus();

        // 2016-5-4: Laser-Scope out of DISCOUNT
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(3);
        assertListenerStatus();

        // 2016-6-1 : BP + AO invoice
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(28);
        assertListenerStatus();

        // 2016-6-4 : Change BCD for AO and
        clock.addDays(3);

        busHandler.pushExpectedEvents(NextEvent.BCD_CHANGE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        subscriptionBaseInternalApi.updateBCD(aoEntitlement.getId(), 4, internalCallContext);
        assertListenerStatus();

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        List<Invoice> invoices = null;

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 4), new LocalDate(2016, 7, 4), InvoiceItemType.RECURRING, new BigDecimal("1999.95")));
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 6, 4), new LocalDate(2016, 7, 1), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-1799.96")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        invoiceChecker.checkInvoice(invoices.get(5).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-7-1 : BP only
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(27);
        assertListenerStatus();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 1), new LocalDate(2016, 8, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        invoiceChecker.checkInvoice(invoices.get(6).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        // 2016-7-4 : AO only
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(3);
        assertListenerStatus();

        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2016, 7, 4), new LocalDate(2016, 8, 4), InvoiceItemType.RECURRING, new BigDecimal("1999.95")));
        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        invoiceChecker.checkInvoice(invoices.get(7).getId(), callContext, expectedInvoices);
        expectedInvoices.clear();

        checkNoMoreInvoiceToGenerate(account);
    }

}
