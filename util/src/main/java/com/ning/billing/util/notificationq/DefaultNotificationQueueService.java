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

import com.ning.billing.util.config.NotificationConfig;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;

import com.google.inject.Inject;

public class DefaultNotificationQueueService extends NotificationQueueServiceBase {
    private final IDBI dbi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultNotificationQueueService(final IDBI dbi, final Clock clock, final InternalCallContextFactory internalCallContextFactory) {
        super(clock);
        this.dbi = dbi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    protected NotificationQueue createNotificationQueueInternal(final String svcName,
                                                                final String queueName,
                                                                final NotificationQueueHandler handler,
                                                                final NotificationConfig config) {
        return new DefaultNotificationQueue(dbi, clock, svcName, queueName, handler, config, internalCallContextFactory);
    }
}
