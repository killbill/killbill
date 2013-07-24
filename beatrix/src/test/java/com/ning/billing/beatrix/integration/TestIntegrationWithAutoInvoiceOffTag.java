/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.DefaultEntitlement;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;

import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestIntegrationWithAutoInvoiceOffTag extends TestIntegrationBase {

    @Inject
    private InvoiceUserApi invoiceApi;

    @Inject
    private TagUserApi tagApi;

    private Account account;
    private SubscriptionBundle bundle;
    private String productName;
    private BillingPeriod term;
    private String planSetName;

    @Override
    @BeforeMethod(groups = {"slow"})
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));
        assertNotNull(account);
        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;
        planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
    }

    @Test(groups = {"slow"}, enabled = true)
    public void testAutoInvoiceOffAccount() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        busHandler.pushExpectedEvents(NextEvent.TAG);
        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        // set next invoice to fail and create network
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE);
        assertNotNull(bpEntitlement);

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 0);

        clock.addDays(10); // DAY 10 still in trial
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 0);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        clock.addDays(30); // DAY 40 out of trial
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 0);

        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.INVOICE, NextEvent.PAYMENT);
        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 1);
    }

    @Test(groups = {"slow"}, enabled = true)
    public void testAutoInvoiceOffSingleSubscription() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // set next invoice to fail and create network
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 1); // first invoice is generated immediately after creation can't reliably stop it


        add_AUTO_INVOICING_OFF_Tag(bpEntitlement.getSubscription().getBundleId(), ObjectType.BUNDLE);

        busHandler.pushExpectedEvents(NextEvent.PHASE);
        clock.addDays(40); // DAY 40 out of trial
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 1); //No additional invoices generated

    }


    @Test(groups = {"slow"}, enabled = true)
    public void testAutoInvoiceOffMultipleSubscriptions() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // set next invoice to fail and create network
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        final DefaultEntitlement bpEntitlement2 = createBaseEntitlementAndCheckForCompletion(account.getId(), "whatever", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.INVOICE);
        assertNotNull(bpEntitlement2);

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2); // first invoice is generated immediately after creation can't reliably stop it

        add_AUTO_INVOICING_OFF_Tag(bpEntitlement.getSubscription().getBundleId(), ObjectType.BUNDLE);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT);
        clock.addDays(40); // DAY 40 out of trial
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 3); // Only one additional invoice generated
    }


    private void add_AUTO_INVOICING_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        tagApi.addTag(id, type, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        final List<Tag> tags = tagApi.getTagsForObject(id, type, callContext);
        assertEquals(tags.size(), 1);
    }


    private void remove_AUTO_INVOICING_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        tagApi.removeTag(id, type, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
    }
}
