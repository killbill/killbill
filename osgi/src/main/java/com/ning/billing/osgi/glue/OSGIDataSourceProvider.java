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

package com.ning.billing.osgi.glue;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.sql.DataSource;

import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;

public class OSGIDataSourceProvider implements Provider<DataSource> {

    private final BoneCPConfig config;

    @Inject
    public OSGIDataSourceProvider(final OSGIDataSourceConfig config) {
        this.config = createConfig(config.getJdbcUrl(), config.getUsername(), config.getPassword());
    }

    @Override
    public DataSource get() {
        return new BoneCPDataSource(config);
    }

    private BoneCPConfig createConfig(final String dbiString, final String userName, final String pwd) {
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
}
