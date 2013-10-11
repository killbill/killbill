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

package com.ning.billing.ovedue.notification;

import java.util.Collection;
import java.util.Iterator;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.clock.Clock;
import com.ning.billing.notificationq.api.NotificationEventWithMetadata;
import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueService;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.inject.Inject;

public class OverdueCheckPoster extends DefaultOverduePosterBase {

    @Inject
    public OverdueCheckPoster(final NotificationQueueService notificationQueueService,
                                    final IDBI dbi, final Clock clock,
                                    final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        super(notificationQueueService, dbi, clock, cacheControllerDispatcher, nonEntityDao);
    }

    @Override
    protected <T extends OverdueCheckNotificationKey> boolean cleanupFutureNotificationsFormTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                                                                                        final Collection<NotificationEventWithMetadata<T>> futureNotifications,
                                                                                                        final DateTime futureNotificationTime, final NotificationQueue overdueQueue) {

        boolean shouldInsertNewNotification = true;
        if (futureNotifications.size() > 0) {
            // Results are ordered by effective date asc
            final DateTime earliestExistingNotificationDate = futureNotifications.iterator().next().getEffectiveDate();

            final int minIndexToDeleteFrom;
            if (earliestExistingNotificationDate.isBefore(futureNotificationTime)) {
                // We don't have to insert a new one. For sanity, delete any other future notification
                minIndexToDeleteFrom = 1;
                shouldInsertNewNotification = false;
            } else {
                // We win - we are before any other already recorded. Delete all others.
                minIndexToDeleteFrom = 0;
            }

            int index = 0;
            final Iterator<NotificationEventWithMetadata<T>> it = futureNotifications.iterator();
            while (it.hasNext()) {
                final NotificationEventWithMetadata<T> cur = it.next();
                if (minIndexToDeleteFrom <= index) {
                    overdueQueue.removeNotificationFromTransaction(entitySqlDaoWrapperFactory.getSqlDao(), cur.getRecordId());
                }
                index++;
            }
        }
        return shouldInsertNewNotification;
    }

}
