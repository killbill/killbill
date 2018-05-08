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

package org.killbill.billing.util.tag.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.DefaultControlTag;
import org.killbill.billing.util.tag.DefaultTagDefinition;
import org.killbill.billing.util.tag.DescriptiveTag;
import org.killbill.billing.util.tag.Tag;
import org.killbill.billing.util.tag.TagDefinition;
import org.killbill.billing.util.tag.dao.SystemTags;
import org.killbill.billing.util.tag.dao.TagDao;
import org.killbill.billing.util.tag.dao.TagDefinitionDao;
import org.killbill.billing.util.tag.dao.TagDefinitionModelDao;
import org.killbill.billing.util.tag.dao.TagModelDao;
import org.killbill.billing.util.tag.dao.TagModelDaoHelper;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultTagUserApi implements TagUserApi {

    private static final Joiner JOINER = Joiner.on(",");

    private static final Function<TagModelDao, Tag> TAG_MODEL_DAO_TAG_FUNCTION = new Function<TagModelDao, Tag>() {
        @Override
        public Tag apply(final TagModelDao input) {
            return TagModelDaoHelper.isControlTag(input.getTagDefinitionId()) ?
                   new DefaultControlTag(input.getId(), ControlTagType.getTypeFromId(input.getTagDefinitionId()), input.getObjectType(), input.getObjectId(), input.getCreatedDate()) :
                   new DescriptiveTag(input.getId(), input.getTagDefinitionId(), input.getObjectType(), input.getObjectId(), input.getCreatedDate());
        }
    };

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
        return ImmutableList.<TagDefinition>copyOf(Collections2.transform(tagDefinitionDao.getTagDefinitions(true, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context)),
                                                                          new Function<TagDefinitionModelDao, TagDefinition>() {
                                                                              @Override
                                                                              public TagDefinition apply(final TagDefinitionModelDao input) {
                                                                                  return new DefaultTagDefinition(input, TagModelDaoHelper.isControlTag(input.getName()));
                                                                              }
                                                                          }));
    }

    @Override
    public TagDefinition createTagDefinition(final String definitionName, final String description, final Set<ObjectType> applicableObjectTypes, final CallContext context) throws TagDefinitionApiException {
        if (definitionName.matches(".*[A-Z].*")) {
            throw new TagDefinitionApiException(ErrorCode.TAG_DEFINITION_HAS_UPPERCASE, definitionName);
        }
        final TagDefinitionModelDao tagDefinitionModelDao = tagDefinitionDao.create(definitionName, description, JOINER.join(applicableObjectTypes), internalCallContextFactory.createInternalCallContextWithoutAccountRecordId(context));
        return new DefaultTagDefinition(tagDefinitionModelDao, TagModelDaoHelper.isControlTag(tagDefinitionModelDao.getName()));
    }

    @Override
    public void deleteTagDefinition(final UUID definitionId, final CallContext context) throws TagDefinitionApiException {
        tagDefinitionDao.deleteById(definitionId, internalCallContextFactory.createInternalCallContextWithoutAccountRecordId(context));
    }

    @Override
    public TagDefinition getTagDefinition(final UUID tagDefinitionId, final TenantContext context)
            throws TagDefinitionApiException {
        final TagDefinitionModelDao tagDefinitionModelDao = tagDefinitionDao.getById(tagDefinitionId, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
        return new DefaultTagDefinition(tagDefinitionModelDao, TagModelDaoHelper.isControlTag(tagDefinitionModelDao.getName()));
    }

    @Override
    public List<TagDefinition> getTagDefinitions(final Collection<UUID> tagDefinitionIds, final TenantContext context)
            throws TagDefinitionApiException {
        return ImmutableList.<TagDefinition>copyOf(Collections2.transform(tagDefinitionDao.getByIds(tagDefinitionIds, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context)),
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

        if (SystemTags.isSystemTag(tagDefinitionId)) {
            // TODO Create a proper ErrorCode instaed
            throw new IllegalStateException(String.format("Failed to add tag for tagDefinitionId='%s': System tags are reserved for the system.", tagDefinitionId));
        }

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(objectId, objectType, context);
        final TagModelDao tag = new TagModelDao(context.getCreatedDate(), tagDefinitionId, objectId, objectType);
        try {
            tagDao.create(tag, internalContext);
        } catch (TagApiException e) {
            // Be lenient here and make the addTag method idempotent
            if (ErrorCode.TAG_ALREADY_EXISTS.getCode() != e.getCode()) {
                throw e;
            }
        }
    }

    @Override
    public void removeTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final CallContext context) throws TagApiException {
        tagDao.deleteTag(objectId, objectType, tagDefinitionId, internalCallContextFactory.createInternalCallContext(objectId, objectType, context));
    }

    @Override
    public Pagination<Tag> searchTags(final String searchKey, final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<TagModelDao, TagApiException>() {
                                                  @Override
                                                  public Pagination<TagModelDao> build() {
                                                      return tagDao.searchTags(searchKey, offset, limit, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
                                                  }
                                              },
                                              TAG_MODEL_DAO_TAG_FUNCTION);
    }

    @Override
    public Pagination<Tag> getTags(final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<TagModelDao, TagApiException>() {
                                                  @Override
                                                  public Pagination<TagModelDao> build() {
                                                      return tagDao.get(offset, limit, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
                                                  }
                                              },
                                              TAG_MODEL_DAO_TAG_FUNCTION);
    }

    @Override
    public void removeTags(final UUID objectId, final ObjectType objectType, final Collection<UUID> tagDefinitionIds, final CallContext context) throws TagApiException {
        // TODO: consider making this batch
        for (final UUID tagDefinitionId : tagDefinitionIds) {
            tagDao.deleteTag(objectId, objectType, tagDefinitionId, internalCallContextFactory.createInternalCallContext(objectId, objectType, context));
        }
    }

    @Override
    public TagDefinition getTagDefinitionForName(final String tagDefinitionName, final TenantContext context)
            throws TagDefinitionApiException {
        return new DefaultTagDefinition(tagDefinitionDao.getByName(tagDefinitionName, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context)),
                                        TagModelDaoHelper.isControlTag(tagDefinitionName));
    }

    @Override
    public List<Tag> getTagsForObject(final UUID objectId, final ObjectType objectType, final boolean includedDeleted, final TenantContext context) {
        return withModelTransform(tagDao.getTagsForObject(objectId, objectType, includedDeleted, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context)));
    }

    @Override
    public List<Tag> getTagsForAccountType(final UUID accountId, final ObjectType objectType, final boolean includedDeleted, final TenantContext context) {
        return withModelTransform(tagDao.getTagsForAccountType(objectType, includedDeleted, internalCallContextFactory.createInternalTenantContext(accountId, context)));
    }

    @Override
    public List<Tag> getTagsForAccount(final UUID accountId, final boolean includedDeleted, final TenantContext context) {
        return withModelTransform(tagDao.getTagsForAccount(includedDeleted, internalCallContextFactory.createInternalTenantContext(accountId, context)));
    }

    @Override
    public List<AuditLogWithHistory> getTagAuditLogsWithHistoryForId(final UUID tagId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return tagDao.getTagAuditLogsWithHistoryForId(tagId, auditLevel, internalCallContextFactory.createInternalTenantContext(tagId, ObjectType.TAG, tenantContext));
    }

    @Override
    public List<AuditLogWithHistory> getTagDefinitionAuditLogsWithHistoryForId(final UUID tagDefinitionId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return tagDefinitionDao.getTagDefinitionAuditLogsWithHistoryForId(tagDefinitionId, auditLevel, internalCallContextFactory.createInternalTenantContext(tagDefinitionId, ObjectType.TAG_DEFINITION, tenantContext));
    }

    private List<Tag> withModelTransform(final Collection<TagModelDao> input) {
        return ImmutableList.<Tag>copyOf(Collections2.transform(input, TAG_MODEL_DAO_TAG_FUNCTION));
    }
}
