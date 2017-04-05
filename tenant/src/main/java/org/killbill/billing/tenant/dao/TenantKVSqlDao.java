/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.tenant.dao;

import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tenant.api.TenantKV;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

@EntitySqlDaoStringTemplate
public interface TenantKVSqlDao extends EntitySqlDao<TenantKVModelDao, TenantKV> {

    @SqlQuery
    public List<TenantKVModelDao> getTenantValueForKey(@Bind("tenantKey") final String key,
                                                       @BindBean final InternalTenantContext context);


    @SqlQuery
    public List<TenantKVModelDao> searchTenantKeyValues(@Bind("tenantKeyPrefix") final String tenantKeyPrefix,
                                                       @BindBean final InternalTenantContext context);

    @SqlUpdate
    @Audited(ChangeType.DELETE)
    public void markTenantKeyAsDeleted(@Bind("id")final String id,
                                       @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public Object updateTenantValueKey(@Bind("id") final String id,
                                       @Bind("tenantValue") final String tenantValue,
                                       @BindBean final InternalCallContext context);
}
