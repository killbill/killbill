/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import org.killbill.billing.util.config.tenant.CacheConfig;
import org.killbill.billing.util.config.tenant.PerTenantConfigInvalidationCallback;

import com.google.inject.name.Names;

public class ConfigModule extends KillBillModule {

    public static final String CONFIG_INVALIDATION_CALLBACK = "ConfigInvalidationCallback";

    public ConfigModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        bind(CacheConfig.class).asEagerSingleton();
        bind(CacheInvalidationCallback.class).annotatedWith(Names.named(CONFIG_INVALIDATION_CALLBACK)).to(PerTenantConfigInvalidationCallback.class).asEagerSingleton();
        bind(ConfigKillbillService.class).to(DefaultConfigKillbillService.class).asEagerSingleton();;
    }
}
