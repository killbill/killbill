/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.tag.api.user;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.api.ControlTagCreationEvent;
import com.ning.billing.util.tag.api.ControlTagDefinitionCreationEvent;
import com.ning.billing.util.tag.api.ControlTagDefinitionDeletionEvent;
import com.ning.billing.util.tag.api.ControlTagDeletionEvent;
import com.ning.billing.util.tag.api.TagDefinitionEvent;
import com.ning.billing.util.tag.api.TagEvent;
import com.ning.billing.util.tag.api.UserTagCreationEvent;
import com.ning.billing.util.tag.api.UserTagDefinitionCreationEvent;
import com.ning.billing.util.tag.api.UserTagDefinitionDeletionEvent;
import com.ning.billing.util.tag.api.UserTagDeletionEvent;

public class TestTagEventBuilder {
    @Test(groups = "fast")
    public void testNewUserTagDefinitionCreationEvent() throws Exception {
        final UUID tagDefinitionId = UUID.randomUUID();
        final String tagDefinitionName = UUID.randomUUID().toString();
        final String tagDefinitionDescription = UUID.randomUUID().toString();
        final boolean controlTag = true;
        final TagDefinition tagDefinition = new DefaultTagDefinition(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, controlTag);
        final UUID userToken = UUID.randomUUID();

        final TagEventBuilder tagEventBuilder = new TagEventBuilder();
        final TagDefinitionEvent event = tagEventBuilder.newUserTagDefinitionCreationEvent(tagDefinitionId, tagDefinition, userToken);
        Assert.assertTrue(event instanceof UserTagDefinitionCreationEvent);

        Assert.assertEquals(event, new DefaultUserTagDefinitionCreationEvent(tagDefinitionId, tagDefinition, userToken));
        Assert.assertTrue(event.equals(new DefaultUserTagDefinitionCreationEvent(tagDefinitionId, tagDefinition, userToken)));

        verifyTagDefinitionEvent(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, tagDefinition, userToken, event);
    }

    @Test(groups = "fast")
    public void testNewUserTagDefinitionDeletionEvent() throws Exception {
        final UUID tagDefinitionId = UUID.randomUUID();
        final String tagDefinitionName = UUID.randomUUID().toString();
        final String tagDefinitionDescription = UUID.randomUUID().toString();
        final boolean controlTag = true;
        final TagDefinition tagDefinition = new DefaultTagDefinition(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, controlTag);
        final UUID userToken = UUID.randomUUID();

        final TagEventBuilder tagEventBuilder = new TagEventBuilder();
        final TagDefinitionEvent event = tagEventBuilder.newUserTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken);
        Assert.assertTrue(event instanceof UserTagDefinitionDeletionEvent);

