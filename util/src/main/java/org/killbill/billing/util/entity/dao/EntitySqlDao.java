/*
 * Copyright 2010-2011 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.entity.dao;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.dao.AuditSqlDao;
import org.killbill.billing.util.dao.HistorySqlDao;
import org.killbill.billing.util.entity.Entity;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.BatchChunkSize;
import org.skife.jdbi.v2.sqlobject.customizers.Define;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.unstable.BindIn;
import org.skife.jdbi.v2.util.LongMapper;

@KillBillSqlDaoStringTemplate
public interface EntitySqlDao<M extends EntityModelDao<E>, E extends Entity> extends AuditSqlDao, HistorySqlDao<M, E>, Transactional<EntitySqlDao<M, E>>, CloseMe {

    @SqlUpdate
    @GetGeneratedKeys(value = LongMapper.class, columnName = "record_id")
    @Audited(ChangeType.INSERT)
    public Object create(@SmartBindBean final M entity,
                         @SmartBindBean final InternalCallContext context);

    @SqlBatch
    @BatchChunkSize(1000) // Arbitrary value, just a safety mechanism in case of very large datasets
    @GetGeneratedKeys(value = LongMapper.class, columnName = "record_id")
    @Audited(ChangeType.INSERT)
    // Note that you cannot rely on the ordering here
    public List<Long> create(@SmartBindBean final Iterable<M> entity,
                             @SmartBindBean final InternalCallContext context);

    @SqlQuery
    public M getByRecordId(@Bind("recordId") final Long recordId,
                           @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<M> getByRecordIds(@BindIn("recordIds") final Collection<Long> recordId,
                                  @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public M getById(@Bind("id") final String id,
                     @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    List<M> getByIds(@BindIn("ids") final Collection<String> ids,
                     @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    List<M> getByIdsIncludedDeleted(@BindIn("ids") final Collection<String> ids,
                                    @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<M> getByAccountRecordId(@SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<M> getByAccountRecordIdIncludedDeleted(@SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getRecordId(@Bind("id") final String id,
                            @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<M> search(@Bind("searchKey") final String searchKey,
                              @Bind("likeSearchKey") final String likeSearchKey,
                              @Bind("offset") final Long offset,
                              @Bind("rowCount") final Long rowCount,
                              @Define("ordering") final String ordering,
                              @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getSearchCount(@Bind("searchKey") final String searchKey,
                               @Bind("likeSearchKey") final String likeSearchKey,
                               @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<M> getAll(@SmartBindBean final InternalTenantContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<M> get(@Bind("offset") final Long offset,
                           @Bind("rowCount") final Long rowCount,
                           @Define("orderBy") final String orderBy,
                           @Define("ordering") final String ordering,
                           @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getRecordIdAtOffset(@Bind("offset") final Long offset);

    @SqlQuery
    public Long getCount(@SmartBindBean final InternalTenantContext context);

    @SqlUpdate
    public void test(@SmartBindBean final InternalTenantContext context);
}
