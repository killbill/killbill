/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import org.killbill.clock.Clock;
import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;


public class DefaultBroadcastDao implements BroadcastDao {

    private final IDBI dbi;
    private final Clock clock;

    @Inject
    public DefaultBroadcastDao(final IDBI dbi, final Clock clock) {
        this.dbi = dbi;
        this.clock = clock;
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(BroadcastModelDao.class));

    }

    @Override
    public void create(final BroadcastModelDao broadcastModelDao) {
        dbi.inTransaction(new TransactionCallback<Void>() {
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
        return dbi.inTransaction(new TransactionCallback<List<BroadcastModelDao>>() {
            @Override
            public List<BroadcastModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final BroadcastSqlDao sqlDao = handle.attach(BroadcastSqlDao.class);
                return sqlDao.getLatestEntries(recordId);
            }
        });
    }

    @Override
    public BroadcastModelDao getLatestEntry() {
        return dbi.inTransaction(new TransactionCallback<BroadcastModelDao>() {
            @Override
            public BroadcastModelDao inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final BroadcastSqlDao sqlDao = handle.attach(BroadcastSqlDao.class);
                return sqlDao.getLatestEntry();
            }
        });
    }
}