        Assert.assertEquals(event, new DefaultUserTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken));
        Assert.assertTrue(event.equals(new DefaultUserTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken)));

        verifyTagDefinitionEvent(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, tagDefinition, userToken, event);
    }

    @Test(groups = "fast")
    public void testNewControlTagDefinitionCreationEvent() throws Exception {
        final UUID tagDefinitionId = UUID.randomUUID();
        final String tagDefinitionName = UUID.randomUUID().toString();
        final String tagDefinitionDescription = UUID.randomUUID().toString();
        final boolean controlTag = true;
        final TagDefinition tagDefinition = new DefaultTagDefinition(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, controlTag);
        final UUID userToken = UUID.randomUUID();

        final TagEventBuilder tagEventBuilder = new TagEventBuilder();
        final TagDefinitionEvent event = tagEventBuilder.newControlTagDefinitionCreationEvent(tagDefinitionId, tagDefinition, userToken);
        Assert.assertTrue(event instanceof ControlTagDefinitionCreationEvent);

        Assert.assertEquals(event, new DefaultControlTagDefinitionCreationEvent(tagDefinitionId, tagDefinition, userToken));
        Assert.assertTrue(event.equals(new DefaultControlTagDefinitionCreationEvent(tagDefinitionId, tagDefinition, userToken)));

        verifyTagDefinitionEvent(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, tagDefinition, userToken, event);
    }

    @Test(groups = "fast")
    public void testNewControlTagDefinitionDeletionEvent() throws Exception {
        final UUID tagDefinitionId = UUID.randomUUID();
        final String tagDefinitionName = UUID.randomUUID().toString();
        final String tagDefinitionDescription = UUID.randomUUID().toString();
        final boolean controlTag = true;
        final TagDefinition tagDefinition = new DefaultTagDefinition(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, controlTag);
        final UUID userToken = UUID.randomUUID();

        final TagEventBuilder tagEventBuilder = new TagEventBuilder();
        final TagDefinitionEvent event = tagEventBuilder.newControlTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken);
        Assert.assertTrue(event instanceof ControlTagDefinitionDeletionEvent);

        Assert.assertEquals(event, new DefaultControlTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken));
        Assert.assertTrue(event.equals(new DefaultControlTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken)));

        verifyTagDefinitionEvent(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, tagDefinition, userToken, event);
    }

    @Test(groups = "fast")
    public void testNewUserTagCreationEvent() throws Exception {
        final UUID tagId = UUID.randomUUID();
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT_EMAIL;
        final UUID tagDefinitionId = UUID.randomUUID();
        final String tagDefinitionName = UUID.randomUUID().toString();
        final String tagDefinitionDescription = UUID.randomUUID().toString();
        final boolean controlTag = true;
        final TagDefinition tagDefinition = new DefaultTagDefinition(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, controlTag);
        final UUID userToken = UUID.randomUUID();

        final TagEventBuilder tagEventBuilder = new TagEventBuilder();
        final TagEvent event = tagEventBuilder.newUserTagCreationEvent(tagId, objectId, objectType, tagDefinition, userToken);
        Assert.assertTrue(event instanceof UserTagCreationEvent);

        Assert.assertEquals(event, new DefaultUserTagCreationEvent(tagId, objectId, objectType, tagDefinition, userToken));
        Assert.assertTrue(event.equals(new DefaultUserTagCreationEvent(tagId, objectId, objectType, tagDefinition, userToken)));

        verifyTagEvent(tagId, objectId, objectType, tagDefinitionId, tagDefinitionName, tagDefinitionDescription, tagDefinition, userToken, event);
    }

    @Test(groups = "fast")
    public void testNewUserTagDeletionEvent() throws Exception {
        final UUID tagId = UUID.randomUUID();
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT_EMAIL;
        final UUID tagDefinitionId = UUID.randomUUID();
        final String tagDefinitionName = UUID.randomUUID().toString();
        final String tagDefinitionDescription = UUID.randomUUID().toString();
        final boolean controlTag = true;
        final TagDefinition tagDefinition = new DefaultTagDefinition(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, controlTag);
        final UUID userToken = UUID.randomUUID();

        final TagEventBuilder tagEventBuilder = new TagEventBuilder();
        final TagEvent event = tagEventBuilder.newUserTagDeletionEvent(tagId, objectId, objectType, tagDefinition, userToken);
        Assert.assertTrue(event instanceof UserTagDeletionEvent);

        Assert.assertEquals(event, new DefaultUserTagDeletionEvent(tagId, objectId, objectType, tagDefinition, userToken));
        Assert.assertTrue(event.equals(new DefaultUserTagDeletionEvent(tagId, objectId, objectType, tagDefinition, userToken)));

        verifyTagEvent(tagId, objectId, objectType, tagDefinitionId, tagDefinitionName, tagDefinitionDescription, tagDefinition, userToken, event);
    }

    @Test(groups = "fast")
    public void testNewControlTagCreationEvent() throws Exception {
        final UUID tagId = UUID.randomUUID();
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT_EMAIL;
        final UUID tagDefinitionId = UUID.randomUUID();
        final String tagDefinitionName = UUID.randomUUID().toString();
        final String tagDefinitionDescription = UUID.randomUUID().toString();
        final boolean controlTag = true;
        final TagDefinition tagDefinition = new DefaultTagDefinition(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, controlTag);
        final UUID userToken = UUID.randomUUID();

        final TagEventBuilder tagEventBuilder = new TagEventBuilder();
        final TagEvent event = tagEventBuilder.newControlTagCreationEvent(tagId, objectId, objectType, tagDefinition, userToken);
        Assert.assertTrue(event instanceof ControlTagCreationEvent);

        Assert.assertEquals(event, new DefaultControlTagCreationEvent(tagId, objectId, objectType, tagDefinition, userToken));
        Assert.assertTrue(event.equals(new DefaultControlTagCreationEvent(tagId, objectId, objectType, tagDefinition, userToken)));

        verifyTagEvent(tagId, objectId, objectType, tagDefinitionId, tagDefinitionName, tagDefinitionDescription, tagDefinition, userToken, event);
    }

    @Test(groups = "fast")
    public void testNewControlTagDeletionEvent() throws Exception {
        final UUID tagId = UUID.randomUUID();
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT_EMAIL;
        final UUID tagDefinitionId = UUID.randomUUID();
        final String tagDefinitionName = UUID.randomUUID().toString();
        final String tagDefinitionDescription = UUID.randomUUID().toString();
        final boolean controlTag = true;
        final TagDefinition tagDefinition = new DefaultTagDefinition(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, controlTag);
        final UUID userToken = UUID.randomUUID();

        final TagEventBuilder tagEventBuilder = new TagEventBuilder();
        final TagEvent event = tagEventBuilder.newControlTagDeletionEvent(tagId, objectId, objectType, tagDefinition, userToken);
        Assert.assertTrue(event instanceof ControlTagDeletionEvent);

        Assert.assertEquals(event, new DefaultControlTagDeletionEvent(tagId, objectId, objectType, tagDefinition, userToken));
        Assert.assertTrue(event.equals(new DefaultControlTagDeletionEvent(tagId, objectId, objectType, tagDefinition, userToken)));

        verifyTagEvent(tagId, objectId, objectType, tagDefinitionId, tagDefinitionName, tagDefinitionDescription, tagDefinition, userToken, event);
    }

    private void verifyTagDefinitionEvent(final UUID tagDefinitionId, final String tagDefinitionName, final String tagDefinitionDescription, final TagDefinition tagDefinition, final UUID userToken, final TagDefinitionEvent event) {
        Assert.assertEquals(event.getTagDefinitionId(), tagDefinitionId);
        Assert.assertEquals(event.getTagDefinition(), tagDefinition);
        Assert.assertEquals(event.getTagDefinition().getId(), tagDefinitionId);
        Assert.assertEquals(event.getTagDefinition().getName(), tagDefinitionName);
        Assert.assertEquals(event.getTagDefinition().getDescription(), tagDefinitionDescription);
        Assert.assertEquals(event.getUserToken(), userToken);

        Assert.assertEquals(event, event);
        Assert.assertTrue(event.equals(event));
    }

    private void verifyTagEvent(final UUID tagId, final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final String tagDefinitionName, final String tagDefinitionDescription, final TagDefinition tagDefinition, final UUID userToken, final TagEvent event) {
        Assert.assertEquals(event.getTagId(), tagId);
        Assert.assertEquals(event.getObjectId(), objectId);
        Assert.assertEquals(event.getObjectType(), objectType);
        Assert.assertEquals(event.getTagDefinition(), tagDefinition);
        Assert.assertEquals(event.getTagDefinition().getId(), tagDefinitionId);
        Assert.assertEquals(event.getTagDefinition().getName(), tagDefinitionName);
        Assert.assertEquals(event.getTagDefinition().getDescription(), tagDefinitionDescription);
        Assert.assertEquals(event.getUserToken(), userToken);

        Assert.assertEquals(event, event);
        Assert.assertTrue(event.equals(event));
    }
}
