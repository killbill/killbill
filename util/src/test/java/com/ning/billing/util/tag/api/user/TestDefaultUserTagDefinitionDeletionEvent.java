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

import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.TagDefinition;

public class TestDefaultUserTagDefinitionDeletionEvent {
    @Test(groups = "fast")
    public void testPojo() throws Exception {
        final UUID tagDefinitionId = UUID.randomUUID();
        final String tagDefinitionName = UUID.randomUUID().toString();
        final String tagDefinitionDescription = UUID.randomUUID().toString();
        final boolean controlTag = true;
        final TagDefinition tagDefinition = new DefaultTagDefinition(tagDefinitionId, tagDefinitionName, tagDefinitionDescription, controlTag);
        final UUID userToken = UUID.randomUUID();

        final DefaultUserTagDefinitionDeletionEvent event = new DefaultUserTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken);
        Assert.assertEquals(event.getBusEventType(), BusEvent.BusEventType.USER_TAGDEFINITION_DELETION);

        Assert.assertEquals(event.getTagDefinitionId(), tagDefinitionId);
        Assert.assertEquals(event.getTagDefinition(), tagDefinition);
        Assert.assertEquals(event.getTagDefinition().getId(), tagDefinitionId);
        Assert.assertEquals(event.getTagDefinition().getName(), tagDefinitionName);
        Assert.assertEquals(event.getTagDefinition().getDescription(), tagDefinitionDescription);
        Assert.assertEquals(event.getUserToken(), userToken);

        Assert.assertEquals(event, event);
        Assert.assertEquals(event, new DefaultUserTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken));
        Assert.assertTrue(event.equals(event));
        Assert.assertTrue(event.equals(new DefaultUserTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken)));
    }
}
