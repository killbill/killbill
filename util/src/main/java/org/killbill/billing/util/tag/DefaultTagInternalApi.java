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

package org.killbill.billing.util.tag;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.tag.dao.TagDao;
import org.killbill.billing.util.tag.dao.TagDefinitionDao;
import org.killbill.billing.util.tag.dao.TagModelDao;
import org.killbill.billing.util.tag.dao.TagModelDaoHelper;

public class DefaultTagInternalApi implements TagInternalApi {

    private final TagDao tagDao;
    private final TagDefinitionDao tagDefinitionDao;

    @Inject
    public DefaultTagInternalApi(final TagDao tagDao, final TagDefinitionDao tagDefinitionDao) {
        this.tagDao = tagDao;
        this.tagDefinitionDao = tagDefinitionDao;
    }

    @Override
    public List<TagDefinition> getTagDefinitions(final InternalTenantContext context) {
        final List<TagDefinition> result = tagDefinitionDao.getTagDefinitions(true, context).stream()
                .map(modelDao -> new DefaultTagDefinition(modelDao, TagModelDaoHelper.isControlTag(modelDao.getName())))
                .collect(Collectors.toUnmodifiableList());
        return result;
    }

    @Override
    public List<Tag> getTags(final UUID objectId, final ObjectType objectType, final InternalTenantContext context) {
        return toTagList(tagDao.getTagsForObject(objectId, objectType, false, context));
    }

    @Override
    public List<Tag> getTagsForAccount(final boolean includedDeleted, final InternalTenantContext context) {
        return toTagList(tagDao.getTagsForAccount(includedDeleted, context));
    }

    @Override
    public List<Tag> getTagsForAccountType(final ObjectType objectType, final boolean includedDeleted, final InternalTenantContext internalTenantContext) {
        return toTagList(tagDao.getTagsForAccountType(objectType, includedDeleted, internalTenantContext));
    }

    @Override
    public void addTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final InternalCallContext context)
            throws TagApiException {
        final TagModelDao tag = new TagModelDao(context.getCreatedDate(), tagDefinitionId, objectId, objectType);
        try {
            tagDao.create(tag, context);
        } catch (final TagApiException e) {
            // Be lenient here and make the addTag method idempotent
            if (ErrorCode.TAG_ALREADY_EXISTS.getCode() != e.getCode()) {
                throw e;
            }
        }
    }

    @Override
    public void removeTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final InternalCallContext context)
            throws TagApiException {
        tagDao.deleteTag(objectId, objectType, tagDefinitionId, context);
    }

    private List<Tag> toTagList(final List<TagModelDao> input) {
        final List<Tag> result = input.stream()
                .map(modelDao -> TagModelDaoHelper.isControlTag(modelDao.getTagDefinitionId()) ?
                                 new DefaultControlTag(ControlTagType.getTypeFromId(modelDao.getTagDefinitionId()), modelDao.getObjectType(), modelDao.getObjectId(), modelDao.getCreatedDate()) :
                                 new DescriptiveTag(modelDao.getTagDefinitionId(), modelDao.getObjectType(), modelDao.getObjectId(), modelDao.getCreatedDate()))
                .collect(Collectors.toUnmodifiableList());
        return result;
    }


}
