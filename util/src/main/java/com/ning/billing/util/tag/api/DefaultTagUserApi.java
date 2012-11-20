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
import java.util.UUID;

import com.ning.billing.ObjectType;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;
import com.ning.billing.util.tag.dao.TagDefinitionModelDao;
import com.ning.billing.util.tag.dao.TagModelDao;
import com.ning.billing.util.tag.dao.TagModelDaoHelper;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
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
        return ImmutableList.<TagDefinition>copyOf(Collections2.transform(tagDefinitionDao.getTagDefinitions(internalCallContextFactory.createInternalTenantContext(context)),
                                                                          new Function<TagDefinitionModelDao, TagDefinition>() {
                                                                              @Override
                                                                              public TagDefinition apply(final TagDefinitionModelDao input) {
                                                                                  return new DefaultTagDefinition(input, TagModelDaoHelper.isControlTag(input.getName()));
                                                                              }
                                                                          }));
    }

    @Override
    public TagDefinition create(final String definitionName, final String description, final CallContext context) throws TagDefinitionApiException {
        final TagDefinitionModelDao tagDefinitionModelDao = tagDefinitionDao.create(definitionName, description, internalCallContextFactory.createInternalCallContext(context));
        return new DefaultTagDefinition(tagDefinitionModelDao, TagModelDaoHelper.isControlTag(tagDefinitionModelDao.getName()));
    }

    @Override
    public void deleteTagDefinition(final UUID definitionId, final CallContext context) throws TagDefinitionApiException {
        tagDefinitionDao.deleteById(definitionId, internalCallContextFactory.createInternalCallContext(context));
    }

    @Override
    public TagDefinition getTagDefinition(final UUID tagDefinitionId, final TenantContext context)
            throws TagDefinitionApiException {
        final TagDefinitionModelDao tagDefinitionModelDao = tagDefinitionDao.getById(tagDefinitionId, internalCallContextFactory.createInternalTenantContext(context));
        return new DefaultTagDefinition(tagDefinitionModelDao, TagModelDaoHelper.isControlTag(tagDefinitionModelDao.getName()));
    }

    @Override
    public List<TagDefinition> getTagDefinitions(final Collection<UUID> tagDefinitionIds, final TenantContext context)
            throws TagDefinitionApiException {
        return ImmutableList.<TagDefinition>copyOf(Collections2.transform(tagDefinitionDao.getByIds(tagDefinitionIds, internalCallContextFactory.createInternalTenantContext(context)),
                                                                          new Function<TagDefinitionModelDao, TagDefinition>() {
                                                                              @Override
                                                                              public TagDefinition apply(final TagDefinitionModelDao input) {
                                                                                  return new DefaultTagDefinition(input, TagModelDaoHelper.isControlTag(input.getName()));
                                                                              }
                                                                          }));
    }

    @Override
    public void addTags(final UUID objectId, final ObjectType objectType, final Collection<UUID> tagDefinitionIds, final CallContext context) throws TagApiException {
        for (final UUID tagDefinitionId : tagDefinitionIds) {
            addTag(objectId, objectType, tagDefinitionId, context);
        }
    }

    @Override
    public void addTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final CallContext context) throws TagApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(objectId, objectType, context);
        final TagModelDao tag = new TagModelDao(context.getCreatedDate(), tagDefinitionId, objectId, objectType);
        tagDao.create(tag, internalContext);
    }

    @Override
    public void removeTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final CallContext context) throws TagApiException {
        tagDao.deleteTag(objectId, objectType, tagDefinitionId, internalCallContextFactory.createInternalCallContext(objectId, objectType, context));
    }

    @Override
    public void removeTags(final UUID objectId, final ObjectType objectType, final Collection<UUID> tagDefinitionIds, final CallContext context) throws TagApiException {
        // TODO: consider making this batch
        for (final UUID tagDefinitionId : tagDefinitionIds) {
            tagDao.deleteTag(objectId, objectType, tagDefinitionId, internalCallContextFactory.createInternalCallContext(objectId, objectType, context));
        }
    }

    @Override
    public List<Tag> getTags(final UUID objectId, final ObjectType objectType, final TenantContext context) {
        return ImmutableList.<Tag>copyOf(Collections2.transform(tagDao.getTags(objectId, objectType, internalCallContextFactory.createInternalTenantContext(context)),
                                                                new Function<TagModelDao, Tag>() {
                                                                    @Override
                                                                    public Tag apply(final TagModelDao input) {
                                                                        return TagModelDaoHelper.isControlTag(input.getTagDefinitionId()) ?
                                                                               new DefaultControlTag(ControlTagType.getTypeFromId(input.getTagDefinitionId()), objectType, objectId, input.getCreatedDate()) :
                                                                               new DescriptiveTag(input.getTagDefinitionId(), objectType, objectId, input.getCreatedDate());
                                                                    }
                                                                }));
    }

    @Override
    public TagDefinition getTagDefinitionForName(final String tagDefinitionName, final TenantContext context)
            throws TagDefinitionApiException {
        return new DefaultTagDefinition(tagDefinitionDao.getByName(tagDefinitionName, internalCallContextFactory.createInternalTenantContext(context)),
                                        TagModelDaoHelper.isControlTag(tagDefinitionName));
    }
}
