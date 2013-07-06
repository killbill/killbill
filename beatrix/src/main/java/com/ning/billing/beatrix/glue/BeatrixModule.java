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

package com.ning.billing.beatrix.glue;

import com.ning.billing.beatrix.DefaultBeatrixService;
import com.ning.billing.beatrix.bus.api.BeatrixService;
import com.ning.billing.beatrix.extbus.BeatrixListener;
import com.ning.billing.beatrix.lifecycle.DefaultLifecycle;
import com.ning.billing.beatrix.lifecycle.Lifecycle;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.util.glue.BusProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

public class BeatrixModule extends AbstractModule {

    // This has to match the DDL
    private final static String EXTERNAL_BUS_TABLE_NAME = "bus_ext_events";
    private final static String EXTERNAL_BUS_HISTORY_TABLE_NAME = "bus_ext_events_history";

    public static final String EXTERNAL_BUS = "externalBus";

    @Override
    protected void configure() {
        installLifecycle();
        installExternalBus();
    }

    protected void installLifecycle() {
        bind(Lifecycle.class).to(DefaultLifecycle.class).asEagerSingleton();
    }

    protected void installExternalBus() {
        bind(BeatrixService.class).to(DefaultBeatrixService.class);
        bind(DefaultBeatrixService.class).asEagerSingleton();

        bind(BusProvider.class).annotatedWith(Names.named(EXTERNAL_BUS)).toInstance(new BusProvider(EXTERNAL_BUS_TABLE_NAME, EXTERNAL_BUS_HISTORY_TABLE_NAME));
        bind(PersistentBus.class).annotatedWith(Names.named(EXTERNAL_BUS)).toProvider(Key.get(BusProvider.class, Names.named(EXTERNAL_BUS))).asEagerSingleton();

        bind(BeatrixListener.class).asEagerSingleton();
    }
}
