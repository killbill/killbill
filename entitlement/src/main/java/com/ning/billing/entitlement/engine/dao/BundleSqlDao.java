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

package com.ning.billing.entitlement.engine.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper(BundleSqlDao.ISubscriptionBundleSqlMapper.class)
public interface BundleSqlDao extends EntitySqlDao<SubscriptionBundle> {
    @SqlUpdate
    @Audited(ChangeType.INSERT)
    public void insertBundle(@Bind(binder = SubscriptionBundleBinder.class) SubscriptionBundleData bundle,
                             @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updateBundleLastSysTime(@Bind("id") String id,
                                        @Bind("lastSysUpdateDate") Date lastSysUpdate,
                                        @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    public SubscriptionBundle getBundleFromId(@Bind("id") String id,
                                              @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public SubscriptionBundle getBundleFromAccountAndKey(@Bind("accountId") String accountId,
                                                         @Bind("externalKey") String externalKey,
                                                         @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public List<SubscriptionBundle> getBundleFromAccount(@Bind("accountId") String accountId,
                                                         @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    public List<SubscriptionBundle> getBundlesForKey(@Bind("externalKey") String externalKey,
                                                     @InternalTenantContextBinder final InternalTenantContext context);

    public static class SubscriptionBundleBinder extends BinderBase implements Binder<Bind, SubscriptionBundleData> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final SubscriptionBundleData bundle) {
            stmt.bind("id", bundle.getId().toString());
            stmt.bind("externalKey", bundle.getKey());
            stmt.bind("accountId", bundle.getAccountId().toString());
            stmt.bind("lastSysUpdateDate", getDate(bundle.getLastSysUpdateTime()));
        }
    }

    public static class ISubscriptionBundleSqlMapper extends MapperBase implements ResultSetMapper<SubscriptionBundle> {
        @Override
        public SubscriptionBundle map(final int arg, final ResultSet r, final StatementContext ctx) throws SQLException {
            final UUID id = UUID.fromString(r.getString("id"));
            final String key = r.getString("external_key");
            final UUID accountId = UUID.fromString(r.getString("account_id"));
            final DateTime lastSysUpdateDate = getDateTime(r, "last_sys_update_date");
            return new SubscriptionBundleData(id, key, accountId, lastSysUpdateDate);
        }
    }
}
