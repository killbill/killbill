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

package com.ning.billing.util.notificationq;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;

import com.google.inject.Inject;

public class DefaultNotificationQueueService extends NotificationQueueServiceBase {

    private final IDBI dbi;
    private final NonEntityDao nonEntityDao;
    private final CacheControllerDispatcher cacheControllerDispatcher;


    @Inject
    public DefaultNotificationQueueService(final IDBI dbi, final Clock clock, final NotificationQueueConfig config,
                                           final InternalCallContextFactory internalCallContextFactory, final NonEntityDao nonEntityDao, final CacheControllerDispatcher cacheControllerDispatcher) {
        super(clock, config, dbi, internalCallContextFactory);
        this.dbi = dbi;
        this.nonEntityDao = nonEntityDao;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
    }


    @Override
    protected NotificationQueue createNotificationQueueInternal(final String svcName,
                                                                final String queueName,
                                                                final NotificationQueueHandler handler) {
        return new DefaultNotificationQueue(svcName, queueName, handler, dbi, this, nonEntityDao, cacheControllerDispatcher);
    }
}
