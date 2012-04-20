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

import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.EventBaseBuilder;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventBuilder;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.*;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@ExternalizedSqlViaStringTemplate3()
public interface EventSqlDao extends Transactional<EventSqlDao>, CloseMe, Transmogrifier  {

    @SqlQuery
    @Mapper(EventSqlMapper.class)
    public EntitlementEvent getEventById(@Bind("event_id") String eventId);

    @SqlUpdate
    public void insertEvent(@Bind(binder = EventSqlDaoBinder.class) EntitlementEvent evt,
                            @CallContextBinder final CallContext context);

    @SqlUpdate
    public void unactiveEvent(@Bind("event_id")String eventId,
                              @CallContextBinder final CallContext context);

    @SqlUpdate
    public void reactiveEvent(@Bind("event_id")String eventId,
                              @CallContextBinder final CallContext context);

    @SqlUpdate
    public void updateVersion(@Bind("event_id")String eventId,
                              @Bind("current_version") Long currentVersion, 
                              @CallContextBinder final CallContext context);
    
    @SqlQuery
    @Mapper(EventSqlMapper.class)
    public List<EntitlementEvent> getFutureActiveEventForSubscription(@Bind("subscription_id") String subscriptionId, @Bind("now") Date now);

    @SqlQuery
    @Mapper(EventSqlMapper.class)
    public List<EntitlementEvent> getEventsForSubscription(@Bind("subscription_id") String subscriptionId);

    public static class EventSqlDaoBinder extends BinderBase implements Binder<Bind, EntitlementEvent> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, EntitlementEvent evt) {
        	
        	String planName = null;
        	String phaseName = null;
        	String priceListName = null;
        	String userType = null;
        	if (evt.getType() == EventType.API_USER) {
        		ApiEvent userEvent = (ApiEvent) evt;
            	planName = userEvent.getEventPlan();
            	phaseName = userEvent.getEventPlanPhase();
            	priceListName = userEvent.getPriceList();
            	userType = userEvent.getEventType().toString();
        	} else {
        		phaseName = ((PhaseEvent) evt).getPhase();
        	}
            stmt.bind("event_id", evt.getId().toString());
            stmt.bind("event_type", evt.getType().toString());
            stmt.bind("user_type", userType);
            stmt.bind("requested_dt", getDate(evt.getRequestedDate()));
            stmt.bind("effective_dt", getDate(evt.getEffectiveDate()));
            stmt.bind("subscription_id", evt.getSubscriptionId().toString());
            stmt.bind("plan_name", planName);
            stmt.bind("phase_name", phaseName);
            stmt.bind("plist_name", priceListName);
            stmt.bind("current_version", evt.getActiveVersion());
            stmt.bind("is_active", evt.isActive());
        }
    }

    public static class EventSqlMapper extends MapperBase implements ResultSetMapper<EntitlementEvent> {
        @Override
        public EntitlementEvent map(int index, ResultSet r, StatementContext ctx)
        throws SQLException {

            long totalOrdering = r.getLong("id");
            UUID id = UUID.fromString(r.getString("event_id"));
            EventType eventType = EventType.valueOf(r.getString("event_type"));
            ApiEventType userType = (eventType == EventType.API_USER) ? ApiEventType.valueOf(r.getString("user_type")) : null;
            DateTime createdDate = getDate(r, "created_date");
            DateTime requestedDate = getDate(r, "requested_dt");
            DateTime effectiveDate = getDate(r, "effective_dt");
            UUID subscriptionId = UUID.fromString(r.getString("subscription_id"));
            String planName = r.getString("plan_name");
            String phaseName = r.getString("phase_name");
            String priceListName = r.getString("plist_name");
            long currentVersion = r.getLong("current_version");
            boolean isActive = r.getBoolean("is_active");
            UUID userToken = r.getString("user_token") != null ? UUID.fromString(r.getString("user_token")) : null;
            
            EventBaseBuilder<?> base = ((eventType == EventType.PHASE) ?
                    new PhaseEventBuilder() :
                        new ApiEventBuilder())
                        .setTotalOrdering(totalOrdering)
                        .setUuid(id)
                        .setSubscriptionId(subscriptionId)
                        .setRequestedDate(requestedDate)
                        .setEffectiveDate(effectiveDate)
                        .setProcessedDate(createdDate)
                        .setActiveVersion(currentVersion)
                        .setActive(isActive);

            EntitlementEvent result = null;
            if (eventType == EventType.PHASE) {
                result = new PhaseEventData(new PhaseEventBuilder(base).setPhaseName(phaseName));
            } else if (eventType == EventType.API_USER) {
                ApiEventBuilder builder = new ApiEventBuilder(base)
                    .setEventPlan(planName)
                    .setEventPlanPhase(phaseName)
                    .setEventPriceList(priceListName)
                    .setEventType(userType)
                    .setUserToken(userToken)
                    .setFromDisk(true);

                if (userType == ApiEventType.CREATE) {
                    result = new ApiEventCreate(builder);
                } else if (userType == ApiEventType.RE_CREATE) {
                    result = new ApiEventReCreate(builder);
                } else if (userType == ApiEventType.MIGRATE_ENTITLEMENT) {
                    result = new ApiEventMigrateEntitlement(builder);
                } else if (userType == ApiEventType.MIGRATE_BILLING) {
                    result = new ApiEventMigrateBilling(builder);
                } else if (userType == ApiEventType.CHANGE) {
                    result = new ApiEventChange(builder);
                } else if (userType == ApiEventType.CANCEL) {
                    result = new ApiEventCancel(builder);
                } else if (userType == ApiEventType.RE_CREATE) {
                    result = new ApiEventReCreate(builder);
                } else if (userType == ApiEventType.UNCANCEL) {
                    result = new ApiEventUncancel(builder);
                }
            } else {
                throw new EntitlementError(String.format("Can't deserialize event %s", eventType));
            }
            return result;
        }
    }
}
