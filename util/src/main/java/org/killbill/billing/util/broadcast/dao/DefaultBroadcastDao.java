/*
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

package org.killbill.billing.util.broadcast.dao;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.util.entity.dao.DBRouter;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultBroadcastDao implements BroadcastDao {

    private final DBRouter<BroadcastSqlDao> dbRouter;

    @Inject
    public DefaultBroadcastDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi) {
        this.dbRouter = new DBRouter<BroadcastSqlDao>(dbi, roDbi, BroadcastSqlDao.class);
    }

    @Override
    public void create(final BroadcastModelDao broadcastModelDao) {
        dbRouter.inTransaction(false, new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final BroadcastSqlDao sqlDao = handle.attach(BroadcastSqlDao.class);
                sqlDao.create(broadcastModelDao);
                return null;
            }
        });
    }

    @Override
    public List<BroadcastModelDao> getLatestEntriesFrom(final Long recordId) {
        return dbRouter.inTransaction(true, new TransactionCallback<List<BroadcastModelDao>>() {
            @Override
            public List<BroadcastModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final BroadcastSqlDao sqlDao = handle.attach(BroadcastSqlDao.class);
                return sqlDao.getLatestEntries(recordId);
            }
        });
    }

    @Override
    public BroadcastModelDao getLatestEntry() {
        return dbRouter.inTransaction(true, new TransactionCallback<BroadcastModelDao>() {
            @Override
            public BroadcastModelDao inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final BroadcastSqlDao sqlDao = handle.attach(BroadcastSqlDao.class);
                return sqlDao.getLatestEntry();
            }
        });
    }
}
