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
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.ning.billing.util.entity.BinderBase;
import com.ning.billing.util.entity.MapperBase;
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
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.util.notificationq.DefaultNotification;
import com.ning.billing.util.notificationq.Notification;
import com.ning.billing.util.notificationq.NotificationLifecycle.NotificationLifecycleState;

@ExternalizedSqlViaStringTemplate3()
public interface NotificationSqlDao extends Transactional<NotificationSqlDao>, CloseMe {

    //
    // APIs for event notifications
    //
    @SqlQuery
    @Mapper(NotificationSqlMapper.class)
    public List<Notification> getReadyNotifications(@Bind("now") Date now, @Bind("max") int max, @Bind("queue_name") String queueName);

    @SqlUpdate
    public int claimNotification(@Bind("owner") String owner, @Bind("next_available") Date nextAvailable, @Bind("id") long id, @Bind("now") Date now);

    @SqlUpdate
    public void clearNotification(@Bind("id") long id, @Bind("owner") String owner);

    @SqlUpdate
    public void removeNotificationsByKey(@Bind("notification_key") String key);
    
    @SqlUpdate
    public void insertNotification(@Bind(binder = NotificationSqlDaoBinder.class) Notification evt);

    @SqlUpdate
    public void insertClaimedHistory(@Bind("sequence_id") int sequenceId, @Bind("owner") String owner, @Bind("claimed_dt") Date clainedDate, @Bind("notification_id") String notificationId);

    public static class NotificationSqlDaoBinder extends BinderBase implements Binder<Bind, Notification> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, Notification evt) {
            stmt.bind("notification_id", evt.getUUID().toString());
            stmt.bind("created_dt", getDate(new DateTime()));
            stmt.bind("notification_key", evt.getNotificationKey());
            stmt.bind("effective_dt", getDate(evt.getEffectiveDate()));
            stmt.bind("queue_name", evt.getQueueName());
            stmt.bind("processing_available_dt", getDate(evt.getNextAvailableDate()));
            stmt.bind("processing_owner", evt.getOwner());
            stmt.bind("processing_state", NotificationLifecycleState.AVAILABLE.toString());
        }
    }


    public static class NotificationSqlMapper extends MapperBase implements ResultSetMapper<Notification> {
        @Override
        public Notification map(int index, ResultSet r, StatementContext ctx)
        throws SQLException {

            final long id = r.getLong("id");
            final UUID uuid = UUID.fromString(r.getString("notification_id"));
            final String notificationKey = r.getString("notification_key");
            final String queueName = r.getString("queue_name");
            final DateTime effectiveDate = getDate(r, "effective_dt");
            final DateTime nextAvailableDate = getDate(r, "processing_available_dt");
            final String processingOwner = r.getString("processing_owner");
            final NotificationLifecycleState processingState = NotificationLifecycleState.valueOf(r.getString("processing_state"));

            return new DefaultNotification(id, uuid, processingOwner, queueName, nextAvailableDate,
                    processingState, notificationKey, effectiveDate);

        }
    }
}
