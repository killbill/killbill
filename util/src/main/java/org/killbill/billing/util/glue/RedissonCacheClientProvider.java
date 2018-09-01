/*
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

import javax.inject.Inject;

import org.killbill.billing.util.config.definition.RedisCacheConfig;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.FstCodec;
import org.redisson.config.Config;

import com.google.inject.Provider;

public class RedissonCacheClientProvider implements Provider<RedissonClient> {

    private final RedisCacheConfig cacheConfig;

    @Inject
    public RedissonCacheClientProvider(final RedisCacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    @Override
    public RedissonClient get() {
        final Config redissonCfg = new Config();
        redissonCfg.setCodec(new FstCodec())
                   .useSingleServer()
                   .setAddress(cacheConfig.getUrl())
                   .setConnectionMinimumIdleSize(cacheConfig.getConnectionMinimumIdleSize());
        return Redisson.create(redissonCfg);
    }
}
