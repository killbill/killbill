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

import com.ning.billing.entitlement.engine.dao.EntitlementEventSqlDao.EventSqlMapper;
import com.ning.billing.entitlement.engine.dao.model.EntitlementEventModelDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
@RegisterMapper(EventSqlMapper.class)
public interface EntitlementEventSqlDao extends EntitySqlDao<EntitlementEventModelDao, EntitlementEvent> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void unactiveEvent(@Bind("id") String id,
                              @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void reactiveEvent(@Bind("id") String id,
                              @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updateVersion(@Bind("id") String id,
                              @Bind("currentVersion") Long currentVersion,
                              @BindBean final InternalCallContext context);

    @SqlQuery
    public List<EntitlementEventModelDao> getFutureActiveEventForSubscription(@Bind("subscriptionId") String subscriptionId,
                                                                              @Bind("now") Date now,
                                                                              @BindBean final InternalTenantContext context);

    @SqlQuery
    public List<EntitlementEventModelDao> getEventsForSubscription(@Bind("subscriptionId") String subscriptionId,
                                                                   @BindBean final InternalTenantContext context);

    public static class EventSqlMapper extends MapperBase implements ResultSetMapper<EntitlementEventModelDao> {

        @Override
        public EntitlementEventModelDao map(final int index, final ResultSet r, final StatementContext ctx)
                throws SQLException {

            final long totalOrdering = r.getLong("record_id");
            final UUID id = UUID.fromString(r.getString("id"));
            final EventType eventType = EventType.valueOf(r.getString("event_type"));
            final ApiEventType userType = (eventType == EventType.API_USER) ? ApiEventType.valueOf(r.getString("user_type")) : null;
            final DateTime createdDate = getDateTime(r, "created_date");
            final DateTime updatedDate = getDateTime(r, "updated_date");
            final DateTime requestedDate = getDateTime(r, "requested_date");
            final DateTime effectiveDate = getDateTime(r, "effective_date");
            final UUID subscriptionId = UUID.fromString(r.getString("subscription_id"));
            final String planName = r.getString("plan_name");
            final String phaseName = r.getString("phase_name");
            final String priceListName = r.getString("price_list_name");
            final long currentVersion = r.getLong("current_version");
            final boolean isActive = r.getBoolean("is_active");
            final UUID userToken = r.getString("user_token") != null ? UUID.fromString(r.getString("user_token")) : null;

            return new EntitlementEventModelDao(id, totalOrdering, eventType, userType, requestedDate, effectiveDate, subscriptionId,
                                                planName, phaseName, priceListName, currentVersion, isActive, createdDate, updatedDate);

        }
    }
}
