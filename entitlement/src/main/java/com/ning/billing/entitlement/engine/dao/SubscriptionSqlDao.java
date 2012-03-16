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

import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
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
public interface SubscriptionSqlDao extends Transactional<SubscriptionSqlDao>, CloseMe, Transmogrifier {


	@SqlUpdate
    public void insertSubscription(@Bind(binder = ISubscriptionDaoBinder.class) SubscriptionData sub);

    @SqlUpdate
    public void removeSubscription(@Bind("id") String id);

    @SqlQuery
    @Mapper(ISubscriptionDaoSqlMapper.class)
    public Subscription getSubscriptionFromId(@Bind("id") String id);

    @SqlQuery
    @Mapper(ISubscriptionDaoSqlMapper.class)
    public List<Subscription> getSubscriptionsFromBundleId(@Bind("bundle_id") String bundleId);

    @SqlUpdate
    public void updateSubscription(@Bind("id") String id, @Bind("active_version") long activeVersion, @Bind("ctd_dt") Date ctd, @Bind("ptd_dt") Date ptd);

    public static class ISubscriptionDaoBinder implements Binder<Bind, SubscriptionData> {

        private Date getDate(DateTime dateTime) {
            return dateTime == null ? null : dateTime.toDate();
        }

        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, SubscriptionData sub) {
            stmt.bind("id", sub.getId().toString());
            stmt.bind("bundle_id", sub.getBundleId().toString());
            stmt.bind("category", sub.getCategory().toString());
            stmt.bind("start_dt", getDate(sub.getStartDate()));
            stmt.bind("bundle_start_dt", getDate(sub.getBundleStartDate()));
            stmt.bind("active_version", sub.getActiveVersion());
            stmt.bind("ctd_dt", getDate(sub.getChargedThroughDate()));
            stmt.bind("ptd_dt", getDate(sub.getPaidThroughDate()));
        }
    }

    public static class ISubscriptionDaoSqlMapper implements ResultSetMapper<SubscriptionData> {

        private DateTime getDate(ResultSet r, String fieldName) throws SQLException {
            final Timestamp resultStamp = r.getTimestamp(fieldName);
            return r.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
        }

        @Override
        public SubscriptionData map(int arg0, ResultSet r, StatementContext ctx)
                throws SQLException {

            UUID id = UUID.fromString(r.getString("id"));
            UUID bundleId = UUID.fromString(r.getString("bundle_id"));
            ProductCategory category = ProductCategory.valueOf(r.getString("category"));
            DateTime bundleStartDate = getDate(r, "bundle_start_dt");
            DateTime startDate = getDate(r, "start_dt");
            DateTime ctd = getDate(r, "ctd_dt");
            DateTime ptd = getDate(r, "ptd_dt");
            long activeVersion = r.getLong("active_version");

            SubscriptionData subscription = new SubscriptionData(new SubscriptionBuilder()
            .setId(id)
            .setBundleId(bundleId)
            .setCategory(category)
            .setBundleStartDate(bundleStartDate)
            .setStartDate(startDate)
            .setActiveVersion(activeVersion)
            .setChargedThroughDate(ctd)
            .setPaidThroughDate(ptd));
            return subscription;
        }
    }
}
