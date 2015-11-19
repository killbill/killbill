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

package org.killbill.billing.util.nodes.dao;

import java.util.Date;
import java.util.List;

import org.killbill.clock.Clock;
import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import com.google.inject.Inject;

public class DefaultNodeInfoDao implements NodeInfoDao {

    private final IDBI dbi;
    private final Clock clock;

    @Inject
    public DefaultNodeInfoDao(final IDBI dbi, final Clock clock) {
        this.dbi = dbi;
        this.clock = clock;
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(NodeInfoModelDao.class));

    }

    @Override
    public void create(final NodeInfoModelDao nodeInfoModelDao) {

        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final NodeInfoSqlDao sqlDao = handle.attach(NodeInfoSqlDao.class);
                if (sqlDao.getByNodeName(nodeInfoModelDao.getNodeName()) != null) {
                    sqlDao.delete(nodeInfoModelDao.getNodeName());
                }
                sqlDao.create(nodeInfoModelDao);
                return null;
            }
        });
    }

    @Override
    public void updateNodeInfo(final String nodeName, final String nodeInfo) {
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final NodeInfoSqlDao sqlDao = handle.attach(NodeInfoSqlDao.class);
                final Date updateDate = clock.getUTCNow().toDate();
                sqlDao.updateNodeInfo(nodeName, nodeInfo, updateDate);
                return null;
            }
        });
    }


    @Override
    public void delete(final String nodeName) {
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final NodeInfoSqlDao sqlDao = handle.attach(NodeInfoSqlDao.class);
                sqlDao.delete(nodeName);
                return null;
            }
        });
    }

    @Override
    public List<NodeInfoModelDao> getAll() {
        return dbi.inTransaction(new TransactionCallback<List<NodeInfoModelDao>>() {
            @Override
            public List<NodeInfoModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final NodeInfoSqlDao sqlDao = handle.attach(NodeInfoSqlDao.class);
                return sqlDao.getAll();
            }
        });
    }

    @Override
    public NodeInfoModelDao getByNodeName(final String nodeName) {
        return dbi.inTransaction(new TransactionCallback<NodeInfoModelDao>() {
            @Override
            public NodeInfoModelDao inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final NodeInfoSqlDao sqlDao = handle.attach(NodeInfoSqlDao.class);
                return sqlDao.getByNodeName(nodeName);
            }
        });
    }

}
