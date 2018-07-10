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

package org.killbill.billing.subscription.engine.dao;

import javax.inject.Named;

import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.jdbi.v2.IDBI;

import com.google.inject.Inject;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class MockSubscriptionDaoSql extends DefaultSubscriptionDao {

    @Inject
    public MockSubscriptionDaoSql(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, final AddonUtils addonUtils, final NotificationQueueService notificationQueueService,
                                  final PersistentBus eventBus, final CacheControllerDispatcher cacheControllerDispatcher,
                                  final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory) {
        super(dbi, roDbi, clock, addonUtils, notificationQueueService, eventBus, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);
    }
}
