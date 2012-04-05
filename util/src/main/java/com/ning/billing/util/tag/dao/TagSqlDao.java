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

import java.util.List;

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.ChangeTypeBinder;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import com.ning.billing.util.entity.EntityCollectionDao;
import com.ning.billing.util.tag.Tag;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(TagMapper.class)
public interface TagSqlDao extends EntityCollectionDao<Tag>, Transactional<TagSqlDao>, Transmogrifier {
    @Override
    @SqlBatch(transactional=false)
    public void batchInsertFromTransaction(@Bind("objectId") final String objectId,
                                           @Bind("objectType") final String objectType,
                                           @TagBinder final List<Tag> entities,
                                           @CallContextBinder final CallContext context);

    @Override
    @SqlBatch(transactional=false)
    public void batchDeleteFromTransaction(@Bind("objectId") final String objectId,
                                           @Bind("objectType") final String objectType,
                                           @TagBinder final List<Tag> entities,
                                           @CallContextBinder final CallContext context);

    @SqlBatch(transactional = false)
    public void batchInsertHistoryFromTransaction(@Bind("objectId") final String objectId,
                                                  @Bind("objectType") final String objectType,
                                                  @Bind("historyRecordId") final List<String> historyRecordIdList,
                                                  @TagBinder final List<Tag> tags,
                                                  @ChangeTypeBinder final ChangeType changeType,
                                                  @CallContextBinder final CallContext context);

    @SqlUpdate
    public void addTagFromTransaction(@Bind("id") final String tagId,
                                      @Bind("tagDefinitionName") final String tagName,
                                      @Bind("objectId") final String objectId,
                                      @Bind("objectType") final String objectType,
                                      @CallContextBinder final CallContext context);

    @SqlUpdate
    public void removeTagFromTransaction(@Bind("tagDefinitionName") final String tagName,
                                         @Bind("objectId") final String objectId,
                                         @Bind("objectType") final String objectType,
                                         @CallContextBinder final CallContext context);

    @SqlQuery
    public Tag findTag(@Bind("tagDefinitionName") final String tagName,
                       @Bind("objectId") final String objectId,
                       @Bind("objectType") final String objectType);
}