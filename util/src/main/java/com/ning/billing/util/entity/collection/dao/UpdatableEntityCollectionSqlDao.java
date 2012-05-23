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

package com.ning.billing.util.entity.collection.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.AuditSqlDao;
import com.ning.billing.util.dao.CollectionHistorySqlDao;
import com.ning.billing.util.dao.HistoryRecordIdMapper;
import com.ning.billing.util.dao.Mapper;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.ObjectTypeBinder;
import com.ning.billing.util.entity.Entity;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import java.util.List;

public interface UpdatableEntityCollectionSqlDao<T extends Entity> extends EntityCollectionSqlDao<T>,
        CollectionHistorySqlDao<T>,
        AuditSqlDao, CloseMe, Transmogrifier {
    @SqlBatch(transactional=false)
    public void updateFromTransaction(@Bind("objectId") final String objectId,
                                      @ObjectTypeBinder final ObjectType objectType,
                                      @BindBean final List<T> entities,
                                      @CallContextBinder final CallContext context);

    @SqlQuery
    public long getMaxHistoryRecordId();

    @SqlQuery
    @RegisterMapper(HistoryRecordIdMapper.class)
    public List<Mapper<Long, Long>> getHistoryRecordIds(@Bind("maxHistoryRecordId") final long maxHistoryRecordId);
}
