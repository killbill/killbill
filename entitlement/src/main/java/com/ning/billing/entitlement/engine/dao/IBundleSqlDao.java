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
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.entitlement.api.user.ISubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;


public interface IBundleSqlDao extends Transactional<IEventSqlDao>, CloseMe, Transmogrifier {

    @SqlUpdate("insert into bundles (id, start_dt, name, account_id) values (:id, :start_dt, :name, :account_id)")
    public void insertBundle(@Bind(binder = SubscriptionBundleBinder.class) SubscriptionBundle bundle);

    @SqlQuery("select id, start_dt, name, account_id from bundles where id = :id")
    @Mapper(ISubscriptionBundleSqlMapper.class)
    public ISubscriptionBundle getBundleFromId(@Bind("id") String id);

    @SqlQuery("select id, start_dt, name, account_id from bundles where account_id = :account_id")
    @Mapper(ISubscriptionBundleSqlMapper.class)
    public List<ISubscriptionBundle> getBundleFromAccount(@Bind("account_id") String accountId);

    public static class SubscriptionBundleBinder implements Binder<Bind, SubscriptionBundle> {

        private Date getDate(DateTime dateTime) {
            return dateTime == null ? null : dateTime.toDate();
        }

        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, SubscriptionBundle bundle) {
            stmt.bind("id", bundle.getId().toString());
            stmt.bind("start_dt", getDate(bundle.getStartDate()));
            stmt.bind("name", bundle.getName());
            stmt.bind("account_id", bundle.getAccountId().toString());
        }
    }

    public static class ISubscriptionBundleSqlMapper implements ResultSetMapper<ISubscriptionBundle> {

        private DateTime getDate(ResultSet r, String fieldName) throws SQLException {
            final Timestamp resultStamp = r.getTimestamp(fieldName);
            return r.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
        }

        @Override
        public ISubscriptionBundle map(int arg, ResultSet r,
                StatementContext ctx) throws SQLException {

            UUID id = UUID.fromString(r.getString("id"));
            String name = r.getString("name");
            UUID accountId = UUID.fromString(r.getString("account_id"));
            DateTime startDate = getDate(r, "start_dt");
            SubscriptionBundle bundle = new SubscriptionBundle(id, name, accountId, startDate);
            return bundle;
        }

    }
}
