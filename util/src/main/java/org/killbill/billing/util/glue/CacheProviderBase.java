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

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jcache.JCacheGaugeSet;
import com.google.common.base.Preconditions;

abstract class CacheProviderBase {

    private static final String PROP_METRIC_REG_JCACHE_STATISTICS = "jcache.statistics";

    private final MetricRegistry metricRegistry;

    CacheProviderBase(final MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    <C extends Configuration> void createCache(final CacheManager cacheManager, final String cacheName, final C configuration) {
        // Make sure we start from a clean state - this is mainly useful for tests
        cacheManager.destroyCache(cacheName);

        final Cache cache = cacheManager.createCache(cacheName, configuration);
        Preconditions.checkState(!cache.isClosed(), "Cache '%s' should not be closed", cacheName);

        // Re-create the metrics to support dynamically created caches (e.g. for Shiro)
        metricRegistry.removeMatching(new MetricFilter() {
            @Override
            public boolean matches(final String name, final Metric metric) {
                return name != null && name.startsWith(PROP_METRIC_REG_JCACHE_STATISTICS);
            }
        });
        metricRegistry.register(PROP_METRIC_REG_JCACHE_STATISTICS, new JCacheGaugeSet());
    }
}
