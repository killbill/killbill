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

import java.util.Collection;
import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.ObjectType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.ObjectTypeBinder;
import com.ning.billing.util.entity.collection.dao.UpdatableEntityCollectionSqlDao;
import com.ning.billing.util.tag.Tag;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(TagMapper.class)
public interface TagSqlDao extends UpdatableEntityCollectionSqlDao<Tag>, Transactional<TagSqlDao>, Transmogrifier {

    @Override
    @SqlBatch(transactional = false)
    public void insertFromTransaction(@Bind("objectId") final String objectId,
                                      @ObjectTypeBinder final ObjectType objectType,
                                      @TagBinder final Collection<Tag> tags,
                                      @InternalTenantContextBinder final InternalCallContext context);

    @Override
    @SqlBatch(transactional = false)
    public void updateFromTransaction(@Bind("objectId") final String objectId,
                                      @ObjectTypeBinder final ObjectType objectType,
                                      @TagBinder final Collection<Tag> tags,
                                      @InternalTenantContextBinder final InternalCallContext context);

    @Override
    @SqlBatch(transactional = false)
    public void deleteFromTransaction(@Bind("objectId") final String objectId,
                                      @ObjectTypeBinder final ObjectType objectType,
                                      @TagBinder final Collection<Tag> tags,
                                      @InternalTenantContextBinder final InternalCallContext context);

    @Override
    @SqlBatch(transactional = false)
    public void addHistoryFromTransaction(@Bind("objectId") final String objectId,
                                          @ObjectTypeBinder final ObjectType objectType,
                                          @TagHistoryBinder final List<EntityHistory<Tag>> histories,
                                          @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void addTagFromTransaction(@Bind("id") final String tagId,
                                      @Bind("tagDefinitionId") final String tagDefinitionId,
                                      @Bind("objectId") final String objectId,
                                      @ObjectTypeBinder final ObjectType objectType,
                                      @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void removeTagFromTransaction(@Bind("tagDefinitionId") final String tagDefinitionId,
                                         @Bind("objectId") final String objectId,
                                         @ObjectTypeBinder final ObjectType objectType,
                                         @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    public Tag findTag(@Bind("tagDefinitionId") final String tagDefinitionId,
                       @Bind("objectId") final String objectId,
                       @ObjectTypeBinder final ObjectType objectType,
                       @InternalTenantContextBinder final InternalTenantContext context);
}
