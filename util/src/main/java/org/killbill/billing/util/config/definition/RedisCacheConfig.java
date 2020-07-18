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

package org.killbill.billing.util.config.definition;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.DefaultNull;
import org.skife.config.Description;

public interface RedisCacheConfig extends KillbillConfig {

    @Config("org.killbill.cache.config.redis")
    @Default("false")
    @Description("Whether Redis integration for caching is enabled")
    public boolean isRedisCachingEnabled();

    @Config("org.killbill.cache.config.redis.url")
    @Default("redis://127.0.0.1:6379")
    @Description("Redis URL")
    public String getUrl();

    @Config("org.killbill.cache.config.redis.connectionMinimumIdleSize")
    @Default("1")
    @Description("Minimum number of connections")
    public int getConnectionMinimumIdleSize();

    @Config("org.killbill.cache.config.redis.password")
    @DefaultNull
    @Description("Redis Password")
    public String getPassword();
}
