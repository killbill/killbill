/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.subscription.engine.dao;

import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.skife.jdbi.v2.IDBI;

import org.killbill.bus.api.PersistentBus;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.dao.NonEntityDao;

import com.google.inject.Inject;

public class MockSubscriptionDaoSql extends DefaultSubscriptionDao {

    @Inject
    public MockSubscriptionDaoSql(final IDBI dbi, final Clock clock, final AddonUtils addonUtils, final NotificationQueueService notificationQueueService,
                                  final PersistentBus eventBus, final CatalogService catalogService, final CacheControllerDispatcher cacheControllerDispatcher,
                                  final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory) {
        super(dbi, clock, addonUtils, notificationQueueService, eventBus, catalogService, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);
    }
}
