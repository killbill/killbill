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

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.TimeSpan;

public interface DbiConfig
{
    @Description("The jdbc url for the database")
    @Config("com.ning.billing.dbi.jdbc.url")
    @Default("jdbc:mysql://127.0.0.1:3306/killbill")
    String getJdbcUrl();

    @Description("The jdbc user name for the database")
    @Config("com.ning.billing.dbi.jdbc.user")
    @Default("root")
    String getUsername();

    @Description("The jdbc password for the database")
    @Config("com.ning.billing.dbi.jdbc.password")
    @Default("root")
    String getPassword();

    @Description("The minimum allowed number of idle connections to the database")
    @Config("com.ning.billing.dbi.jdbc.minIdle")
    @Default("1")
    int getMinIdle();

    @Description("The maximum allowed number of active connections to the database")
    @Config("com.ning.billing.dbi.jdbc.maxActive")
    @Default("10")
    int getMaxActive();

    @Description("How long to wait before a connection attempt to the database is considered timed out")
    @Config("com.ning.billing.dbi.jdbc.connectionTimeout")
    @Default("10s")
    TimeSpan getConnectionTimeout();
}