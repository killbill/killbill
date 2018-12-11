/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.beatrix.integration;

import java.util.Collection;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestIntegrationWithAutoInvoiceOffTag extends TestIntegrationBase {

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
    public void testAutoInvoiceOffAccount() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        // set next invoice to fail and create network
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK);
        assertNotNull(bpEntitlement);

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 0);

        clock.addDays(10); // DAY 10 still in trial
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 0);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        clock.addDays(30); // DAY 40 out of trial
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 0);

        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1074")
    public void testAutoInvoiceOffWithTZ() throws Exception {
        clock.setTime(new DateTime(2018, 12, 1, 0, 25, 0, 0));

        account = createAccountWithNonOsgiPaymentMethod(getAccountData(null, DateTimeZone.forID("America/Los_Angeles")));
        assertEquals(account.getBillCycleDayLocal(), (Integer) 0);

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);
        assertEquals(accountUserApi.getAccountById(account.getId(), callContext).getBillCycleDayLocal(), (Integer) 30);

        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getInvoiceItems().size(), 1);
        assertEquals(invoices.get(0).getTargetDate(), new LocalDate(2018, 11, 30));
        assertEquals(invoices.get(0).getInvoiceItems().get(0).getStartDate(), new LocalDate(2018, 11, 30));

        clock.setTime(new DateTime(2018, 12, 30, 0, 20, 0, 0));
        assertEquals(clock.getUTCToday(), new LocalDate(2018, 12, 30));
        assertEquals(clock.getUTCNow().toDateTime(account.getTimeZone()).toLocalDate(), new LocalDate(2018, 12, 29));
        // Still in trial
        assertListenerStatus();
        assertEquals(entitlementApi.getEntitlementForId(bpEntitlement.getId(), callContext).getLastActivePhase().getName(), "shotgun-monthly-trial");
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        // Adding / Removing AUTO_INVOICING_OFF shouldn't have any impact
        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT, NextEvent.NULL_INVOICE);

        assertListenerStatus();
        assertEquals(entitlementApi.getEntitlementForId(bpEntitlement.getId(), callContext).getLastActivePhase().getName(), "shotgun-monthly-trial");
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext).size(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.setTime(new DateTime(2018, 12, 31, 0, 25, 0, 0));
        assertListenerStatus();

        invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2);
        assertEquals(invoices.get(1).getTargetDate(), new LocalDate(2018, 12, 30));
        assertEquals(invoices.get(1).getInvoiceItems().get(0).getStartDate(), new LocalDate(2018, 12, 30));
        assertEquals(invoices.get(1).getInvoiceItems().get(0).getEndDate(), new LocalDate(2019, 1, 30));
    }
}
