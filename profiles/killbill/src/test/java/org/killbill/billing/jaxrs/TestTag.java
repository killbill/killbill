/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Subscription;
import org.killbill.billing.client.model.Tag;
import org.killbill.billing.client.model.TagDefinition;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.tag.ControlTagType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestTag extends TestJaxrsBase {

    @Test(groups = "slow", description = "Cannot add badly formatted TagDefinition")
    public void testTagErrorHandling() throws Exception {
        final TagDefinition[] tagDefinitions = {new TagDefinition(null, false, null, null, null),
                                                new TagDefinition(null, false, "something", null, null),
                                                new TagDefinition(null, false, null, "something", null)};

        for (final TagDefinition tagDefinition : tagDefinitions) {
            try {
                killBillClient.createTagDefinition(tagDefinition, createdBy, reason, comment);
                fail();
            } catch (final KillBillClientException e) {
            }
        }
    }

    @Test(groups = "slow", description = "Can create a TagDefinition")
    public void testTagDefinitionOk() throws Exception {
        final TagDefinition input = new TagDefinition(null, false, "blue", "relaxing color", ImmutableList.<ObjectType>of());

        final TagDefinition objFromJson = killBillClient.createTagDefinition(input, createdBy, reason, comment);
        assertNotNull(objFromJson);
        assertEquals(objFromJson.getName(), input.getName());
        assertEquals(objFromJson.getDescription(), input.getDescription());
    }

    @Test(groups = "slow", description = "Can create and delete TagDefinitions")
    public void testMultipleTagDefinitionOk() throws Exception {
        List<TagDefinition> objFromJson = killBillClient.getTagDefinitions();
        final int sizeSystemTag = objFromJson.isEmpty() ? 0 : objFromJson.size();

        final TagDefinition inputBlue = new TagDefinition(null, false, "blue", "relaxing color", ImmutableList.<ObjectType>of());
        killBillClient.createTagDefinition(inputBlue, createdBy, reason, comment);

        final TagDefinition inputRed = new TagDefinition(null, false, "red", "hot color", ImmutableList.<ObjectType>of());
        killBillClient.createTagDefinition(inputRed, createdBy, reason, comment);

        final TagDefinition inputYellow = new TagDefinition(null, false, "yellow", "vibrant color", ImmutableList.<ObjectType>of());
        killBillClient.createTagDefinition(inputYellow, createdBy, reason, comment);

        final TagDefinition inputGreen = new TagDefinition(null, false, "green", "super relaxing color", ImmutableList.<ObjectType>of());
        killBillClient.createTagDefinition(inputGreen, createdBy, reason, comment);

        objFromJson = killBillClient.getTagDefinitions();
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 4 + sizeSystemTag);

        killBillClient.deleteTagDefinition(objFromJson.get(0).getId(), createdBy, reason, comment);

        objFromJson = killBillClient.getTagDefinitions();
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 3 + sizeSystemTag);
    }


    @Test(groups = "slow", description = "Can search all tags for an account")
    public void testGetAllTagsByType() throws Exception {

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithDefaultPaymentMethod();

        final Subscription subscriptionJson = createEntitlement(account.getAccountId(), "87544332", "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY, true);

        for (final ControlTagType controlTagType : ControlTagType.values()) {
            killBillClient.createAccountTag(account.getAccountId(), controlTagType.getId(), createdBy, reason, comment);
        }

        final TagDefinition bundleTagDefInput = new TagDefinition(null, false, "bundletagdef", "nothing special", ImmutableList.<ObjectType>of());
        final TagDefinition bundleTagDef = killBillClient.createTagDefinition(bundleTagDefInput, createdBy, reason, comment);

        killBillClient.createBundleTag(subscriptionJson.getBundleId(), bundleTagDef.getId(), createdBy, reason, comment);

        final Tags allBundleTags = killBillClient.getBundleTags(subscriptionJson.getBundleId(), AuditLevel.FULL);
        Assert.assertEquals(allBundleTags.size(), 1);

        final Tags allAccountTags = killBillClient.getAllAccountTags(account.getAccountId(), null, AuditLevel.FULL);
        Assert.assertEquals(allAccountTags.size(), ControlTagType.values().length + 1);


        final Tags allBundleTagsForAccount = killBillClient.getAllAccountTags(account.getAccountId(), ObjectType.BUNDLE.name(), AuditLevel.FULL);
        Assert.assertEquals(allBundleTagsForAccount.size(), 1);
    }


    @Test(groups = "slow", description = "Can search system tags")
    public void testSystemTagsPagination() throws Exception {
        final Account account = createAccount();
        for (final ControlTagType controlTagType : ControlTagType.values()) {
            killBillClient.createAccountTag(account.getAccountId(), controlTagType.getId(), createdBy, reason, comment);
        }

        final Tags allTags = killBillClient.getTags();
        Assert.assertEquals(allTags.size(), ControlTagType.values().length);

        for (final ControlTagType controlTagType : ControlTagType.values()) {
            Assert.assertEquals(killBillClient.searchTags(controlTagType.toString()).size(), 1);
            Assert.assertEquals(killBillClient.searchTags(controlTagType.getDescription()).size(), 1);
        }
    }

    @Test(groups = "slow", description = "Can paginate through all tags")
    public void testTagsPagination() throws Exception {
        final Account account = createAccount();
        for (int i = 0; i < 5; i++) {
            final TagDefinition tagDefinition = new TagDefinition(null, false, UUID.randomUUID().toString().substring(0, 5), UUID.randomUUID().toString(), ImmutableList.<ObjectType>of(ObjectType.ACCOUNT));
            final UUID tagDefinitionId = killBillClient.createTagDefinition(tagDefinition, createdBy, reason, comment).getId();
            killBillClient.createAccountTag(account.getAccountId(), tagDefinitionId, createdBy, reason, comment);
        }

        final Tags allTags = killBillClient.getTags();
        Assert.assertEquals(allTags.size(), 5);

        Tags page = killBillClient.getTags(0L, 1L);
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(page);
            Assert.assertEquals(page.size(), 1);
            Assert.assertEquals(page.get(0), allTags.get(i));
            page = page.getNext();
        }
        Assert.assertNull(page);

        for (final Tag tag : allTags) {
            doSearchTag(UUID.randomUUID().toString(), null);
            doSearchTag(tag.getTagId().toString(), tag);
            doSearchTag(tag.getTagDefinitionName(), tag);

            final TagDefinition tagDefinition = killBillClient.getTagDefinition(tag.getTagDefinitionId());
            doSearchTag(tagDefinition.getDescription(), tag);
        }

        final Tags tags = killBillClient.searchTags(ObjectType.ACCOUNT.toString());
        Assert.assertEquals(tags.size(), 5);
        Assert.assertEquals(tags.getPaginationCurrentOffset(), 0);
        Assert.assertEquals(tags.getPaginationTotalNbRecords(), 5);
        Assert.assertEquals(tags.getPaginationMaxNbRecords(), 5);
    }

    private void doSearchTag(final String searchKey, @Nullable final Tag expectedTag) throws KillBillClientException {
        final Tags tags = killBillClient.searchTags(searchKey);
        if (expectedTag == null) {
            Assert.assertTrue(tags.isEmpty());
            Assert.assertEquals(tags.getPaginationCurrentOffset(), 0);
            Assert.assertEquals(tags.getPaginationTotalNbRecords(), 0);
            Assert.assertEquals(tags.getPaginationMaxNbRecords(), 5);
        } else {
            Assert.assertEquals(tags.size(), 1);
            Assert.assertEquals(tags.get(0), expectedTag);
            Assert.assertEquals(tags.getPaginationCurrentOffset(), 0);
            Assert.assertEquals(tags.getPaginationTotalNbRecords(), 1);
            Assert.assertEquals(tags.getPaginationMaxNbRecords(), 5);
        }
    }
}
