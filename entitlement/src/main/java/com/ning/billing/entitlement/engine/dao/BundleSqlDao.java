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
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.AuditSqlDao;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.EntitySqlDao;


@ExternalizedSqlViaStringTemplate3()
public interface BundleSqlDao extends Transactional<BundleSqlDao>, EntitySqlDao<SubscriptionBundle>,
        AuditSqlDao, CloseMe, Transmogrifier {

    @SqlUpdate
    public void insertBundle(@Bind(binder = SubscriptionBundleBinder.class) SubscriptionBundleData bundle,
                             @CallContextBinder final CallContext context);

    @SqlUpdate
    public void updateBundleLastSysTime(@Bind("id") String id, @Bind("lastSysUpdateDate") Date lastSysUpdate);

    @SqlQuery
    @Mapper(ISubscriptionBundleSqlMapper.class)
    public SubscriptionBundle getBundleFromId(@Bind("id") String id);

    @SqlQuery
    @Mapper(ISubscriptionBundleSqlMapper.class)
    public SubscriptionBundle getBundleFromKey(@Bind("externalKey") String externalKey);

    @SqlQuery
    @Mapper(ISubscriptionBundleSqlMapper.class)
    public List<SubscriptionBundle> getBundleFromAccount(@Bind("accountId") String accountId);

    public static class SubscriptionBundleBinder extends BinderBase implements Binder<Bind, SubscriptionBundleData> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final SubscriptionBundleData bundle) {
            stmt.bind("id", bundle.getId().toString());
            stmt.bind("startDate", getDate(bundle.getStartDate()));
            stmt.bind("externalKey", bundle.getKey());
            stmt.bind("accountId", bundle.getAccountId().toString());
            stmt.bind("lastSysUpdateDate", getDate(bundle.getLastSysUpdateTime()));
        }
    }

    public static class ISubscriptionBundleSqlMapper extends MapperBase implements ResultSetMapper<SubscriptionBundle> {

        @Override
        public SubscriptionBundle map(final int arg, final ResultSet r,
                                      final StatementContext ctx) throws SQLException {
            final UUID id = UUID.fromString(r.getString("id"));
            final String key = r.getString("external_key");
            final UUID accountId = UUID.fromString(r.getString("account_id"));
            final DateTime startDate = getDate(r, "start_date");
            final DateTime lastSysUpdateDate = getDate(r, "last_sys_update_date");
            final SubscriptionBundleData bundle = new SubscriptionBundleData(id, key, accountId, startDate, lastSysUpdateDate);
            return bundle;
        }
    }
}
