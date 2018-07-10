/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
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

    @Test(groups = "slow")
    public void testAutoInvoiceOffSingleSubscription() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // set next invoice to fail and create network
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1); // first invoice is generated immediately after creation can't reliably stop it

        add_AUTO_INVOICING_OFF_Tag(bpEntitlement.getSubscriptionBase().getBundleId(), ObjectType.BUNDLE);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        clock.addDays(40); // DAY 40 out of trial
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 1); //No additional invoices generated
    }

    @Test(groups = "slow")
    public void testAutoInvoiceOffMultipleSubscriptions() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // set next invoice to fail and create network
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        final DefaultEntitlement bpEntitlement2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "whatever", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement2);

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 2); // first invoice is generated immediately after creation can't reliably stop it

        add_AUTO_INVOICING_OFF_Tag(bpEntitlement.getSubscriptionBase().getBundleId(), ObjectType.BUNDLE);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE, NextEvent.NULL_INVOICE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(40); // DAY 40 out of trial
        assertListenerStatus();

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        assertEquals(invoices.size(), 3); // Only one additional invoice generated
    }

}
