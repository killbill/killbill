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

import javax.sql.DataSource;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.transactions.SerializableTransactionRunner;

import com.ning.billing.util.dao.DateTimeArgumentFactory;
import com.ning.billing.util.dao.DateTimeZoneArgumentFactory;
import com.ning.billing.util.dao.EnumArgumentFactory;
import com.ning.billing.util.dao.LocalDateArgumentFactory;
import com.ning.billing.util.dao.UUIDArgumentFactory;
import com.ning.billing.util.dao.UuidMapper;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;
import com.mchange.v2.c3p0.ComboPooledDataSource;

public class DBIProvider implements Provider<IDBI> {

    private final String jdbcUri;
    private final String userName;
    private final String userPwd;

    @Inject
    public DBIProvider(final DbiConfig config) {
        this(config.getJdbcUrl(), config.getUsername(), config.getPassword());
    }

    public DBIProvider(final String jdbcUri, final String userName, final String userPwd) {
        this.jdbcUri = jdbcUri;
        this.userName = userName;
        this.userPwd = userPwd;
    }

    @Override
    public IDBI get() {
        final DataSource ds = getC3P0DataSource();
        final DBI dbi = new DBI(ds);
        dbi.registerArgumentFactory(new UUIDArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeZoneArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeArgumentFactory());
        dbi.registerArgumentFactory(new LocalDateArgumentFactory());
        dbi.registerArgumentFactory(new EnumArgumentFactory());
        dbi.registerMapper(new UuidMapper());

        // Restart transactions in case of deadlocks
        dbi.setTransactionHandler(new SerializableTransactionRunner());
        //final SQLLog log = new Log4JLog();
        //dbi.setSQLLog(log);

        return dbi;
    }



    private DataSource getBoneCPDatSource() {
        final BoneCPConfig dbConfig = new BoneCPConfig();
        dbConfig.setJdbcUrl(jdbcUri);
        dbConfig.setUsername(userName);
        dbConfig.setPassword(userPwd);
        dbConfig.setPartitionCount(1);
        //dbConfig.setDefaultTransactionIsolation("READ_COMMITTED");
        dbConfig.setDisableJMX(false);

        final BoneCPDataSource ds = new BoneCPDataSource(dbConfig);
        return ds;
    }

    private DataSource getC3P0DataSource() {
        ComboPooledDataSource cpds = new ComboPooledDataSource();
        cpds.setJdbcUrl(jdbcUri);
        cpds.setUser(userName);
        cpds.setPassword(userPwd);
        cpds.setMinPoolSize(1);
        cpds.setMaxPoolSize(10);
        return cpds;
    }
}
