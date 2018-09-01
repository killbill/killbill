/*
 * Copyright 2010-2011 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import javax.annotation.Nullable;
import javax.sql.DataSource;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.RedisLockerConfig;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.memory.MemoryGlobalLocker;
import org.killbill.commons.locker.mysql.MySqlGlobalLocker;
import org.killbill.commons.locker.postgresql.PostgreSQLGlobalLocker;
import org.killbill.commons.locker.redis.RedisGlobalLocker;
import org.redisson.api.RedissonClient;
import org.skife.config.ConfigurationObjectFactory;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;

public class GlobalLockerModule extends KillBillModule {

    public static final String REDIS_LOCKER_CLIENT = "redisLockerClient";

    public GlobalLockerModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Provides
    @Singleton
    // Note: we need to inject the pooled DataSource here, not the (direct) one from EmbeddedDB
    protected GlobalLocker provideGlobalLocker(final DataSource dataSource, final EmbeddedDB embeddedDB, @Nullable @Named(REDIS_LOCKER_CLIENT) final RedissonClient redissonClient) {
        if (redissonClient != null) {
            return new RedisGlobalLocker(redissonClient);
        } else {
            if (EmbeddedDB.DBEngine.MYSQL.equals(embeddedDB.getDBEngine())) {
                return new MySqlGlobalLocker(dataSource);
            } else if (EmbeddedDB.DBEngine.POSTGRESQL.equals(embeddedDB.getDBEngine())) {
                return new PostgreSQLGlobalLocker(dataSource);
            } else {
                return new MemoryGlobalLocker();
            }
        }
    }

    @Override
    protected void configure() {
        final RedisLockerConfig redisLockerConfig = new ConfigurationObjectFactory(skifeConfigSource).build(RedisLockerConfig.class);
        bind(RedisLockerConfig.class).toInstance(redisLockerConfig);

        if (redisLockerConfig.isRedisLockerEnabled()) {
            bind(RedissonClient.class).annotatedWith(Names.named(REDIS_LOCKER_CLIENT)).toProvider(RedissonLockerClientProvider.class).asEagerSingleton();
        } else {
            bind(RedissonClient.class).annotatedWith(Names.named(REDIS_LOCKER_CLIENT)).toProvider(Providers.<RedissonClient>of(null));
        }
    }
}
