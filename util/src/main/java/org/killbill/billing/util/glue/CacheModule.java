/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
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

import javax.cache.CacheManager;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.cache.AccountBCDCacheLoader;
import org.killbill.billing.util.cache.AccountIdFromBundleIdCacheLoader;
import org.killbill.billing.util.cache.AccountRecordIdCacheLoader;
import org.killbill.billing.util.cache.BaseCacheLoader;
import org.killbill.billing.util.cache.BundleIdFromSubscriptionIdCacheLoader;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheControllerDispatcherProvider;
import org.killbill.billing.util.cache.ImmutableAccountCacheLoader;
import org.killbill.billing.util.cache.ObjectIdCacheLoader;
import org.killbill.billing.util.cache.OverriddenPlanCacheLoader;
import org.killbill.billing.util.cache.RecordIdCacheLoader;
import org.killbill.billing.util.cache.TenantCacheLoader;
import org.killbill.billing.util.cache.TenantCatalogCacheLoader;
import org.killbill.billing.util.cache.TenantConfigCacheLoader;
import org.killbill.billing.util.cache.TenantKVCacheLoader;
import org.killbill.billing.util.cache.TenantOverdueConfigCacheLoader;
import org.killbill.billing.util.cache.TenantRecordIdCacheLoader;
import org.killbill.billing.util.cache.TenantStateMachineConfigCacheLoader;
import org.killbill.billing.util.config.definition.CacheConfig;
import org.killbill.billing.util.config.definition.EhCacheConfig;
import org.killbill.billing.util.config.definition.RedisCacheConfig;
import org.redisson.api.RedissonClient;
import org.skife.config.AugmentedConfigurationObjectFactory;

import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;

public class CacheModule extends KillBillModule {

    public static final String REDIS_CACHE_CLIENT = "redisCacheClient";

    public CacheModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        final CacheConfig cacheConfig = new AugmentedConfigurationObjectFactory(skifeConfigSource).build(CacheConfig.class);
        bind(CacheConfig.class).toInstance(cacheConfig);

        final EhCacheConfig ehCacheConfig = new AugmentedConfigurationObjectFactory(skifeConfigSource).build(EhCacheConfig.class);
        bind(EhCacheConfig.class).toInstance(ehCacheConfig);

        final RedisCacheConfig redisCacheConfig = new AugmentedConfigurationObjectFactory(skifeConfigSource).build(RedisCacheConfig.class);
        bind(RedisCacheConfig.class).toInstance(redisCacheConfig);

        if (redisCacheConfig.isRedisCachingEnabled()) {
            bind(RedissonClient.class).annotatedWith(Names.named(REDIS_CACHE_CLIENT)).toProvider(RedissonCacheClientProvider.class).asEagerSingleton();
            bind(CacheManager.class).toProvider(Redis107CacheManagerProvider.class).asEagerSingleton();
        } else {
            bind(RedissonClient.class).annotatedWith(Names.named(REDIS_CACHE_CLIENT)).toProvider(Providers.<RedissonClient>of(null));
            bind(CacheManager.class).toProvider(Eh107CacheManagerProvider.class).asEagerSingleton();
        }

        // Kill Bill generic cache dispatcher
        bind(CacheControllerDispatcher.class).toProvider(CacheControllerDispatcherProvider.class).asEagerSingleton();

        final Multibinder<BaseCacheLoader> resultSetMapperSetBinder = Multibinder.newSetBinder(binder(), BaseCacheLoader.class);
        resultSetMapperSetBinder.addBinding().to(ImmutableAccountCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(AccountBCDCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(RecordIdCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(AccountRecordIdCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(TenantRecordIdCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(ObjectIdCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(TenantCatalogCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(TenantConfigCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(TenantOverdueConfigCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(TenantKVCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(TenantCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(OverriddenPlanCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(TenantStateMachineConfigCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(AccountIdFromBundleIdCacheLoader.class).asEagerSingleton();
        resultSetMapperSetBinder.addBinding().to(BundleIdFromSubscriptionIdCacheLoader.class).asEagerSingleton();
    }
}
