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

package com.ning.billing.util.tag.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import com.google.inject.Inject;

public class DefaultTagUserApi implements TagUserApi {

    private final InternalCallContextFactory internalCallContextFactory;
    private final TagDefinitionDao tagDefinitionDao;
    private final TagDao tagDao;

    @Inject
    public DefaultTagUserApi(final InternalCallContextFactory internalCallContextFactory, final TagDefinitionDao tagDefinitionDao, final TagDao tagDao) {
        this.internalCallContextFactory = internalCallContextFactory;
        this.tagDefinitionDao = tagDefinitionDao;
        this.tagDao = tagDao;
    }

    @Override
    public List<TagDefinition> getTagDefinitions(final TenantContext context) {
        return tagDefinitionDao.getTagDefinitions(internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public TagDefinition create(final String definitionName, final String description, final CallContext context) throws TagDefinitionApiException {
        return tagDefinitionDao.create(definitionName, description, internalCallContextFactory.createInternalCallContext(context));
    }

    @Override
    public void deleteTagDefinition(final UUID definitionId, final CallContext context) throws TagDefinitionApiException {
        tagDefinitionDao.deleteById(definitionId, internalCallContextFactory.createInternalCallContext(context));
    }

    @Override
    public TagDefinition getTagDefinition(final UUID tagDefinitionId, final TenantContext context)
            throws TagDefinitionApiException {
        return tagDefinitionDao.getById(tagDefinitionId, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public List<TagDefinition> getTagDefinitions(final Collection<UUID> tagDefinitionIds, final TenantContext context)
            throws TagDefinitionApiException {
        return tagDefinitionDao.getByIds(tagDefinitionIds, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public void addTags(final UUID objectId, final ObjectType objectType, final Collection<UUID> tagDefinitionIds, final CallContext context) throws TagApiException {
        // TODO: consider making this batch
        for (final UUID tagDefinitionId : tagDefinitionIds) {
            // TODO accountId?
            tagDao.insertTag(objectId, objectType, tagDefinitionId, internalCallContextFactory.createInternalCallContext(context));
        }
    }

    @Override
    public void addTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final CallContext context) throws TagApiException {
        // TODO accountId?
        tagDao.insertTag(objectId, objectType, tagDefinitionId, internalCallContextFactory.createInternalCallContext(context));
    }

    @Override
    public void removeTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final CallContext context) throws TagApiException {
        // TODO accountId?
        tagDao.deleteTag(objectId, objectType, tagDefinitionId, internalCallContextFactory.createInternalCallContext(context));
    }

    @Override
    public void removeTags(final UUID objectId, final ObjectType objectType, final Collection<UUID> tagDefinitionIds, final CallContext context) throws TagApiException {
        // TODO: consider making this batch
        for (final UUID tagDefinitionId : tagDefinitionIds) {
            // TODO accountId?
            tagDao.deleteTag(objectId, objectType, tagDefinitionId, internalCallContextFactory.createInternalCallContext(context));
        }
    }

    @Override
    public Map<String, Tag> getTags(final UUID objectId, final ObjectType objectType, final TenantContext context) {
        return tagDao.loadEntities(objectId, objectType, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public TagDefinition getTagDefinitionForName(final String tagDefinitionName, final TenantContext context)
            throws TagDefinitionApiException {
        return tagDefinitionDao.getByName(tagDefinitionName, internalCallContextFactory.createInternalTenantContext(context));
    }
}
