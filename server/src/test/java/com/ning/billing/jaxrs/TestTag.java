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

package com.ning.billing.jaxrs;

import java.util.List;

import org.testng.annotations.Test;

import com.ning.billing.client.KillBillClientException;
import com.ning.billing.client.model.TagDefinition;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestTag extends TestJaxrsBase {

    @Test(groups = "slow", description = "Cannot add badly formatted TagDefinition")
    public void testTagErrorHandling() throws Exception {
        final TagDefinition[] tagDefinitions = {new TagDefinition(null, false, null, null, null),
                                                new TagDefinition(null, false, "something", null, null),
                                                new TagDefinition(null, false, null, "something", null)};

        for (final TagDefinition tagDefinition : tagDefinitions) {
            try {
                killBillClient.createTagDefinition(tagDefinition, createdBy, reason, comment);
            } catch (final KillBillClientException e) {
            }
        }
    }

    @Test(groups = "slow", description = "Can create a TagDefinition")
    public void testTagDefinitionOk() throws Exception {
        final TagDefinition input = new TagDefinition(null, false, "blue", "relaxing color", ImmutableList.<String>of());

        final TagDefinition objFromJson = killBillClient.createTagDefinition(input, createdBy, reason, comment);
        assertNotNull(objFromJson);
        assertEquals(objFromJson.getName(), input.getName());
        assertEquals(objFromJson.getDescription(), input.getDescription());
    }

    @Test(groups = "slow", description = "Can create and delete TagDefinitions")
    public void testMultipleTagDefinitionOk() throws Exception {
        List<TagDefinition> objFromJson = killBillClient.getTagDefinitions();
        final int sizeSystemTag = objFromJson.isEmpty() ? 0 : objFromJson.size();

        final TagDefinition inputBlue = new TagDefinition(null, false, "blue", "relaxing color", ImmutableList.<String>of());
        killBillClient.createTagDefinition(inputBlue, createdBy, reason, comment);

        final TagDefinition inputRed = new TagDefinition(null, false, "red", "hot color", ImmutableList.<String>of());
        killBillClient.createTagDefinition(inputRed, createdBy, reason, comment);

        final TagDefinition inputYellow = new TagDefinition(null, false, "yellow", "vibrant color", ImmutableList.<String>of());
        killBillClient.createTagDefinition(inputYellow, createdBy, reason, comment);

        final TagDefinition inputGreen = new TagDefinition(null, false, "green", "super relaxing color", ImmutableList.<String>of());
        killBillClient.createTagDefinition(inputGreen, createdBy, reason, comment);

        objFromJson = killBillClient.getTagDefinitions();
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 4 + sizeSystemTag);

        killBillClient.deleteTagDefinition(objFromJson.get(0).getId(), createdBy, reason, comment);

        objFromJson = killBillClient.getTagDefinitions();
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 3 + sizeSystemTag);
    }
}
