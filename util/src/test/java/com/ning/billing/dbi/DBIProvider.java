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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;
import com.ning.jdbi.metrics.JdbiGroupStrategy;
import com.ning.jdbi.metrics.MetricsTimingCollector;
import com.ning.jdbi.metrics.SqlJdbiGroupStrategy;
import com.yammer.metrics.core.MetricsRegistry;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TimingCollector;
import org.skife.jdbi.v2.logging.Log4JLog;
import org.skife.jdbi.v2.tweak.SQLLog;

import java.util.concurrent.TimeUnit;

public class DBIProvider implements Provider<IDBI>
{
    private final MetricsRegistry metricsRegistry;
    private final DbiConfig config;

    @Inject
    public DBIProvider(final MetricsRegistry metricsRegistry, final DbiConfig config)
    {
        this.metricsRegistry = metricsRegistry;
        this.config = config;
    }

    @Override
    public IDBI get()
    {
        final BoneCPConfig dbConfig = new BoneCPConfig();
        dbConfig.setJdbcUrl(config.getJdbcUrl());
        dbConfig.setUsername(config.getUsername());
        dbConfig.setPassword(config.getPassword());
        dbConfig.setMinConnectionsPerPartition(config.getMinIdle());
        dbConfig.setMaxConnectionsPerPartition(config.getMaxActive());
        dbConfig.setConnectionTimeout(config.getConnectionTimeout().getPeriod(), config.getConnectionTimeout().getUnit());
        dbConfig.setPartitionCount(1);
        dbConfig.setDefaultTransactionIsolation("REPEATABLE_READ");
        dbConfig.setDisableJMX(false);

        final BoneCPDataSource ds = new BoneCPDataSource(dbConfig);
        final DBI dbi = new DBI(ds);
        final SQLLog log = new Log4JLog();
        dbi.setSQLLog(log);

        final JdbiGroupStrategy jdbiGroupStrategy = new SqlJdbiGroupStrategy();
        final TimingCollector timingCollector = new MetricsTimingCollector(metricsRegistry, jdbiGroupStrategy, TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
        dbi.setTimingCollector(timingCollector);

        return dbi;
    }
}