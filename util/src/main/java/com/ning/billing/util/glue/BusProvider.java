/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.util.glue;

import javax.inject.Inject;
import javax.inject.Provider;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.bus.DefaultPersistentBus;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.bus.api.PersistentBusConfig;
import com.ning.billing.clock.Clock;


public class BusProvider implements Provider<PersistentBus> {

    private IDBI dbi;
    private Clock clock;
    private PersistentBusConfig busConfig;
    private String tableName;
    private String historyTableName;

    public BusProvider(final String tableName, final String historyTableName) {
        this.tableName = tableName;
        this.historyTableName = historyTableName;
    }

    @Inject
    public void initialize(final IDBI dbi, final Clock clock, final PersistentBusConfig config) {
        this.dbi = dbi;
        this.clock = clock;
        this.busConfig = config;
    }


    @Override
    public PersistentBus get() {
        return new DefaultPersistentBus(dbi, clock, busConfig, tableName, historyTableName);
    }
}
