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

package com.ning.billing.util.glue;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.bus.BusTableName;
import com.ning.billing.bus.DefaultPersistentBus;
import com.ning.billing.bus.InMemoryPersistentBus;
import com.ning.billing.bus.PersistentBus;
import com.ning.billing.bus.PersistentBusConfig;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.svcsapi.bus.BusService;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class BusModule extends AbstractModule {

    private final BusType type;
    private final ConfigSource configSource;
    private final String tableName;

    public BusModule(final ConfigSource configSource) {
        this(BusType.PERSISTENT, configSource, "bus_events");
    }

    public BusModule(final ConfigSource configSource, final String tableName) {
        this(BusType.PERSISTENT, configSource, tableName);
    }

    protected BusModule(final BusType type, final ConfigSource configSource, final String tableName) {
        this.type = type;
        this.configSource = configSource;
        this.tableName = tableName;
    }

    public enum BusType {
        MEMORY,
        PERSISTENT
    }

    @Override
    protected void configure() {
        bind(BusService.class).to(DefaultBusService.class);
        switch (type) {
            case MEMORY:
                configureInMemoryEventBus();
                break;
            case PERSISTENT:
                configurePersistentEventBus();
                break;
            default:
                new RuntimeException("Unrecognized EventBus type " + type);
        }

    }

    protected void configurePersistentBusConfig() {
        final PersistentBusConfig config = new ConfigurationObjectFactory(configSource).build(PersistentBusConfig.class);
        bind(PersistentBusConfig.class).toInstance(config);
    }

    private void configurePersistentEventBus() {
        configurePersistentBusConfig();
        bind(String.class).annotatedWith(BusTableName.class).toInstance(tableName);
        bind(PersistentBus.class).to(DefaultPersistentBus.class).asEagerSingleton();
    }

    private void configureInMemoryEventBus() {
        bind(PersistentBus.class).to(InMemoryPersistentBus.class).asEagerSingleton();
    }
}
