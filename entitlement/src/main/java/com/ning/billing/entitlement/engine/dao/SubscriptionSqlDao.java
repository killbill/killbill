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
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.engine.dao.SubscriptionSqlDao.SubscriptionMapper;
import com.ning.billing.entitlement.engine.dao.model.SubscriptionModelDao;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper(SubscriptionMapper.class)
public interface SubscriptionSqlDao extends EntitySqlDao<SubscriptionModelDao, Subscription> {

    @SqlQuery
    public List<SubscriptionModelDao> getSubscriptionsFromBundleId(@Bind("bundleId") String bundleId,
                                                                   @BindBean final InternalTenantContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updateChargedThroughDate(@Bind("id") String id, @Bind("chargedThroughDate") Date chargedThroughDate,
                                         @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateActiveVersion(@Bind("id") String id, @Bind("activeVersion") long activeVersion,
                             @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updateForRepair(@Bind("id") String id, @Bind("activeVersion") long activeVersion,
                                @Bind("startDate") Date startDate,
                                @Bind("bundleStartDate") Date bundleStartDate,
                                @BindBean final InternalCallContext context);

    public static class SubscriptionMapper extends MapperBase implements ResultSetMapper<SubscriptionModelDao> {

        @Override
        public SubscriptionModelDao map(final int arg0, final ResultSet r, final StatementContext ctx)
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
            return new SubscriptionModelDao(id, bundleId, category, startDate, bundleStartDate, activeVersion, ctd, ptd, createdDate, updatedDate);

        }
    }
}
