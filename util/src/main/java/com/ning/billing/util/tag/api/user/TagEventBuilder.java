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

import com.ning.billing.ObjectType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.events.ControlTagCreationInternalEvent;
import com.ning.billing.util.events.ControlTagDefinitionCreationInternalEvent;
import com.ning.billing.util.events.ControlTagDefinitionDeletionInternalEvent;
import com.ning.billing.util.events.ControlTagDeletionInternalEvent;
import com.ning.billing.util.events.UserTagCreationInternalEvent;
import com.ning.billing.util.events.UserTagDefinitionCreationInternalEvent;
import com.ning.billing.util.events.UserTagDefinitionDeletionInternalEvent;
import com.ning.billing.util.events.UserTagDeletionInternalEvent;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.dao.TagDefinitionModelDao;

public class TagEventBuilder {

    public UserTagDefinitionCreationInternalEvent newUserTagDefinitionCreationEvent(final UUID tagDefinitionId, final TagDefinitionModelDao tagDefinition, final Long searchKey1, final Long searchKey2, final UUID userToken) {
        return new DefaultUserTagDefinitionCreationEvent(tagDefinitionId, new DefaultTagDefinition(tagDefinition, false), searchKey1, searchKey2, userToken);
    }

    public UserTagDefinitionDeletionInternalEvent newUserTagDefinitionDeletionEvent(final UUID tagDefinitionId, final TagDefinitionModelDao tagDefinition, final Long searchKey1, final Long searchKey2, final UUID userToken) {
        return new DefaultUserTagDefinitionDeletionEvent(tagDefinitionId, new DefaultTagDefinition(tagDefinition, false), searchKey1, searchKey2, userToken);
    }

    public ControlTagDefinitionCreationInternalEvent newControlTagDefinitionCreationEvent(final UUID tagDefinitionId, final TagDefinitionModelDao tagDefinition, final Long searchKey1, final Long searchKey2, final UUID userToken) {
        return new DefaultControlTagDefinitionCreationEvent(tagDefinitionId, new DefaultTagDefinition(tagDefinition, true), searchKey1, searchKey2, userToken);
    }

    public ControlTagDefinitionDeletionInternalEvent newControlTagDefinitionDeletionEvent(final UUID tagDefinitionId, final TagDefinitionModelDao tagDefinition, final Long searchKey1, final Long searchKey2, final UUID userToken) {
        return new DefaultControlTagDefinitionDeletionEvent(tagDefinitionId, new DefaultTagDefinition(tagDefinition, true), searchKey1, searchKey2, userToken);
    }

    public UserTagCreationInternalEvent newUserTagCreationEvent(final UUID tagId, final UUID objectId, final ObjectType objectType, final TagDefinitionModelDao tagDefinition, final Long searchKey1, final Long searchKey2, final UUID userToken) {
        return new DefaultUserTagCreationEvent(tagId, objectId, objectType, new DefaultTagDefinition(tagDefinition, false), searchKey1, searchKey2, userToken);
    }

    public UserTagDeletionInternalEvent newUserTagDeletionEvent(final UUID tagId, final UUID objectId, final ObjectType objectType, final TagDefinitionModelDao tagDefinition, final Long searchKey1, final Long searchKey2, final UUID userToken) {
        return new DefaultUserTagDeletionEvent(tagId, objectId, objectType, new DefaultTagDefinition(tagDefinition, false), searchKey1, searchKey2, userToken);
    }

    public ControlTagCreationInternalEvent newControlTagCreationEvent(final UUID tagId, final UUID objectId, final ObjectType objectType, final TagDefinitionModelDao tagDefinition, final Long searchKey1, final Long searchKey2, final UUID userToken) {
        return new DefaultControlTagCreationEvent(tagId, objectId, objectType, new DefaultTagDefinition(tagDefinition, true), searchKey1, searchKey2, userToken);
    }

    public ControlTagDeletionInternalEvent newControlTagDeletionEvent(final UUID tagId, final UUID objectId, final ObjectType objectType, final TagDefinitionModelDao tagDefinition, final Long searchKey1, final Long searchKey2, final UUID userToken) {
        return new DefaultControlTagDeletionEvent(tagId, objectId, objectType, new DefaultTagDefinition(tagDefinition, true), searchKey1, searchKey2, userToken);
    }
}
