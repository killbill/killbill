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

import com.google.inject.Inject;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.util.entity.BinderBase;
import com.ning.billing.util.entity.MapperBase;
import com.ning.billing.util.overdue.OverdueAccessApi;

@ExternalizedSqlViaStringTemplate3()
public interface BundleSqlDao extends Transactional<BundleSqlDao>, CloseMe, Transmogrifier {

    @SqlUpdate
    public void insertBundle(@Bind(binder = SubscriptionBundleBinder.class) SubscriptionBundleData bundle);

    @SqlUpdate
    public void removeBundle(@Bind("id") String id);

    @SqlQuery
    @Mapper(ISubscriptionBundleSqlMapper.class)
    public SubscriptionBundle getBundleFromId(@Bind("id") String id);

    @SqlQuery
    @Mapper(ISubscriptionBundleSqlMapper.class)
    public SubscriptionBundle getBundleFromKey(@Bind("name") String name);

    @SqlQuery
    @Mapper(ISubscriptionBundleSqlMapper.class)
    public List<SubscriptionBundle> getBundleFromAccount(@Bind("account_id") String accountId);

    public static class SubscriptionBundleBinder extends BinderBase implements Binder<Bind, SubscriptionBundleData> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, SubscriptionBundleData bundle) {
            stmt.bind("id", bundle.getId().toString());
            stmt.bind("start_dt", getDate(bundle.getStartDate()));
            stmt.bind("name", bundle.getKey());
            stmt.bind("account_id", bundle.getAccountId().toString());
        }
    }

    public static class ISubscriptionBundleSqlMapper extends MapperBase implements ResultSetMapper<SubscriptionBundle> {
        
        @Override
        public SubscriptionBundle map(int arg, ResultSet r,
                StatementContext ctx) throws SQLException {

            UUID id = UUID.fromString(r.getString("id"));
            String name = r.getString("name");
            UUID accountId = UUID.fromString(r.getString("account_id"));
            DateTime startDate = getDate(r, "start_dt");
            SubscriptionBundleData bundle = new SubscriptionBundleData(id, name, accountId, startDate, null);
            return bundle;
        }

    }
}
