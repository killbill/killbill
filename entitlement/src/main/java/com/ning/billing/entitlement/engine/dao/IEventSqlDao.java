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
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.entitlement.events.EventBaseBuilder;
import com.ning.billing.entitlement.events.IEntitlementEvent;
import com.ning.billing.entitlement.events.IEntitlementEvent.EventType;
import com.ning.billing.entitlement.events.IEventLifecycle.IEventLifecycleState;
import com.ning.billing.entitlement.events.phase.IPhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventCreate;
import com.ning.billing.entitlement.events.user.ApiEventPause;
import com.ning.billing.entitlement.events.user.ApiEventResume;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.events.user.ApiEventUncancel;
import com.ning.billing.entitlement.events.user.IApiEvent;
import com.ning.billing.entitlement.exceptions.EntitlementError;

@ExternalizedSqlViaStringTemplate3()
public interface IEventSqlDao extends Transactional<IEventSqlDao>, CloseMe, Transmogrifier  {

    //
    // APIs for event notifications
    //
    @SqlQuery
    @Mapper(IEventSqlMapper.class)
    public List<IEntitlementEvent> getReadyEvents(@Bind("now") Date now, @Bind("max") int max);

    @SqlUpdate
    public int claimEvent(@Bind("owner") String owner, @Bind("next_available") Date nextAvailable, @Bind("event_id") String eventId, @Bind("now") Date now);

    @SqlUpdate
    public void clearEvent(@Bind("event_id") String eventId, @Bind("owner") String owner);

    @SqlUpdate
    public void insertEvent(@Bind(binder = IEventSqlDaoBinder.class) IEntitlementEvent evt);

    @SqlUpdate
    public void insertClaimedHistory(@Bind("sequence_id") int sequenceId, @Bind("owner_id") String ownerId, @Bind("hostname") String hostname, @Bind("claimed_dt") Date clainedDate, @Bind("event_id") String eventId);

    @SqlUpdate
    public void unactiveEvent(@Bind("event_id")String eventId, @Bind("now") Date now);

    @SqlUpdate
    public void reactiveEvent(@Bind("event_id")String eventId, @Bind("now") Date now);

    @SqlQuery
    @Mapper(IEventSqlMapper.class)
    public List<IEntitlementEvent> getFutureActiveEventForSubscription(@Bind("subscription_id") String subscriptionId, @Bind("now") Date now);

    @SqlQuery
    @Mapper(IEventSqlMapper.class)
    public List<IEntitlementEvent> getEventsForSubscription(@Bind("subscription_id") String subscriptionId);

    public static class IEventSqlDaoBinder implements Binder<Bind, IEntitlementEvent> {

        private Date getDate(DateTime dateTime) {
            return dateTime == null ? null : dateTime.toDate();
        }

        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, IEntitlementEvent evt) {
            stmt.bind("event_id", evt.getId().toString());
            stmt.bind("event_type", evt.getType().toString());
            stmt.bind("user_type", (evt.getType() == EventType.API_USER) ? ((IApiEvent) evt).getEventType().toString() : null);
            stmt.bind("created_dt", getDate(evt.getProcessedDate()));
            stmt.bind("updated_dt", getDate(evt.getProcessedDate()));
            stmt.bind("requested_dt", getDate(evt.getRequestedDate()));
            stmt.bind("effective_dt", getDate(evt.getEffectiveDate()));
            stmt.bind("subscription_id", evt.getSubscriptionId().toString());
            stmt.bind("plan_name", (evt.getType() == EventType.API_USER) ? ((IApiEvent) evt).getEventPlan() : null);
            stmt.bind("phase_name", (evt.getType() == EventType.API_USER) ? ((IApiEvent) evt).getEventPlanPhase() : ((IPhaseEvent) evt).getPhase());
            stmt.bind("plist_name", (evt.getType() == EventType.API_USER) ? ((IApiEvent) evt).getPriceList() : null);
            stmt.bind("current_version", evt.getActiveVersion());
            stmt.bind("is_active", evt.isActive());
            stmt.bind("processing_available_dt", getDate(evt.getNextAvailableDate()));
            stmt.bind("processing_owner", (String) null);
            stmt.bind("processing_state", IEventLifecycleState.AVAILABLE.toString());
        }
    }

    public static class IEventSqlMapper implements ResultSetMapper<IEntitlementEvent> {

        private DateTime getDate(ResultSet r, String fieldName) throws SQLException {
            final Timestamp resultStamp = r.getTimestamp(fieldName);
            return r.wasNull() ? null : new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
        }

        @Override
        public IEntitlementEvent map(int index, ResultSet r, StatementContext ctx)
        throws SQLException {

            UUID id = UUID.fromString(r.getString("event_id"));
            EventType eventType = EventType.valueOf(r.getString("event_type"));
            ApiEventType userType = (eventType == EventType.API_USER) ? ApiEventType.valueOf(r.getString("user_type")) : null;
            DateTime createdDate = getDate(r, "created_dt");
            DateTime requestedDate = getDate(r, "requested_dt");
            DateTime effectiveDate = getDate(r, "effective_dt");
            UUID subscriptionId = UUID.fromString(r.getString("subscription_id"));
            String planName = r.getString("plan_name");
            String phaseName = r.getString("phase_name");
            String priceListName = r.getString("plist_name");
            long currentVersion = r.getLong("current_version");
            boolean isActive = r.getBoolean("is_active");
            DateTime nextAvailableDate = getDate(r, "processing_available_dt");
            UUID processingOwner = (r.getString("processing_owner") != null) ? UUID.fromString(r.getString("processing_owner")) : null;
            IEventLifecycleState processingState = IEventLifecycleState.valueOf(r.getString("processing_state"));

            EventBaseBuilder<?> base = ((eventType == EventType.PHASE) ?
                    new PhaseEventBuilder() :
                        new ApiEventBuilder())
                        .setUuid(id)
                        .setSubscriptionId(subscriptionId)
                        .setRequestedDate(requestedDate)
                        .setEffectiveDate(effectiveDate)
                        .setProcessedDate(createdDate)
                        .setActiveVersion(currentVersion)
                        .setActive(isActive)
                        .setProcessingOwner(processingOwner)
                        .setNextAvailableProcessingTime(nextAvailableDate)
                        .setProcessingState(processingState);


            IEntitlementEvent result = null;
            if (eventType == EventType.PHASE) {
                EventBaseBuilder<PhaseEventBuilder> realBase = (EventBaseBuilder<PhaseEventBuilder>) base;
                result = new PhaseEvent(new PhaseEventBuilder(realBase).setPhaseName(phaseName));
            } else if (eventType == EventType.API_USER) {

                EventBaseBuilder<ApiEventBuilder> realBase = (EventBaseBuilder<ApiEventBuilder>) base;
                ApiEventBuilder builder = new ApiEventBuilder(realBase)
                    .setEventPlan(planName)
                    .setEventPlanPhase(phaseName)
                    .setEventPriceList(priceListName)
                    .setEventType(userType);

                if (userType == ApiEventType.CREATE) {
                    result = new ApiEventCreate(builder);
                } else if (userType == ApiEventType.CHANGE) {
                    result = new ApiEventChange(builder);
                } else if (userType == ApiEventType.CANCEL) {
                    result = new ApiEventCancel(builder);
                } else if (userType == ApiEventType.PAUSE) {
                    result = new ApiEventPause(builder);
                } else if (userType == ApiEventType.RESUME) {
                    result = new ApiEventResume(builder);
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
