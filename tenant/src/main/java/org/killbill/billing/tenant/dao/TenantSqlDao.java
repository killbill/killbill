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

import org.killbill.billing.util.cache.Cachable;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CachableKey;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;

import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;

@KillBillSqlDaoStringTemplate
public interface TenantSqlDao extends EntitySqlDao<TenantModelDao, Tenant> {

    @SqlQuery
    public TenantModelDao getByApiKey(@Bind("apiKey") final String apiKey);

    @SqlQuery
    public TenantModelDao getSecrets(@Bind("id") final String id);
}
