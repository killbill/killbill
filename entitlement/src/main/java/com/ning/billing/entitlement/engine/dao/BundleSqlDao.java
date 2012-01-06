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

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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

    public static class SubscriptionBundleBinder implements Binder<Bind, SubscriptionBundleData> {

        private Date getDate(DateTime dateTime) {
            return dateTime == null ? null : dateTime.toDate();
        }

        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, SubscriptionBundleData bundle) {
            stmt.bind("id", bundle.getId().toString());
            stmt.bind("start_dt", getDate(bundle.getStartDate()));
            stmt.bind("name", bundle.getKey());
            stmt.bind("account_id", bundle.getAccountId().toString());
        }
    }

    public static class ISubscriptionBundleSqlMapper implements ResultSetMapper<SubscriptionBundle> {

        private DateTime getDate(ResultSet r, String fieldName) throws SQLException {
            final Timestamp resultStamp = r.getTimestamp(fieldName);
            return r.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
        }

        @Override
        public SubscriptionBundle map(int arg, ResultSet r,
                StatementContext ctx) throws SQLException {

            UUID id = UUID.fromString(r.getString("id"));
            String name = r.getString("name");
            UUID accountId = UUID.fromString(r.getString("account_id"));
            DateTime startDate = getDate(r, "start_dt");
            SubscriptionBundleData bundle = new SubscriptionBundleData(id, name, accountId, startDate);
            return bundle;
        }

    }
}
