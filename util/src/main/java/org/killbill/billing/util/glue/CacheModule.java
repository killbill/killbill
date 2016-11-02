/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheControllerDispatcherProvider;
import org.killbill.billing.util.cache.EhCacheCacheManagerProvider;
import org.killbill.billing.util.config.definition.EhCacheConfig;
import org.skife.config.ConfigurationObjectFactory;

import net.sf.ehcache.CacheManager;

public class CacheModule extends KillBillModule {

    public CacheModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        final EhCacheConfig config = new ConfigurationObjectFactory(skifeConfigSource).build(EhCacheConfig.class);
        bind(EhCacheConfig.class).toInstance(config);

        // EhCache specifics
        bind(CacheManager.class).toProvider(EhCacheCacheManagerProvider.class).asEagerSingleton();

        // Kill Bill generic cache dispatcher
        bind(CacheControllerDispatcher.class).toProvider(CacheControllerDispatcherProvider.class).asEagerSingleton();
    }
}
