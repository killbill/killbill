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

import java.util.UUID;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;

import com.ning.billing.util.cache.AccountRecordIdCacheLoader;
import com.ning.billing.util.cache.Cachable;
import com.ning.billing.util.cache.Cachable.CacheType;
import com.ning.billing.util.cache.CacheController;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.cache.CacheControllerProvider;
import com.ning.billing.util.cache.CacheManagerProvider;
import com.ning.billing.util.cache.RecordIdCacheLoader;
import com.ning.billing.util.cache.TenantRecordIdCacheLoader;
import com.ning.billing.util.config.CacheConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.loader.CacheLoader;

public class CacheModule extends AbstractModule {


    public static final Named RECORD_ID_CACHE_NAMED = Names.named(Cachable.RECORD_ID_CACHE_NAME);
    public static final Named ACCOUNT_RECORD_ID_CACHE_NAMED = Names.named(Cachable.ACCOUNT_RECORD_ID_CACHE_NAME);
    public static final Named TENANT_RECORD_ID_CACHE_NAMED = Names.named(Cachable.TENANT_RECORD_ID_CACHE_NAME);

    protected void installConfig() {
        final ConfigSource configSource = new SimplePropertyConfigSource(System.getProperties());
        final CacheConfig config = new ConfigurationObjectFactory(configSource).build(CacheConfig.class);
        bind(CacheConfig.class).toInstance(config);
    }

    @Override
    protected void configure() {

        installConfig();

        bind(CacheManager.class).toProvider(CacheManagerProvider.class).asEagerSingleton();

        bind(CacheLoader.class).annotatedWith(RECORD_ID_CACHE_NAMED).to(RecordIdCacheLoader.class).asEagerSingleton();
        bind(new TypeLiteral<CacheController<UUID, Long>>() {}).annotatedWith(RECORD_ID_CACHE_NAMED).toProvider(new CacheControllerProvider<UUID, Long>(Cachable.RECORD_ID_CACHE_NAME)).asEagerSingleton();

        bind(CacheLoader.class).annotatedWith(ACCOUNT_RECORD_ID_CACHE_NAMED).to(AccountRecordIdCacheLoader.class).asEagerSingleton();
        bind(new TypeLiteral<CacheController<UUID, Long>>() {}).annotatedWith(ACCOUNT_RECORD_ID_CACHE_NAMED).toProvider(new CacheControllerProvider<UUID, Long>(Cachable.ACCOUNT_RECORD_ID_CACHE_NAME)).asEagerSingleton();

        bind(CacheLoader.class).annotatedWith(TENANT_RECORD_ID_CACHE_NAMED).to(TenantRecordIdCacheLoader.class).asEagerSingleton();
        bind(new TypeLiteral<CacheController<UUID, Long>>() {}).annotatedWith(TENANT_RECORD_ID_CACHE_NAMED).toProvider(new CacheControllerProvider<UUID, Long>(Cachable.TENANT_RECORD_ID_CACHE_NAME)).asEagerSingleton();

        bind(CacheControllerDispatcher.class).asEagerSingleton();
    }
}
