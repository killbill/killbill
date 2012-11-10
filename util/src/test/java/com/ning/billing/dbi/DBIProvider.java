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

package com.ning.billing.dbi;

import java.util.concurrent.TimeUnit;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.transactions.SerializableTransactionRunner;

import com.ning.billing.util.dao.DateTimeArgumentFactory;
import com.ning.billing.util.dao.DateTimeZoneArgumentFactory;
import com.ning.billing.util.dao.EnumArgumentFactory;
import com.ning.billing.util.dao.UUIDArgumentFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;

public class DBIProvider implements Provider<IDBI> {

    private final BoneCPConfig dbConfig;

    @Inject
    public DBIProvider(final DbiConfig config) {
        this(config.getJdbcUrl(), config.getUsername(), config.getPassword());
    }

    public DBIProvider(final String dbiString, final String userName, final String pwd) {
        this.dbConfig = createConfig(dbiString, userName, pwd);
    }

    BoneCPConfig createConfig(final String dbiString, final String userName, final String pwd) {
        final BoneCPConfig dbConfig = new BoneCPConfig();
        dbConfig.setJdbcUrl(dbiString);
        dbConfig.setUsername(userName);
        dbConfig.setPassword(pwd);
        dbConfig.setMinConnectionsPerPartition(1);
        dbConfig.setMaxConnectionsPerPartition(30);
        dbConfig.setConnectionTimeout(10, TimeUnit.SECONDS);
        dbConfig.setPartitionCount(1);
        dbConfig.setDefaultTransactionIsolation("REPEATABLE_READ");
        dbConfig.setDisableJMX(false);
        dbConfig.setLazyInit(true);
        return dbConfig;
    }

    @Override
    public IDBI get() {
        final BoneCPDataSource ds = new BoneCPDataSource(dbConfig);
        final DBI dbi = new DBI(ds);
        dbi.registerArgumentFactory(new UUIDArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeZoneArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeArgumentFactory());
        dbi.registerArgumentFactory(new EnumArgumentFactory());

        // Restart transactions in case of deadlocks
        dbi.setTransactionHandler(new SerializableTransactionRunner());
        //final SQLLog log = new Log4JLog();
        //dbi.setSQLLog(log);

        return dbi;
    }
}
