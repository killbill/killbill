/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.server.config;

import org.killbill.billing.server.modules.DataSourceConnectionPoolingType;
import org.killbill.billing.util.config.KillbillConfig;
import org.killbill.commons.jdbi.log.LogLevel;
import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.TimeSpan;

public interface DaoConfig extends KillbillConfig {

    @Description("The jdbc url for the database")
    @Config("org.killbill.dao.url")
    @Default("jdbc:mysql://127.0.0.1:3306/killbill")
    String getJdbcUrl();

    @Description("The jdbc user name for the database")
    @Config("org.killbill.dao.user")
    @Default("killbill")
    String getUsername();

    @Description("The jdbc password for the database")
    @Config("org.killbill.dao.password")
    @Default("killbill")
    String getPassword();

    @Description("The minimum allowed number of idle connections to the database")
    @Config("org.killbill.dao.minIdle")
    @Default("1")
    int getMinIdle();

    @Description("The maximum allowed number of active connections to the database")
    @Config("org.killbill.dao.maxActive")
    @Default("30")
    int getMaxActive();

    @Description("How long to wait before a connection attempt to the database is considered timed out")
    @Config("org.killbill.dao.connectionTimeout")
    @Default("10s")
    TimeSpan getConnectionTimeout();

    @Description("The time for a connection to remain unused before it is closed off")
    @Config("org.killbill.dao.idleMaxAge")
    @Default("60m")
    TimeSpan getIdleMaxAge();

    @Description("Any connections older than this setting will be closed off whether it is idle or not. Connections " +
                 "currently in use will not be affected until they are returned to the pool")
    @Config("org.killbill.dao.maxConnectionAge")
    @Default("0m")
    TimeSpan getMaxConnectionAge();

    @Description("Time for a connection to remain idle before sending a test query to the DB")
    @Config("org.killbill.dao.idleConnectionTestPeriod")
    @Default("5m")
    TimeSpan getIdleConnectionTestPeriod();

    @Description("Log level for SQL queries")
    @Config("org.killbill.dao.logLevel")
    @Default("WARN")
    LogLevel getLogLevel();

    @Description("The TransactionHandler to use for all Handle instances")
    @Config("org.killbill.dao.transactionHandler")
    @Default("org.killbill.commons.jdbi.transaction.RestartTransactionRunner")
    String getTransactionHandlerClass();

    @Description("Connection pooling type")
    @Config("org.killbill.dao.poolingType")
    @Default("C3P0")
    DataSourceConnectionPoolingType getConnectionPoolingType();
}
