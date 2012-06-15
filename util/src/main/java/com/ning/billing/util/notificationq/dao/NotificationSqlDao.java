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

package com.ning.billing.util.notificationq.dao;

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
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.notificationq.DefaultNotification;
import com.ning.billing.util.notificationq.Notification;
import com.ning.billing.util.queue.PersistentQueueEntryLifecycle.PersistentQueueEntryLifecycleState;

@ExternalizedSqlViaStringTemplate3()
public interface NotificationSqlDao extends Transactional<NotificationSqlDao>, CloseMe {

    //
    // APIs for event notifications
    //
    @SqlQuery
    @Mapper(NotificationSqlMapper.class)
    public List<Notification> getReadyNotifications(@Bind("now") Date now, @Bind("owner") String owner, @Bind("max") int max, @Bind("queueName") String queueName);

    @SqlUpdate
    public int claimNotification(@Bind("owner") String owner, @Bind("nextAvailable") Date nextAvailable,
                                 @Bind("id") String id, @Bind("now") Date now);

    @SqlUpdate
    public void clearNotification(@Bind("id") String id, @Bind("owner") String owner);

    @SqlUpdate
    public void removeNotificationsByKey(@Bind("notificationKey") String key);

    @SqlUpdate
    public void insertNotification(@Bind(binder = NotificationSqlDaoBinder.class) Notification evt);

    @SqlUpdate
    public void insertClaimedHistory(@Bind("ownerId") String ownerId, @Bind("claimedDate") Date claimedDate, @Bind("notificationId") String notificationId);

    public static class NotificationSqlDaoBinder extends BinderBase implements Binder<Bind, Notification> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final Notification evt) {
            stmt.bind("id", evt.getId().toString());
            stmt.bind("createdDate", getDate(new DateTime()));
            stmt.bind("creatingOwner", evt.getCreatedOwner());
            stmt.bind("notificationKey", evt.getNotificationKey());
            stmt.bind("effectiveDate", getDate(evt.getEffectiveDate()));
            stmt.bind("queueName", evt.getQueueName());
            stmt.bind("processingAvailableDate", getDate(evt.getNextAvailableDate()));
            stmt.bind("processingOwner", evt.getOwner());
            stmt.bind("processingState", PersistentQueueEntryLifecycleState.AVAILABLE.toString());
        }
    }


    public static class NotificationSqlMapper extends MapperBase implements ResultSetMapper<Notification> {
        @Override
        public Notification map(final int index, final ResultSet r, final StatementContext ctx)
                throws SQLException {

            final Long ordering = r.getLong("record_id");
            final UUID id = getUUID(r, "id");
            final String createdOwner = r.getString("creating_owner");
            final String notificationKey = r.getString("notification_key");
            final String queueName = r.getString("queue_name");
            final DateTime effectiveDate = getDate(r, "effective_date");
            final DateTime nextAvailableDate = getDate(r, "processing_available_date");
            final String processingOwner = r.getString("processing_owner");
            final PersistentQueueEntryLifecycleState processingState = PersistentQueueEntryLifecycleState.valueOf(r.getString("processing_state"));

            return new DefaultNotification(ordering, id, createdOwner, processingOwner, queueName, nextAvailableDate,
                                           processingState, notificationKey, effectiveDate);

        }
    }
}
