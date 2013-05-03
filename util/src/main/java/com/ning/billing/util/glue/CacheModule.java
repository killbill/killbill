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

package com.ning.billing.util.glue;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.cache.CacheControllerDispatcherProvider;
import com.ning.billing.util.cache.EhCacheCacheManagerProvider;
import com.ning.billing.util.config.CacheConfig;

import com.google.inject.AbstractModule;
import net.sf.ehcache.CacheManager;

public class CacheModule extends AbstractModule {

    private final ConfigSource configSource;

    public CacheModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    @Override
    protected void configure() {
        final CacheConfig config = new ConfigurationObjectFactory(configSource).build(CacheConfig.class);
        bind(CacheConfig.class).toInstance(config);

        // EhCache specifics
        bind(CacheManager.class).toProvider(EhCacheCacheManagerProvider.class).asEagerSingleton();

        // Kill Bill generic cache dispatcher
        bind(CacheControllerDispatcher.class).toProvider(CacheControllerDispatcherProvider.class).asEagerSingleton();
    }
}
