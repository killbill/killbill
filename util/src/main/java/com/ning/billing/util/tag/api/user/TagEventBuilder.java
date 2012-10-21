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

import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.events.ControlTagCreationInternalEvent;
import com.ning.billing.util.events.ControlTagDefinitionCreationInternalEvent;
import com.ning.billing.util.events.ControlTagDefinitionDeletionInternalEvent;
import com.ning.billing.util.events.ControlTagDeletionInternalEvent;
import com.ning.billing.util.events.UserTagCreationInternalEvent;
import com.ning.billing.util.events.UserTagDefinitionCreationInternalEvent;
import com.ning.billing.util.events.UserTagDefinitionDeletionInternalEvent;
import com.ning.billing.util.events.UserTagDeletionInternalEvent;
import com.ning.billing.util.tag.TagDefinition;

public class TagEventBuilder {
    public UserTagDefinitionCreationInternalEvent newUserTagDefinitionCreationEvent(final UUID tagDefinitionId, final TagDefinition tagDefinition, final UUID userToken) {
        return new DefaultUserTagDefinitionCreationEvent(tagDefinitionId, tagDefinition, userToken);
    }

    public UserTagDefinitionDeletionInternalEvent newUserTagDefinitionDeletionEvent(final UUID tagDefinitionId, final TagDefinition tagDefinition, final UUID userToken) {
        return new DefaultUserTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken);
    }

    public ControlTagDefinitionCreationInternalEvent newControlTagDefinitionCreationEvent(final UUID tagDefinitionId, final TagDefinition tagDefinition, final UUID userToken) {
        return new DefaultControlTagDefinitionCreationEvent(tagDefinitionId, tagDefinition, userToken);
    }

    public ControlTagDefinitionDeletionInternalEvent newControlTagDefinitionDeletionEvent(final UUID tagDefinitionId, final TagDefinition tagDefinition, final UUID userToken) {
        return new DefaultControlTagDefinitionDeletionEvent(tagDefinitionId, tagDefinition, userToken);
    }

    public UserTagCreationInternalEvent newUserTagCreationEvent(final UUID tagId, final UUID objectId, final ObjectType objectType, final TagDefinition tagDefinition, final UUID userToken) {
        return new DefaultUserTagCreationEvent(tagId, objectId, objectType, tagDefinition, userToken);
    }

    public UserTagDeletionInternalEvent newUserTagDeletionEvent(final UUID tagId, final UUID objectId, final ObjectType objectType, final TagDefinition tagDefinition, final UUID userToken) {
        return new DefaultUserTagDeletionEvent(tagId, objectId, objectType, tagDefinition, userToken);
    }

    public ControlTagCreationInternalEvent newControlTagCreationEvent(final UUID tagId, final UUID objectId, final ObjectType objectType, final TagDefinition tagDefinition, final UUID userToken) {
        return new DefaultControlTagCreationEvent(tagId, objectId, objectType, tagDefinition, userToken);
    }

    public ControlTagDeletionInternalEvent newControlTagDeletionEvent(final UUID tagId, final UUID objectId, final ObjectType objectType, final TagDefinition tagDefinition, final UUID userToken) {
        return new DefaultControlTagDeletionEvent(tagId, objectId, objectType, tagDefinition, userToken);
    }
}
