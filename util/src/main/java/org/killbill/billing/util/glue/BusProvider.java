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

package org.killbill.billing.util.glue;

import javax.inject.Inject;
import javax.inject.Provider;

import org.skife.jdbi.v2.IDBI;

import org.killbill.bus.DefaultPersistentBus;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBusConfig;
import org.killbill.clock.Clock;

import com.codahale.metrics.MetricRegistry;


public class BusProvider implements Provider<PersistentBus> {

    private final PersistentBusConfig busConfig;

    private IDBI dbi;
    private Clock clock;
    private MetricRegistry metricRegistry;

    public BusProvider(final PersistentBusConfig busConfig) {
        this.busConfig = busConfig;
    }

    @Inject
    public void initialize(final IDBI dbi, final Clock clock, final MetricRegistry metricRegistry) {
        this.dbi = dbi;
        this.clock = clock;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public PersistentBus get() {
        return new DefaultPersistentBus(dbi, clock, busConfig, metricRegistry);
    }
}
