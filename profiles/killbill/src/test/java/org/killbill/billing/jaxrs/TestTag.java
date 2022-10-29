/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.ObjectType;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.UTF8UrlEncoder;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AuditLog;
import org.killbill.billing.client.model.gen.Subscription;
import org.killbill.billing.client.model.gen.Tag;
import org.killbill.billing.client.model.gen.TagDefinition;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.dao.SystemTags;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestTag extends TestJaxrsBase {

    @Test(groups = "slow", description = "Cannot add badly formatted TagDefinition")
    public void testTagErrorHandling() throws Exception {
        final TagDefinition[] tagDefinitions = {new TagDefinition(null, false, null, null, List.of(ObjectType.ACCOUNT), null),
                                                new TagDefinition(null, false, "something", null, List.of(ObjectType.INVOICE), null),
                                                new TagDefinition(null, false, null, "something", List.of(ObjectType.TRANSACTION), null)};

        for (final TagDefinition tagDefinition : tagDefinitions) {
            try {
                tagDefinitionApi.createTagDefinition(tagDefinition, requestOptions);
                fail();
            } catch (final KillBillClientException e) {
            }
        }
    }

    @Test(groups = "slow", description = "Can create a TagDefinition")
    public void testTagDefinitionOk() throws Exception {
        final TagDefinition input = new TagDefinition(null, false, "blue", "relaxing color", List.of(ObjectType.TRANSACTION), null);

        final TagDefinition objFromJson = tagDefinitionApi.createTagDefinition(input, requestOptions);
        assertNotNull(objFromJson);
        assertEquals(objFromJson.getName(), input.getName());
        assertEquals(objFromJson.getDescription(), input.getDescription());
    }

    @Test(groups = "slow", description = "Can create and delete TagDefinitions")
    public void testMultipleTagDefinitionOk() throws Exception {
        List<TagDefinition> objFromJson = tagDefinitionApi.getTagDefinitions(requestOptions);
        final int sizeSystemTag = objFromJson.isEmpty() ? 0 : objFromJson.size();

        final TagDefinition inputBlue = new TagDefinition(null, false, "blue", "relaxing color", List.of(ObjectType.TRANSACTION), null);
        tagDefinitionApi.createTagDefinition(inputBlue, requestOptions);

        final TagDefinition inputRed = new TagDefinition(null, false, "red", "hot color", List.of(ObjectType.TRANSACTION), null);
        tagDefinitionApi.createTagDefinition(inputRed, requestOptions);

        final TagDefinition inputYellow = new TagDefinition(null, false, "yellow", "vibrant color", List.of(ObjectType.TRANSACTION), null);
        tagDefinitionApi.createTagDefinition(inputYellow, requestOptions);

        final TagDefinition inputGreen = new TagDefinition(null, false, "green", "super relaxing color", List.of(ObjectType.TRANSACTION), null);
        tagDefinitionApi.createTagDefinition(inputGreen, requestOptions);

        objFromJson = tagDefinitionApi.getTagDefinitions(requestOptions);
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 4 + sizeSystemTag);

        tagDefinitionApi.deleteTagDefinition(objFromJson.get(0).getId(), requestOptions);

        objFromJson = tagDefinitionApi.getTagDefinitions(requestOptions);
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 3 + sizeSystemTag);
    }

    @Test(groups = "slow", description = "Can search all tags for an account")
    public void testGetAllTagsByType() throws Exception {

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithDefaultPaymentMethod();

        final Subscription subscriptionJson = createSubscription(account.getAccountId(), "87544332", "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY);

        int nbAllowedControlTagType = 0;
        for (final ControlTagType controlTagType : ControlTagType.values()) {
            if (controlTagType.getApplicableObjectTypes().contains(ObjectType.ACCOUNT)) {
                accountApi.createAccountTags(account.getAccountId(), List.of(controlTagType.getId()), requestOptions);
                nbAllowedControlTagType++;
            }
        }

        final TagDefinition bundleTagDefInput = new TagDefinition(null, false, "bundletagdef", "nothing special", List.of(ObjectType.TRANSACTION), null);
        final TagDefinition bundleTagDef = tagDefinitionApi.createTagDefinition(bundleTagDefInput, requestOptions);

        bundleApi.createBundleTags(subscriptionJson.getBundleId(), List.of(bundleTagDef.getId()), requestOptions);

        final Tags allBundleTags = bundleApi.getBundleTags(subscriptionJson.getBundleId(), requestOptions);
        Assert.assertEquals(allBundleTags.size(), 1);

        final Tags allAccountTags = accountApi.getAllTags(account.getAccountId(), null, requestOptions);
        Assert.assertEquals(allAccountTags.size(), nbAllowedControlTagType + 1);

        final Tags allBundleTagsForAccount = accountApi.getAllTags(account.getAccountId(), ObjectType.BUNDLE, requestOptions);
        Assert.assertEquals(allBundleTagsForAccount.size(), 1);
    }

    @Test(groups = "slow", description = "Can search system tags")
    public void testSystemTagsPagination() throws Exception {
        final Account account = createAccount();

        int nbAllowedControlTagType = 0;
        for (final ControlTagType controlTagType : ControlTagType.values()) {
            if (controlTagType.getApplicableObjectTypes().contains(ObjectType.ACCOUNT)) {
                accountApi.createAccountTags(account.getAccountId(), List.of(controlTagType.getId()), requestOptions);
                nbAllowedControlTagType++;
            }
        }

        final Tags allTags = accountApi.getAccountTags(account.getAccountId(), requestOptions);
        Assert.assertEquals(allTags.size(), nbAllowedControlTagType);

        for (final ControlTagType controlTagType : ControlTagType.values()) {
            if (controlTagType.getApplicableObjectTypes().contains(ObjectType.ACCOUNT)) {
                Assert.assertEquals(tagApi.searchTags(controlTagType.toString(), requestOptions).size(), 1);
                // TODO Hack until we fix client api
                Assert.assertEquals(tagApi.searchTags(UTF8UrlEncoder.encode(controlTagType.getDescription()), requestOptions).size(), 1);
            }
        }
    }

    @Test(groups = "slow", description = "Can create a TagDefinition")
    public void testNotAllowedSystemTag() throws Exception {

        final Account account = createAccount();

        try {
            accountApi.createAccountTags(account.getAccountId(), List.of(SystemTags.PARK_TAG_DEFINITION_ID), requestOptions);
            Assert.fail("Creating a tag associated with a system tag should fail");
        } catch (final Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test(groups = "slow", description = "Cannot create a control tag against wrong object type")
    public void testNotApplicableType() throws Exception {

        final Account account = createAccount();
        try {
            accountApi.createAccountTags(account.getAccountId(), List.of(ControlTagType.WRITTEN_OFF.getId()), requestOptions);
            Assert.fail("Creating a (control) tag against a wrong object type should fail");
        } catch (final Exception e) {
            Assert.assertTrue(true);
        }
    }



    @Test(groups = "slow", description = "retrieve account logs")
    public void testGetTagAuditLogsWithHistory() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);
        final TagDefinition accountTagDefInput = new TagDefinition()
                .setName("tag_name")
                .setDescription("nothing special")
                .setApplicableObjectTypes(List.of(ObjectType.ACCOUNT));

        final TagDefinition accountTagDef = tagDefinitionApi.createTagDefinition(accountTagDefInput, requestOptions);
        accountApi.createAccountTags(accountJson.getAccountId(), List.of(accountTagDef.getId()), requestOptions);

        // get all audit for the account
        final List<AuditLog> auditLogsJson = accountApi.getAccountAuditLogs(accountJson.getAccountId(), requestOptions);
        Assert.assertEquals(auditLogsJson.size(), 2);
        UUID objectId = null;
        for (AuditLog auditLog : auditLogsJson) {
            if (auditLog.getObjectType().equals(ObjectType.TAG)) {
                objectId = auditLog.getObjectId();
                break;
            }
        }
        assertNotNull(objectId);
        final List<AuditLog> tagAuditLogWithHistories = tagApi.getTagAuditLogsWithHistory(objectId, requestOptions);
        assertEquals(tagAuditLogWithHistories.size(), 1);
        assertEquals(tagAuditLogWithHistories.get(0).getChangeType(), ChangeType.INSERT.toString());
        assertEquals(tagAuditLogWithHistories.get(0).getObjectType(), ObjectType.TAG);
        assertEquals(tagAuditLogWithHistories.get(0).getObjectId(), objectId);

        final LinkedHashMap history1 = (LinkedHashMap) tagAuditLogWithHistories.get(0).getHistory();
        assertNotNull(history1);
        assertEquals(history1.get("tagDefinitionId"), accountTagDef.getId().toString());
        assertEquals(history1.get("objectId"), accountJson.getAccountId().toString());
        assertEquals(history1.get("objectType"), ObjectType.ACCOUNT.toString());
    }

    @Test(groups = "slow", description = "Can paginate through all tags")
    public void testTagsPagination() throws Exception {
        final Account account = createAccount();
        for (int i = 0; i < 5; i++) {
            final TagDefinition tagDefinition = new TagDefinition(null, false, "td-" + i, UUID.randomUUID().toString(), List.of(ObjectType.ACCOUNT), null);
            final UUID tagDefinitionId = tagDefinitionApi.createTagDefinition(tagDefinition, requestOptions).getId();
            accountApi.createAccountTags(account.getAccountId(), List.of(tagDefinitionId), requestOptions);
        }

        final Tags allTags = accountApi.getAccountTags(account.getAccountId(), requestOptions);
        Assert.assertEquals(allTags.size(), 5);

        Tags page = tagApi.getTags(0L, 1L, AuditLevel.NONE, requestOptions);
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(page);
            Assert.assertEquals(page.size(), 1);
            final Tag targetTag = page.get(0);

            Assert.assertTrue(allTags.stream().anyMatch(targetTag::equals));

            page = page.getNext();
        }
        Assert.assertNull(page);

        for (final Tag tag : allTags) {
            doSearchTag(UUID.randomUUID().toString(), null);
            doSearchTag(tag.getTagId().toString(), tag);
            doSearchTag(tag.getTagDefinitionName(), tag);

            final TagDefinition tagDefinition = tagDefinitionApi.getTagDefinition(tag.getTagDefinitionId(), requestOptions);
            doSearchTag(tagDefinition.getDescription(), tag);
        }

        final Tags tags = tagApi.searchTags(ObjectType.ACCOUNT.toString(), requestOptions);
        Assert.assertEquals(tags.size(), 5);
        Assert.assertEquals(tags.getPaginationCurrentOffset(), 0);
        Assert.assertEquals(tags.getPaginationTotalNbRecords(), 5);
        Assert.assertEquals(tags.getPaginationMaxNbRecords(), 5);
    }

    private void doSearchTag(final String searchKey, @Nullable final Tag expectedTag) throws KillBillClientException {
        final Tags tags = tagApi.searchTags(searchKey, requestOptions);
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
