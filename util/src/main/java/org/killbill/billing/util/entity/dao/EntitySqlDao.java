/*
 * Copyright 2010-2011 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.util.Iterator;
import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.cache.Cachable;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CachableKey;
import org.killbill.billing.util.dao.AuditSqlDao;
import org.killbill.billing.util.dao.HistorySqlDao;
import org.killbill.billing.util.entity.Entity;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

@EntitySqlDaoStringTemplate
public interface EntitySqlDao<M extends EntityModelDao<E>, E extends Entity> extends AuditSqlDao, HistorySqlDao<M, E>, Transactional<EntitySqlDao<M, E>>, CloseMe {

    @SqlUpdate
    @Audited(ChangeType.INSERT)
    public Object create(@BindBean final M entity,
                         @BindBean final InternalCallContext context) throws EntityPersistenceException;

    @SqlQuery
    public M getById(@Bind("id") final String id,
                     @BindBean final InternalTenantContext context);

    @SqlQuery
    public M getByRecordId(@Bind("recordId") final Long recordId,
                           @BindBean final InternalTenantContext context);

    @SqlQuery
    public List<M> getByAccountRecordId(@BindBean final InternalTenantContext context);

    @SqlQuery
    public List<M> getByAccountRecordIdIncludedDeleted(@BindBean final InternalTenantContext context);

    @SqlQuery
    @Cachable(CacheType.RECORD_ID)
    public Long getRecordId(@CachableKey(1) @Bind("id") final String id,
                            @BindBean final InternalTenantContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<M> search(@Bind("searchKey") final String searchKey,
                              @Bind("likeSearchKey") final String likeSearchKey,
                              @Bind("offset") final Long offset,
                              @Bind("rowCount") final Long rowCount,
                              @Define("ordering") final String ordering,
                              @BindBean final InternalTenantContext context);

    @SqlQuery
    public Long getSearchCount(@Bind("searchKey") final String searchKey,
                               @Bind("likeSearchKey") final String likeSearchKey,
                               @BindBean final InternalTenantContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<M> getAll(@BindBean final InternalTenantContext context);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<M> get(@Bind("offset") final Long offset,
                           @Bind("rowCount") final Long rowCount,
                           @Define("orderBy") final String orderBy,
                           @Define("ordering") final String ordering,
                           @BindBean final InternalTenantContext context);

    @SqlQuery
    public Long getCount(@BindBean final InternalTenantContext context);

    @SqlUpdate
    public void test(@BindBean final InternalTenantContext context);
}
