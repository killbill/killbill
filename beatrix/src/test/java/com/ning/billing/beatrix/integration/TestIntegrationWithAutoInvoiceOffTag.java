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

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
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
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;

import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Guice(modules = {BeatrixModule.class})
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

    @BeforeMethod(groups = {"slow"})
    public void setupBeforeTest() throws Exception {

        account = createAccountWithPaymentMethod(getAccountData(25));
        assertNotNull(account);

        bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", callContext);

        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;
        planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
    }

    @Test(groups = {"slow"}, enabled = true)
    public void testAutoInvoiceOffAccount() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        // set next invoice to fail and create network
        busHandler.pushExpectedEvents(NextEvent.CREATE);
        final SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                   new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, callContext));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));


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

        busHandler.pushExpectedEvents(NextEvent.INVOICE);
        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 1);
    }

    @Test(groups = {"slow"}, enabled = true)
    public void testAutoInvoiceOffSingleSubscription() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));

        // set next invoice to fail and create network
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                   new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, callContext));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 1); // first invoice is generated immediately after creation can't reliably stop it


        add_AUTO_INVOICING_OFF_Tag(baseSubscription.getBundleId(), ObjectType.BUNDLE);

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
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                   new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, callContext));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));

        final SubscriptionBundle bundle2 = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", callContext);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final SubscriptionData baseSubscription2 = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle2.getId(),
                                                                                                                    new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, callContext));
        assertNotNull(baseSubscription2);
        assertTrue(busHandler.isCompleted(DELAY));

        Collection<Invoice> invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 2); // first invoice is generated immediately after creation can't reliably stop it

        add_AUTO_INVOICING_OFF_Tag(baseSubscription.getBundleId(), ObjectType.BUNDLE);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE, NextEvent.INVOICE);
        clock.addDays(40); // DAY 40 out of trial
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceApi.getInvoicesByAccount(account.getId(), callContext);
        assertEquals(invoices.size(), 3); // Only one additional invoice generated
    }


    private void add_AUTO_INVOICING_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        tagApi.addTag(id, type, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        final Map<String, Tag> tags = tagApi.getTags(id, type, callContext);
        assertEquals(tags.size(), 1);
    }


    private void remove_AUTO_INVOICING_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        tagApi.removeTag(id, type, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
    }
}
