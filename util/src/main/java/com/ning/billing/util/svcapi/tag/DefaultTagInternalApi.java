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
package com.ning.billing.util.svcapi.tag;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import com.ning.billing.ObjectType;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

public class DefaultTagInternalApi implements TagInternalApi {

    private final TagDao tagDao;
    private final TagDefinitionDao tagDefinitionDao;

    @Inject
    public DefaultTagInternalApi(final TagDao tagDao,
                                 final TagDefinitionDao tagDefinitionDao) {
        this.tagDao = tagDao;
        this.tagDefinitionDao = tagDefinitionDao;
    }

    @Override
    public List<TagDefinition> getTagDefinitions(final InternalTenantContext context) {
        return tagDefinitionDao.getTagDefinitions(context);
    }

    @Override
    public Map<String, Tag> getTags(UUID objectId, ObjectType objectType,
            InternalTenantContext context) {
        return tagDao.getTags(objectId, objectType, context);
    }

    @Override
    public void addTag(UUID objectId, ObjectType objectType,
            UUID tagDefinitionId, InternalCallContext context)
            throws TagApiException {
        tagDao.insertTag(objectId, objectType, tagDefinitionId, context);

    }

    @Override
    public void removeTag(UUID objectId, ObjectType objectType,
            UUID tagDefinitionId, InternalCallContext context)
            throws TagApiException {
        tagDao.deleteTag(objectId, objectType, tagDefinitionId, context);
    }
}
