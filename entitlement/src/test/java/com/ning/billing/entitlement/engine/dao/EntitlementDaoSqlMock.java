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

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

import com.google.inject.Inject;
import com.ning.billing.entitlement.glue.IEntitlementConfig;
import com.ning.billing.util.clock.IClock;

public class EntitlementDaoSqlMock extends EntitlementDao implements IEntitlementDaoMock {

    private final ResetSqlDao resetDao;

    @Inject
    public EntitlementDaoSqlMock(DBI dbi, IClock clock, IEntitlementConfig config) {
        super(dbi, clock, config);
        this.resetDao = dbi.onDemand(ResetSqlDao.class);
    }

    @Override
    public void reset() {
        resetDao.inTransaction(new Transaction<Void, ResetSqlDao>() {

            @Override
            public Void inTransaction(ResetSqlDao dao, TransactionStatus status)
                    throws Exception {
                resetDao.resetEvents();
                resetDao.resetClaimedEvents();
                resetDao.resetSubscriptions();
                resetDao.resetBundles();
                return null;
            }
        });
    }

    public static interface ResetSqlDao extends Transactional<ResetSqlDao>, CloseMe {

        @SqlUpdate("truncate table events")
        public void resetEvents();

        @SqlUpdate("truncate table claimed_events")
        public void resetClaimedEvents();

        @SqlUpdate("truncate table subscriptions")
        public void resetSubscriptions();

        @SqlUpdate("truncate table bundles")
        public void resetBundles();
    }

}
