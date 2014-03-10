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

package org.killbill.billing.beatrix.glue;

import org.skife.config.ConfigSource;

import org.killbill.billing.beatrix.DefaultBeatrixService;
import org.killbill.billing.beatrix.bus.api.BeatrixService;
import org.killbill.billing.beatrix.extbus.BeatrixListener;
import org.killbill.billing.beatrix.lifecycle.DefaultLifecycle;
import org.killbill.billing.beatrix.lifecycle.Lifecycle;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.billing.util.glue.BusProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.name.Names;

public class BeatrixModule extends AbstractModule {

    public static final String EXTERNAL_BUS = "externalBus";

    private final ConfigSource configSource;

    public BeatrixModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

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

        final PersistentBusConfig extBusConfig = new ExternalPersistentBusConfig(configSource);

        bind(BusProvider.class).annotatedWith(Names.named(EXTERNAL_BUS)).toInstance(new BusProvider(extBusConfig));
        bind(PersistentBus.class).annotatedWith(Names.named(EXTERNAL_BUS)).toProvider(Key.get(BusProvider.class, Names.named(EXTERNAL_BUS))).asEagerSingleton();

        bind(BeatrixListener.class).asEagerSingleton();
    }
}
