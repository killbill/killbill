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

import com.google.inject.AbstractModule;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.bus.InMemoryBus;
import com.ning.billing.util.bus.PersistentBus;

public class BusModule extends AbstractModule {

    private final BusType type;
    
    public BusModule() {
        super();
        // Default to Memory at this point
        type = BusType.MEMORY;
    }

    public BusModule(BusType type) {
        super();
        this.type = type;
    }

    public enum BusType {
        MEMORY,
        PERSISTENT
    }
    
    @Override
    protected void configure() {
        bind(BusService.class).to(DefaultBusService.class);
        switch(type) {
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

    private void configurePersistentEventBus() {
        bind(Bus.class).to(PersistentBus.class).asEagerSingleton();
        
    }
    
    private void configureInMemoryEventBus() {
        bind(Bus.class).to(InMemoryBus.class).asEagerSingleton();
    }
}
