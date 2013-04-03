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

package com.ning.billing.osgi.bundles.analytics.dao;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;

import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

@UseStringTemplate3StatementLocator
public interface BusinessAnalyticsSqlDao extends Transactional<BusinessAnalyticsSqlDao> {

    @SqlUpdate
    public void create(@Bind("tableName") final String tableName,
                       @BindBean final BusinessModelDaoBase entity,
                       @BindBean final CallContext callContext);

    @SqlUpdate
    public void deleteByAccountRecordId(@Bind("tableName") final String tableName,
                                        @Bind("accountRecordId") final Long accountRecordId,
                                        @Bind("tenantRecordId") final Long tenantRecordId,
                                        @BindBean final CallContext callContext);

    @SqlQuery
    public Iterable findByAccountRecordId(@Bind("tableName") final String tableName,
                                          @Bind("accountRecordId") final Long accountRecordId,
                                          @BindBean final TenantContext callContext);
}
