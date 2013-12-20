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

import javax.sql.DataSource;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.TimingCollector;
import org.skife.jdbi.v2.tweak.SQLLog;
import org.skife.jdbi.v2.tweak.TransactionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.dao.AuditLogModelDaoMapper;
import com.ning.billing.util.dao.DateTimeArgumentFactory;
import com.ning.billing.util.dao.DateTimeZoneArgumentFactory;
import com.ning.billing.util.dao.EnumArgumentFactory;
import com.ning.billing.util.dao.LocalDateArgumentFactory;
import com.ning.billing.util.dao.UUIDArgumentFactory;
import com.ning.billing.util.dao.UuidMapper;
import com.ning.jetty.jdbi.config.DaoConfig;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.jdbi.InstrumentedTimingCollector;
import com.yammer.metrics.jdbi.strategies.BasicSqlNameStrategy;

public class DBIProvider implements Provider<DBI> {

    private static final Logger logger = LoggerFactory.getLogger(DBIProvider.class);

    private final DataSource ds;
    private final MetricsRegistry metricsRegistry;
    private final DaoConfig config;
    private SQLLog sqlLog;

    @Inject
    public DBIProvider(final DataSource ds, final MetricsRegistry metricsRegistry, final DaoConfig config) {
        this.ds = ds;
        this.metricsRegistry = metricsRegistry;
        this.config = config;
    }

    @Inject(optional = true)
    public void setSqlLog(final SQLLog sqlLog) {
        this.sqlLog = sqlLog;
    }

    @Override
    public DBI get() {
        final DBI dbi = new DBI(ds);
        dbi.registerArgumentFactory(new UUIDArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeZoneArgumentFactory());
        dbi.registerArgumentFactory(new DateTimeArgumentFactory());
        dbi.registerArgumentFactory(new LocalDateArgumentFactory());
        dbi.registerArgumentFactory(new EnumArgumentFactory());
        dbi.registerMapper(new UuidMapper());
        dbi.registerMapper(new AuditLogModelDaoMapper());

        if (sqlLog != null) {
            dbi.setSQLLog(sqlLog);
        }

        if (config.getTransactionHandlerClass() != null) {
            logger.info("Using " + config.getTransactionHandlerClass() + " as a transaction handler class");
            try {
                dbi.setTransactionHandler((TransactionHandler) Class.forName(config.getTransactionHandlerClass()).newInstance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        final BasicSqlNameStrategy basicSqlNameStrategy = new BasicSqlNameStrategy();
        final TimingCollector timingCollector = new InstrumentedTimingCollector(metricsRegistry, basicSqlNameStrategy, TimeUnit.MILLISECONDS, TimeUnit.SECONDS);
        dbi.setTimingCollector(timingCollector);

        return dbi;
    }
}
