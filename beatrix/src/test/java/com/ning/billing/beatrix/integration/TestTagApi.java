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

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBase;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

import com.google.inject.Inject;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestTagApi extends TestIntegrationBase {


    private Account account;

    @Inject
    private TagUserApi tagApi;

    @Override
    @BeforeMethod(groups = {"slow"})
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));
        assertNotNull(account);
    }

    @Test(groups = {"slow"}, enabled = true)
    public void testApiTagOnAccount() throws Exception {

        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        assertTrue(busHandler.isCompleted(DELAY));

        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_PAY_OFF.getId(), callContext);
        assertTrue(busHandler.isCompleted(DELAY));

        List<Tag> tags = tagApi.getTagsForAccount(account.getId(), callContext);
        Assert.assertEquals(tags.size(), 2);

        tags = tagApi.getTagsForObject(account.getId(), ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(tags.size(), 2);

        tags = tagApi.getTagsForAccountType(account.getId(), ObjectType.ACCOUNT, callContext);
        Assert.assertEquals(tags.size(), 2);
    }


    @Test(groups = {"slow"}, enabled = true)
    public void testApiTagOnInvoice() throws Exception {

        //
        // Create necessary logic to end up with an Invoice object on that account.
        //
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", callContext);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final PlanPhaseSpecifier bpPlanPhaseSpecifier = new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null);
        final SubscriptionData bpSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                       bpPlanPhaseSpecifier,
                                                                                                                       null,
                                                                                                                       callContext));
        assertNotNull(bpSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), callContext);
        Assert.assertEquals(invoices.size(), 1);

        final Invoice invoice = invoices.get(0);
        Assert.assertEquals(invoice.getAccountId(), account.getId());

        //
        // Create a new tag definition
        //
        busHandler.pushExpectedEvents(NextEvent.TAG_DEFINITION);
        TagDefinition tagDefinition = tagApi.create("foo", "foo desc", callContext);
        assertTrue(busHandler.isCompleted(DELAY));

        //
        // Add 2 Tags on the invoice (1 control tag and 1 user tag)
        //
        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagApi.addTag(invoice.getId(), ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.getId(), callContext);
        assertTrue(busHandler.isCompleted(DELAY));

        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagApi.addTag(invoice.getId(), ObjectType.INVOICE, tagDefinition.getId(), callContext);
        assertTrue(busHandler.isCompleted(DELAY));

        List<Tag> tags = tagApi.getTagsForAccount(account.getId(), callContext);
        Assert.assertEquals(tags.size(), 2);

        tags = tagApi.getTagsForObject(invoice.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(tags.size(), 2);

        tags = tagApi.getTagsForAccountType(account.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(tags.size(), 2);


        //
        // Add a tag on the account itself and retry
        //
        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_PAY_OFF.getId(), callContext);
        assertTrue(busHandler.isCompleted(DELAY));

        tags = tagApi.getTagsForAccount(account.getId(), callContext);
        Assert.assertEquals(tags.size(), 3);

        tags = tagApi.getTagsForObject(invoice.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(tags.size(), 2);

        tags = tagApi.getTagsForAccountType(account.getId(), ObjectType.INVOICE, callContext);
        Assert.assertEquals(tags.size(), 2);


    }


}
