/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.beatrix.extbus.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;
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
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.ObjectType;
import com.ning.billing.beatrix.bus.api.ExtBusEventType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.queue.PersistentQueueEntryLifecycle.PersistentQueueEntryLifecycleState;

@ExternalizedSqlViaStringTemplate3()
public interface ExtBusSqlDao extends Transactional<ExtBusSqlDao>, CloseMe {

    @SqlQuery
    @Mapper(ExtBusSqlMapper.class)
    public ExtBusEventEntry getNextBusExtEventEntry(@Bind("max") int max,
                                                    @Bind("owner") String owner,
                                                    @Bind("now") Date now,
                                                    @InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    public int claimBusExtEvent(@Bind("owner") String owner,
                                @Bind("nextAvailable") Date nextAvailable,
                                @Bind("recordId") Long id,
                                @Bind("now") Date now,
                                @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void clearBusExtEvent(@Bind("recordId") Long id,
                                 @Bind("owner") String owner,
                                 @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void removeBusExtEventsById(@Bind("recordId") Long id,
                                       @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void insertBusExtEvent(@Bind(binder = ExtBusSqlBinder.class) ExtBusEventEntry evt,
                                  @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void insertClaimedExtHistory(@Bind("ownerId") String owner,
                                        @Bind("claimedDate") Date claimedDate,
                                        @Bind("busEventId") long id,
                                        @InternalTenantContextBinder final InternalCallContext context);

    public static class ExtBusSqlBinder extends BinderBase implements Binder<Bind, ExtBusEventEntry> {

        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final ExtBusEventEntry evt) {
            stmt.bind("eventType", evt.getExtBusType().toString());
            bindWithPotentialNullStringValue(stmt, "objectId", evt.getObjectId());
            stmt.bind("objectType", evt.getObjectType().toString());
            stmt.bind("userToken", getUUIDString(evt.getUserToken()));
            stmt.bind("createdDate", getDate(new DateTime()));
            stmt.bind("creatingOwner", evt.getCreatedOwner());
            stmt.bind("processingAvailableDate", getDate(evt.getNextAvailableDate()));
            stmt.bind("processingOwner", evt.getOwner());
            stmt.bind("processingState", PersistentQueueEntryLifecycleState.AVAILABLE.toString());
        }

        private void bindWithPotentialNullStringValue(final SQLStatement stmt, final String bindType, final Object bindValue) {
            if (bindValue == null) {
                stmt.bindNull(bindType, Types.VARCHAR);
            } else {
                stmt.bind(bindType, bindValue.toString());
            }
        }
    }

    public static class ExtBusSqlMapper extends MapperBase implements ResultSetMapper<ExtBusEventEntry> {

        @Override
        public ExtBusEventEntry map(final int index, final ResultSet r, final StatementContext ctx)
                throws SQLException {
            final Long recordId = r.getLong("record_id");
            final ExtBusEventType eventType = ExtBusEventType.valueOf(r.getString("event_type"));
            final UUID objectId = getUUID(r, "object_id");
            final ObjectType objectType = ObjectType.valueOf(r.getString("object_type"));
            final UUID userToken = getUUID(r, "user_token");
            final String createdOwner = r.getString("creating_owner");
            final DateTime nextAvailableDate = getDateTime(r, "processing_available_date");
            final String processingOwner = r.getString("processing_owner");
            final PersistentQueueEntryLifecycleState processingState = PersistentQueueEntryLifecycleState.valueOf(r.getString("processing_state"));
            final Long accountRecordId = r.getLong("account_record_id");
            final Long tenantRecordId = r.getLong("tenant_record_id");
            return new ExtBusEventEntry(recordId, createdOwner, processingOwner, nextAvailableDate, processingState,
                                        objectType, objectId, userToken, eventType, accountRecordId, tenantRecordId);
        }
    }
}
