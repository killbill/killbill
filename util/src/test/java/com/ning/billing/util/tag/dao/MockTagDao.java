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

import com.google.inject.Inject;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.tag.Tag;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MockTagDao implements TagDao {
    private Map<UUID, List<Tag>> tagStore = new HashMap<UUID, List<Tag>>();
    private final Clock clock;

    @Inject
    public MockTagDao(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void saveTagsFromTransaction(final Transmogrifier dao, final UUID objectId, final String objectType,
                                        final List<Tag> tags, final CallContext context) {
        tagStore.put(objectId, tags);
    }

    @Override
    public void saveTags(UUID objectId, String objectType, List<Tag> tags, CallContext context) {
        tagStore.put(objectId, tags);
    }

    @Override
    public List<Tag> loadTags(UUID objectId, String objectType) {
        return tagStore.get(objectId);
    }

    @Override
    public List<Tag> loadTagsFromTransaction(Transmogrifier dao, UUID objectId, String objectType) {
        return tagStore.get(objectId);
    }

    @Override
    public void addTag(final String tagName, final UUID objectId, final String objectType, final CallContext context) {
        Tag tag = new Tag() {
            private UUID id = UUID.randomUUID();
            private DateTime createdDate = clock.getUTCNow();

            @Override
            public String getTagDefinitionName() {
                return tagName;
            }

            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public String getCreatedBy() {
                return context.getUserName();
            }

            @Override
            public DateTime getCreatedDate() {
                return createdDate;
            }
        };


        tagStore.get(objectId).add(tag);
    }

    @Override
    public void removeTag(String tagName, UUID objectId, String objectType, CallContext context) {
        List<Tag> tags = tagStore.get(objectId);
        if (tags != null) {
            Iterator<Tag> tagIterator = tags.iterator();
            while (tagIterator.hasNext()) {
                Tag tag = tagIterator.next();
                if (tag.getTagDefinitionName().equals(tagName)) {
                    tagIterator.remove();
                }
            }
        }
    }
}
