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

package com.ning.billing.util.tag.dao;

import javax.annotation.Nullable;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MockTagDao implements TagDao {
    private Map<UUID, List<Tag>> tagStore = new HashMap<UUID, List<Tag>>();

    @Override
    public void saveEntitiesFromTransaction(final Transmogrifier dao, final UUID objectId, final ObjectType objectType,
                                        final List<Tag> tags, final CallContext context) {
        tagStore.put(objectId, tags);
    }

    @Override
    public void saveEntities(UUID objectId, ObjectType objectType, List<Tag> tags, CallContext context) {
        tagStore.put(objectId, tags) ;
    }

    @Override
    public Map<String, Tag> loadEntities(UUID objectId, ObjectType objectType) {
        return getMap(tagStore.get(objectId));
    }

    @Override
    public Map<String, Tag> loadEntitiesFromTransaction(Transmogrifier dao, UUID objectId, ObjectType objectType) {
        return getMap(tagStore.get(objectId));
    }

    private Map<String, Tag> getMap(@Nullable final List<Tag> tags) {
        Map<String, Tag> map = new HashMap<String, Tag>();
        if (tags != null) {
            for (Tag tag : tags) {
                map.put(tag.getTagDefinitionName(), tag);
            }
        }
        return map;
    }

    @Override
    public void insertTag(final UUID objectId, final ObjectType objectType,
                          final TagDefinition tagDefinition, final CallContext context) {
        Tag tag = new Tag() {
            private UUID id = UUID.randomUUID();

            @Override
            public String getTagDefinitionName() {
                return tagDefinition.getName();
            }

            @Override
            public UUID getId() {
                return id;
            }
        };

        if (tagStore.get(objectId) == null) {
            tagStore.put(objectId, new ArrayList<Tag>());
        }

        tagStore.get(objectId).add(tag);
    }

    @Override
    public void insertTags(final UUID objectId, final ObjectType objectType,
                           final List<TagDefinition> tagDefinitions, final CallContext context) {
        for (TagDefinition tagDefinition : tagDefinitions) {
            insertTag(objectId, objectType, tagDefinition, context);
        }
    }

    @Override
    public void deleteTag(final UUID objectId, final ObjectType objectType,
                          final TagDefinition tagDefinition, final CallContext context) {
        List<Tag> tags = tagStore.get(objectId);
        if (tags != null) {
            Iterator<Tag> tagIterator = tags.iterator();
            while (tagIterator.hasNext()) {
                Tag tag = tagIterator.next();
                if (tag.getTagDefinitionName().equals(tagDefinition.getName())) {
                    tagIterator.remove();
                }
            }
        }
    }
}
