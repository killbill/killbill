/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.glue;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.util.config.ConfigKillbillService;
import org.killbill.billing.util.config.DefaultConfigKillbillService;
import org.killbill.billing.util.config.definition.EventConfig;
import org.killbill.billing.util.config.definition.MultiTenantEventConfig;
import org.killbill.billing.util.config.tenant.CacheConfig;
import org.killbill.billing.util.config.tenant.PerTenantConfigInvalidationCallback;
import org.skife.config.AugmentedConfigurationObjectFactory;

import com.google.inject.name.Names;

public class EventModule extends KillBillModule {


    public EventModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        final EventConfig eventConfig = new AugmentedConfigurationObjectFactory(skifeConfigSource).build(EventConfig.class);
        bind(EventConfig.class).annotatedWith(Names.named(KillBillModule.STATIC_CONFIG)).toInstance(eventConfig);
        bind(EventConfig.class).to(MultiTenantEventConfig.class).asEagerSingleton();

    }
}
