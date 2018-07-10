/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.overdue.notification;

import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.jdbi.v2.IDBI;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class OverdueAsyncBusPoster extends DefaultOverduePosterBase {

    @Inject
    public OverdueAsyncBusPoster(final NotificationQueueService notificationQueueService,
                                 final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher,
                                 final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory) {
        super(notificationQueueService, dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);
    }

    @Override
    protected <T extends OverdueCheckNotificationKey> boolean cleanupFutureNotificationsFormTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                                                                        final Iterable<NotificationEventWithMetadata<T>> futureNotifications,
                                                                                                        final DateTime futureNotificationTime,
                                                                                                        final NotificationQueue overdueQueue) {
        // If we already have notification for that account we don't insert the new one
        // Note that this is slightly incorrect because we could for instance already have a REFRESH and insert a CLEAR, but if that were the case,
        // if means overdue state would change very rapidly and the behavior would anyway be non deterministic
        // Note: don't use isEmpty() to go through all results to close the connection
        return Iterables.size(futureNotifications) == 0;
    }
}
