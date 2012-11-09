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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.ObjectType;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.tag.Tag;

public class MockTagDao implements TagDao {

    private final Map<UUID, List<Tag>> tagStore = new HashMap<UUID, List<Tag>>();

    @Override
    public void create(final Tag tag, final InternalCallContext context) throws TagApiException {
        if (tagStore.get(tag.getObjectId()) == null) {
            tagStore.put(tag.getObjectId(), new ArrayList<Tag>());
        }
        tagStore.get(tag.getObjectId()).add(tag);
    }

    @Override
    public void deleteTag(final UUID objectId, final ObjectType objectType,
                          final UUID tagDefinitionId, final InternalCallContext context) {
        final List<Tag> tags = tagStore.get(objectId);
        if (tags != null) {
            final Iterator<Tag> tagIterator = tags.iterator();
            while (tagIterator.hasNext()) {
                final Tag tag = tagIterator.next();
                if (tag.getTagDefinitionId().equals(tagDefinitionId)) {
                    tagIterator.remove();
                }
            }
        }
    }

    @Override
    public Tag getById(final UUID tagId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Tag> getTags(final UUID objectId, final ObjectType objectType, final InternalTenantContext internalTenantContext) {
        throw new UnsupportedOperationException();
    }
}
