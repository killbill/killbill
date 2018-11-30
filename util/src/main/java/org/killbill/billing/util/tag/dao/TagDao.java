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

package org.killbill.billing.util.tag.dao;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntityDao;
import org.killbill.billing.util.tag.Tag;

public interface TagDao extends EntityDao<TagModelDao, Tag, TagApiException> {

    void deleteTag(UUID objectId, ObjectType objectType, UUID tagDefinition, InternalCallContext context) throws TagApiException;

    Pagination<TagModelDao> searchTags(String searchKey, Long offset, Long limit, InternalTenantContext context);

    List<TagModelDao> getTagsForObject(UUID objectId, ObjectType objectType, boolean includedDeleted, InternalTenantContext internalTenantContext);

    List<TagModelDao> getTagsForAccountType(ObjectType objectType, boolean includedDeleted, InternalTenantContext internalTenantContext);

    List<TagModelDao> getTagsForAccount(boolean includedDeleted, InternalTenantContext internalTenantContext);

    List<AuditLogWithHistory> getTagAuditLogsWithHistoryForId(UUID tagId, AuditLevel auditLevel, InternalTenantContext context);

}
