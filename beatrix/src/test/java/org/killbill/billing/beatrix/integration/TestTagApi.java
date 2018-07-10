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

package org.killbill.billing.beatrix.integration;

import java.util.Collection;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.TagDefinition;

import com.google.common.collect.ImmutableSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestTagApi extends TestIntegrationBase {

    private Account account;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        account = createAccountWithNonOsgiPaymentMethod(getAccountData(25));
        assertNotNull(account);
    }

    @Test(groups = "slow")
    public void testApiTagOnAccount() throws Exception {
        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_PAY_OFF.getId(), callContext);
        assertListenerStatus();

        List<Tag> tags = tagUserApi.getTagsForAccount(account.getId(), false, callContext);
        Assert.assertEquals(tags.size(), 2);
        checkTagsExists(tags);

        tags = tagUserApi.getTagsForObject(account.getId(), ObjectType.ACCOUNT, false, callContext);
        Assert.assertEquals(tags.size(), 2);
        checkTagsExists(tags);

        tags = tagUserApi.getTagsForAccountType(account.getId(), ObjectType.ACCOUNT, false, callContext);
        Assert.assertEquals(tags.size(), 2);
        checkTagsExists(tags);
    }

    @Test(groups = "slow")
    public void testApiTagOnInvoice() throws Exception {
        //
        // Create necessary logic to end up with an Invoice object on that account.
        //

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", productName, ProductCategory.BASE, term, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        Assert.assertEquals(invoices.size(), 1);

        final Invoice invoice = invoices.get(0);
        Assert.assertEquals(invoice.getAccountId(), account.getId());

        //
        // Create a new tag definition
        //
        busHandler.pushExpectedEvents(NextEvent.TAG_DEFINITION);
        final TagDefinition tagDefinition = tagUserApi.createTagDefinition("foo", "foo desc", ImmutableSet.<ObjectType>of(ObjectType.ACCOUNT, ObjectType.INVOICE), callContext);
        assertListenerStatus();

        final TagDefinition tagDefinition2 = tagUserApi.getTagDefinition(tagDefinition.getId(), callContext);
        assertEquals(tagDefinition2.getApplicableObjectTypes().size(), 2);
        assertEquals(tagDefinition2.getApplicableObjectTypes().get(0), ObjectType.ACCOUNT);
        assertEquals(tagDefinition2.getApplicableObjectTypes().get(1), ObjectType.INVOICE);

        //
        // Add 2 Tags on the invoice (1 invoice tag and 1 user tag)
        //
        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagUserApi.addTag(invoice.getId(), ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.getId(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagUserApi.addTag(invoice.getId(), ObjectType.INVOICE, tagDefinition.getId(), callContext);
        assertListenerStatus();

        List<Tag> tags = tagUserApi.getTagsForAccount(account.getId(), false, callContext);
        Assert.assertEquals(tags.size(), 2);
        checkTagsExists(tags);

        tags = tagUserApi.getTagsForObject(invoice.getId(), ObjectType.INVOICE, false, callContext);
        Assert.assertEquals(tags.size(), 2);
        checkTagsExists(tags);

        tags = tagUserApi.getTagsForAccountType(account.getId(), ObjectType.INVOICE, false, callContext);
        Assert.assertEquals(tags.size(), 2);
        checkTagsExists(tags);

        //
        // Add a tag on the account itself and retry
        //
        busHandler.pushExpectedEvents(NextEvent.TAG);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_PAY_OFF.getId(), callContext);
        assertListenerStatus();

        tags = tagUserApi.getTagsForAccount(account.getId(), false, callContext);
        Assert.assertEquals(tags.size(), 3);
        checkTagsExists(tags);

        tags = tagUserApi.getTagsForObject(invoice.getId(), ObjectType.INVOICE, false, callContext);
        Assert.assertEquals(tags.size(), 2);
        checkTagsExists(tags);

        tags = tagUserApi.getTagsForAccountType(account.getId(), ObjectType.INVOICE, false, callContext);
        Assert.assertEquals(tags.size(), 2);
        checkTagsExists(tags);
    }

    private void checkTagsExists(final Collection<Tag> tags) {
        for (final Tag tag : tags) {
            Assert.assertNotNull(recordIdApi.getRecordId(tag.getId(), ObjectType.TAG, callContext));
        }
    }
}
