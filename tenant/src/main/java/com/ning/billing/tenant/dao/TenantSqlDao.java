/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.tenant.dao;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

import com.ning.billing.tenant.api.Tenant;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.UuidMapper;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper({UuidMapper.class, TenantMapper.class})
public interface TenantSqlDao extends EntitySqlDao<Tenant> {

    @SqlQuery
    public Tenant getByApiKey(@Bind("apiKey") final String apiKey);

    @SqlUpdate
    public void create(@TenantBinder final Tenant tenant,
                       @Bind("apiSecret") final String apiSecret,
                       @Bind("apiSalt") final String apiSalt,
                       @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    @Mapper(TenantSecretsMapper.class)
    public TenantSecrets getSecrets(@Bind("id") final String id);

}
