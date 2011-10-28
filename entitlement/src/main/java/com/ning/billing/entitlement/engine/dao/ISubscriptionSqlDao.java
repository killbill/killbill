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

import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.ISubscription;
import com.ning.billing.entitlement.api.user.Subscription;

public interface ISubscriptionSqlDao extends Transactional<ISubscriptionSqlDao>, CloseMe, Transmogrifier {

    final static String SUBSCRIPTION_FIELDS = " id, bundle_id, category, start_dt, bundle_start_dt, active_version, ctd_dt, ptd_dt ";
    final static String SUBSCRIPTION_VALUES = " :id, :bundle_id, :category, :start_dt, :bundle_start_dt, :active_version, :ctd_dt, :ptd_dt ";


    @SqlUpdate("insert into subscriptions (" + SUBSCRIPTION_FIELDS +") value (" + SUBSCRIPTION_VALUES + ")")
    public void insertSubscription(@Bind(binder = ISubscriptionDaoBinder.class) Subscription sub);

    @SqlQuery("select " + SUBSCRIPTION_FIELDS + " from subscriptions where id = :id")
    @Mapper(ISubscriptionDaoSqlMapper.class)
    public ISubscription getSubscriptionFromId(@Bind("id") String id);

    @SqlQuery("select " + SUBSCRIPTION_FIELDS + "from subscriptions where bundle_id = :bundle_id")
    @Mapper(ISubscriptionDaoSqlMapper.class)
    public List<ISubscription> getSubscriptionsFromBundleId(@Bind("bundle_id") String bundleId);

    @SqlUpdate("update subscriptions set active_version = :active_version, ctd_dt = :ctd_dt, ptd_dt = :ptd_dt")
    public void updateSubscription(@Bind("active_version") long activeVersion, @Bind("ctd_dt") Date ctd, @Bind("ptd_dt") Date ptd);

    public static class ISubscriptionDaoBinder implements Binder<Bind, Subscription> {

        private Date getDate(DateTime dateTime) {
            return dateTime == null ? null : dateTime.toDate();
        }

        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, Subscription sub) {
            stmt.bind("id", sub.getId().toString());
            stmt.bind("bundle_id", sub.getBundleId().toString());
            stmt.bind("category", sub.getCategory().toString());
            stmt.bind("start_dt", getDate(sub.getStartDate()));
            stmt.bind("bundle_start_dt", getDate(sub.getBundleStartDate()));
            stmt.bind("active_version", sub.getActiveVersion());
            stmt.bind("ctd_dt", getDate(sub.getPaidThroughDate()));
            stmt.bind("ptd_dt", getDate(sub.getPaidThroughDate()));
        }
    }

    public static class ISubscriptionDaoSqlMapper implements ResultSetMapper<Subscription> {

        private DateTime getDate(ResultSet r, String fieldName) throws SQLException {
            final Timestamp resultStamp = r.getTimestamp(fieldName);
            return r.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
        }

        @Override
        public Subscription map(int arg0, ResultSet r, StatementContext ctx)
                throws SQLException {

            UUID id = UUID.fromString(r.getString("id"));
            UUID bundleId = UUID.fromString(r.getString("bundle_id"));
            ProductCategory category = ProductCategory.valueOf(r.getString("category"));
            DateTime bundleStartDate = getDate(r, "bundle_start_dt");
            DateTime startDate = getDate(r, "start_dt");
            DateTime ctd = getDate(r, "ctd_dt");
            DateTime ptd = getDate(r, "ptd_dt");
            long activeVersion = r.getLong("active_version");

            Subscription subscription = new Subscription(id, bundleId, category, bundleStartDate, startDate, ctd, ptd, activeVersion);
            return subscription;
        }
    }


}
