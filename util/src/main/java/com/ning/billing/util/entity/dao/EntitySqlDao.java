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

package com.ning.billing.util.entity.dao;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.cache.Cachable;
import com.ning.billing.util.cache.Cachable.CacheType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.AuditSqlDao;
import com.ning.billing.util.dao.HistorySqlDao;
import com.ning.billing.util.entity.Entity;
import com.ning.billing.util.entity.EntityPersistenceException;

// TODO get rid of Transmogrifier, but code does not compile even if we create the
// method  public <T> T become(Class<T> typeToBecome); ?
//
@EntitySqlDaoStringTemplate
public interface EntitySqlDao<M extends EntityModelDao<E>, E extends Entity> extends AuditSqlDao, HistorySqlDao<M, E>, Transmogrifier, Transactional<EntitySqlDao<M, E>>, CloseMe {

    @SqlUpdate
    @Audited(ChangeType.INSERT)
    public void create(@BindBean final M entity,
                       @BindBean final InternalCallContext context) throws EntityPersistenceException;

    @SqlQuery
    public M getById(@Bind("id") final String id,
                     @BindBean final InternalTenantContext context);

    @SqlQuery
    public M getByRecordId(@Bind("recordId") final Long recordId,
                           @BindBean final InternalTenantContext context);

    @SqlQuery
    @Cachable(CacheType.RECORD_ID)
    public Long getRecordId(@Bind("id") final String id,
                            @BindBean final InternalTenantContext context);

    @SqlQuery
    public List<M> get(@BindBean final InternalTenantContext context);

    @SqlUpdate
    public void test(@BindBean final InternalTenantContext context);


}
