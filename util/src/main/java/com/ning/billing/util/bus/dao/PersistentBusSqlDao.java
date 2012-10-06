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

package com.ning.billing.util.bus.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

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

import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.queue.PersistentQueueEntryLifecycle.PersistentQueueEntryLifecycleState;

@ExternalizedSqlViaStringTemplate3()
public interface PersistentBusSqlDao extends Transactional<PersistentBusSqlDao>, CloseMe {

    @SqlQuery
    @Mapper(PersistentBusSqlMapper.class)
    public BusEventEntry getNextBusEventEntry(@Bind("max") int max,
                                              @Bind("owner") String owner,
                                              @Bind("now") Date now,
                                              @InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    public int claimBusEvent(@Bind("owner") String owner,
                             @Bind("nextAvailable") Date nextAvailable,
                             @Bind("recordId") Long id,
                             @Bind("now") Date now,
                             @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void clearBusEvent(@Bind("recordId") Long id,
                              @Bind("owner") String owner,
                              @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void removeBusEventsById(@Bind("recordId") Long id,
                                    @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void insertBusEvent(@Bind(binder = PersistentBusSqlBinder.class) BusEventEntry evt,
                               @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    public void insertClaimedHistory(@Bind("ownerId") String owner,
                                     @Bind("claimedDate") Date claimedDate,
                                     @Bind("busEventId") long id,
                                     @InternalTenantContextBinder final InternalCallContext context);

    public static class PersistentBusSqlBinder extends BinderBase implements Binder<Bind, BusEventEntry> {

        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final BusEventEntry evt) {
            stmt.bind("className", evt.getBusEventClass());
            stmt.bind("eventJson", evt.getBusEventJson());
            stmt.bind("createdDate", getDate(new DateTime()));
            stmt.bind("creatingOwner", evt.getCreatedOwner());
            stmt.bind("processingAvailableDate", getDate(evt.getNextAvailableDate()));
            stmt.bind("processingOwner", evt.getOwner());
            stmt.bind("processingState", PersistentQueueEntryLifecycleState.AVAILABLE.toString());
        }
    }

    public static class PersistentBusSqlMapper extends MapperBase implements ResultSetMapper<BusEventEntry> {

        @Override
        public BusEventEntry map(final int index, final ResultSet r, final StatementContext ctx)
                throws SQLException {

            final Long recordId = r.getLong("record_id");
            final String className = r.getString("class_name");
            final String createdOwner = r.getString("creating_owner");
            final String eventJson = r.getString("event_json");
            final DateTime nextAvailableDate = getDateTime(r, "processing_available_date");
            final String processingOwner = r.getString("processing_owner");
            final PersistentQueueEntryLifecycleState processingState = PersistentQueueEntryLifecycleState.valueOf(r.getString("processing_state"));
            final Long accountRecordId = r.getLong("account_record_id");
            final Long tenantRecordId = r.getLong("tenant_record_id");

            return new BusEventEntry(recordId, createdOwner, processingOwner, nextAvailableDate, processingState, className,
                                     eventJson, accountRecordId, tenantRecordId);
        }
    }
}
