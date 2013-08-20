/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.server.modules;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;

import org.skife.config.TimeSpan;

import com.ning.jetty.jdbi.config.DaoConfig;

import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;
import com.mchange.v2.c3p0.ComboPooledDataSource;

public class DataSourceProvider implements Provider<DataSource> {

    private final DaoConfig config;

    @Inject
    public DataSourceProvider(final DaoConfig config) {
        this.config = config;
    }

    @Override
    public DataSource get() {
        return getDataSource();
    }

    private DataSource getDataSource() {
        final DataSource ds;

        // TODO PIERRE DaoConfig is in the skeleton
        final String dataSource = System.getProperty("com.ning.jetty.jdbi.datasource", "c3p0");
        if (dataSource.equals("c3p0")) {
            ds = getC3P0DataSource();
        } else if (dataSource.equals("bonecp")) {
            ds = getBoneCPDatSource();
        } else {
            throw new IllegalArgumentException("DataSource " + dataSource + " unsupported");
        }

        return ds;
    }

    private DataSource getBoneCPDatSource() {
        final BoneCPConfig dbConfig = new BoneCPConfig();
        dbConfig.setJdbcUrl(config.getJdbcUrl());
        dbConfig.setUsername(config.getUsername());
        dbConfig.setPassword(config.getPassword());
        dbConfig.setMinConnectionsPerPartition(config.getMinIdle());
        dbConfig.setMaxConnectionsPerPartition(config.getMaxActive());
        dbConfig.setConnectionTimeout(config.getConnectionTimeout().getPeriod(), config.getConnectionTimeout().getUnit());
        dbConfig.setIdleMaxAge(config.getIdleMaxAge().getPeriod(), config.getIdleMaxAge().getUnit());
        dbConfig.setMaxConnectionAge(config.getMaxConnectionAge().getPeriod(), config.getMaxConnectionAge().getUnit());
        dbConfig.setIdleConnectionTestPeriod(config.getIdleConnectionTestPeriod().getPeriod(), config.getIdleConnectionTestPeriod().getUnit());
        dbConfig.setPartitionCount(1);
        dbConfig.setDisableJMX(false);

        return new BoneCPDataSource(dbConfig);
    }

    private DataSource getC3P0DataSource() {
        final ComboPooledDataSource cpds = new ComboPooledDataSource();
        cpds.setJdbcUrl(config.getJdbcUrl());
        cpds.setUser(config.getUsername());
        cpds.setPassword(config.getPassword());
        // http://www.mchange.com/projects/c3p0/#minPoolSize
        // Minimum number of Connections a pool will maintain at any given time.
        cpds.setMinPoolSize(config.getMinIdle());
        // http://www.mchange.com/projects/c3p0/#maxPoolSize
        // Maximum number of Connections a pool will maintain at any given time.
        cpds.setMaxPoolSize(config.getMaxActive());
        // http://www.mchange.com/projects/c3p0/#checkoutTimeout
        // The number of milliseconds a client calling getConnection() will wait for a Connection to be checked-in or
        // acquired when the pool is exhausted. Zero means wait indefinitely. Setting any positive value will cause the getConnection()
        // call to time-out and break with an SQLException after the specified number of milliseconds.
        cpds.setCheckoutTimeout(toMilliSeconds(config.getConnectionTimeout()));
        // http://www.mchange.com/projects/c3p0/#maxIdleTime
        // Seconds a Connection can remain pooled but unused before being discarded. Zero means idle connections never expire.
        cpds.setMaxIdleTime(toSeconds(config.getIdleMaxAge()));
        // http://www.mchange.com/projects/c3p0/#maxConnectionAge
        // Seconds, effectively a time to live. A Connection older than maxConnectionAge will be destroyed and purged from the pool.
        // This differs from maxIdleTime in that it refers to absolute age. Even a Connection which has not been much idle will be purged
        // from the pool if it exceeds maxConnectionAge. Zero means no maximum absolute age is enforced.
        cpds.setMaxConnectionAge(toSeconds(config.getMaxConnectionAge()));
        // http://www.mchange.com/projects/c3p0/#idleConnectionTestPeriod
        // If this is a number greater than 0, c3p0 will test all idle, pooled but unchecked-out connections, every this number of seconds.
        cpds.setIdleConnectionTestPeriod(toSeconds(config.getIdleConnectionTestPeriod()));

        return cpds;
    }

    private int toSeconds(final TimeSpan timeSpan) {
        return toSeconds(timeSpan.getPeriod(), timeSpan.getUnit());
    }

    private int toSeconds(final long period, final TimeUnit timeUnit) {
        return (int) TimeUnit.SECONDS.convert(period, timeUnit);
    }

    private int toMilliSeconds(final TimeSpan timeSpan) {
        return toMilliSeconds(timeSpan.getPeriod(), timeSpan.getUnit());
    }

    private int toMilliSeconds(final long period, final TimeUnit timeUnit) {
        return (int) TimeUnit.MILLISECONDS.convert(period, timeUnit);
    }
}
