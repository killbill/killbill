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

import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.entity.Entity;
import com.ning.billing.util.entity.EntityPersistenceException;

public interface EntitySqlDao<T extends Entity> {

    @SqlUpdate
    public void create(@BindBean final T entity,
                       @InternalTenantContextBinder final InternalCallContext context) throws EntityPersistenceException;

    @SqlQuery
    public T getById(@Bind("id") final String id,
                     @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public T getByRecordId(@Bind("recordId") final Long recordId,
                           @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Long getRecordId(@Bind("id") final String id,
                            @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public Long getHistoryRecordId(@Bind("recordId") final Long recordId,
                                   @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public List<T> get(@InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    public void test(@InternalTenantContextBinder final InternalTenantContext context);
}
