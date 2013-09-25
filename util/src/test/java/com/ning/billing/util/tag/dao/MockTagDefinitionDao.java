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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;

public class MockTagDefinitionDao implements TagDefinitionDao {

    private final Map<String, TagDefinitionModelDao> tags = new ConcurrentHashMap<String, TagDefinitionModelDao>();

    @Override
    public List<TagDefinitionModelDao> getTagDefinitions(final InternalTenantContext context) {
        return new ArrayList<TagDefinitionModelDao>(tags.values());
    }

    @Override
    public TagDefinitionModelDao getByName(final String definitionName, final InternalTenantContext context) {
        return tags.get(definitionName);
    }

    @Override
    public TagDefinitionModelDao create(final String definitionName, final String description,
                                        final InternalCallContext context) throws TagDefinitionApiException {
        final TagDefinitionModelDao tag = new TagDefinitionModelDao(null, definitionName, description);

        tags.put(tag.getId().toString(), tag);
        return tag;
    }

    @Override
    public void deleteById(final UUID definitionId, final InternalCallContext context) throws TagDefinitionApiException {
        tags.remove(definitionId.toString());
    }

    @Override
    public TagDefinitionModelDao getById(final UUID definitionId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public List<TagDefinitionModelDao> getByIds(final Collection<UUID> definitionIds, final InternalTenantContext context) {
        return null;
    }
}
