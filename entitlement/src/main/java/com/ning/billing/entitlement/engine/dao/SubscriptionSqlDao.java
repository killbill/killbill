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

import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.AuditSqlDao;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;

@ExternalizedSqlViaStringTemplate3()
public interface SubscriptionSqlDao extends Transactional<SubscriptionSqlDao>, AuditSqlDao, CloseMe, Transmogrifier {

    @SqlUpdate
    public void insertSubscription(@Bind(binder = SubscriptionBinder.class) SubscriptionData sub,
                                   @InternalTenantContextBinder final InternalCallContext context);

    @SqlQuery
    @Mapper(SubscriptionMapper.class)
    public Subscription getSubscriptionFromId(@Bind("id") String id,
                                              @InternalTenantContextBinder final InternalTenantContext context);

    @SqlQuery
    @Mapper(SubscriptionMapper.class)
    public List<Subscription> getSubscriptionsFromBundleId(@Bind("bundleId") String bundleId,
                                                           @InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    public void updateChargedThroughDate(@Bind("id") String id, @Bind("chargedThroughDate") Date chargedThroughDate,
                                         @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    void updateActiveVersion(@Bind("id") String id, @Bind("activeVersion") long activeVersion,
                             @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void updateForRepair(@Bind("id") String id, @Bind("activeVersion") long activeVersion,
                                @Bind("startDate") Date startDate,
                                @Bind("bundleStartDate") Date bundleStartDate,
                                @InternalTenantContextBinder final InternalCallContext context);

    public static class SubscriptionBinder extends BinderBase implements Binder<Bind, SubscriptionData> {

        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final SubscriptionData sub) {
            stmt.bind("id", sub.getId().toString());
            stmt.bind("bundleId", sub.getBundleId().toString());
            stmt.bind("category", sub.getCategory().toString());
            stmt.bind("startDate", getDate(sub.getAlignStartDate()));
            stmt.bind("bundleStartDate", getDate(sub.getBundleStartDate()));
            stmt.bind("activeVersion", sub.getActiveVersion());
            stmt.bind("chargedThroughDate", getDate(sub.getChargedThroughDate()));
            stmt.bind("paidThroughDate", getDate(sub.getPaidThroughDate()));
        }
    }

    public static class SubscriptionMapper extends MapperBase implements ResultSetMapper<SubscriptionData> {

        @Override
        public SubscriptionData map(final int arg0, final ResultSet r, final StatementContext ctx)
                throws SQLException {
            final UUID id = UUID.fromString(r.getString("id"));
            final UUID bundleId = UUID.fromString(r.getString("bundle_id"));
            final ProductCategory category = ProductCategory.valueOf(r.getString("category"));
            final DateTime bundleStartDate = getDateTime(r, "bundle_start_date");
            final DateTime startDate = getDateTime(r, "start_date");
            final DateTime ctd = getDateTime(r, "charged_through_date");
            final DateTime ptd = getDateTime(r, "paid_through_date");
            final long activeVersion = r.getLong("active_version");
            final DateTime createdDate = getDateTime(r, "created_date");
            final DateTime updatedDate = getDateTime(r, "updated_date");
            return new SubscriptionData(new SubscriptionBuilder()
                                                .setId(id)
                                                .setBundleId(bundleId)
                                                .setCategory(category)
                                                .setCreatedDate(createdDate)
                                                .setUpdatedDate(updatedDate)
                                                .setBundleStartDate(bundleStartDate)
                                                .setAlignStartDate(startDate)
                                                .setActiveVersion(activeVersion)
                                                .setChargedThroughDate(ctd)
                                                .setPaidThroughDate(ptd));
        }
    }
}
