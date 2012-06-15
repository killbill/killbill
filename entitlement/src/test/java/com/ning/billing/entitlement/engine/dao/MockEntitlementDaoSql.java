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

package com.ning.billing.entitlement.engine.dao;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

import com.google.inject.Inject;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationQueueService;

public class MockEntitlementDaoSql extends AuditedEntitlementDao implements MockEntitlementDao {

    private final ResetSqlDao resetDao;

    @Inject
    public MockEntitlementDaoSql(final IDBI dbi, final Clock clock, final AddonUtils addonUtils, final NotificationQueueService notificationQueueService,
                                 final Bus eventBus) {
        super(dbi, clock, addonUtils, notificationQueueService, eventBus);
        this.resetDao = dbi.onDemand(ResetSqlDao.class);
    }


    @Override
    public void reset() {
        resetDao.inTransaction(new Transaction<Void, ResetSqlDao>() {

            @Override
            public Void inTransaction(final ResetSqlDao dao, final TransactionStatus status)
                    throws Exception {
                resetDao.resetEvents();
                resetDao.resetSubscriptions();
                resetDao.resetBundles();
                resetDao.resetClaimedNotifications();
                resetDao.resetNotifications();
                return null;
            }
        });
    }

    public static interface ResetSqlDao extends Transactional<ResetSqlDao>, CloseMe {

        @SqlUpdate("truncate table subscription_events")
        public void resetEvents();

        @SqlUpdate("truncate table subscriptions")
        public void resetSubscriptions();

        @SqlUpdate("truncate table bundles")
        public void resetBundles();

        @SqlUpdate("truncate table notifications")
        public void resetNotifications();

        @SqlUpdate("truncate table claimed_notifications")
        public void resetClaimedNotifications();

    }
}
