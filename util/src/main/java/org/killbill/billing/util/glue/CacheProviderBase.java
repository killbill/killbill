/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;

import org.killbill.billing.util.config.definition.EhCacheConfig;
import org.killbill.xmlloader.UriAccessor;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jcache.JCacheGaugeSet;

abstract class CacheProviderBase {

    private static final String PROP_METRIC_REG_JCACHE_STATISTICS = "jcache.statistics";

    private final MetricRegistry metricRegistry;

    final URL xmlConfigurationURL;

    CacheProviderBase(final MetricRegistry metricRegistry, final EhCacheConfig cacheConfig) {
        this.metricRegistry = metricRegistry;

        try {
            xmlConfigurationURL = UriAccessor.toURL(cacheConfig.getCacheConfigLocation());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    <K, V> Cache<K, V> createCache(final CacheManager cacheManager, final String cacheName, final Class<K> keyType, final Class<V> valueType) {
        // Make sure we start from a clean state - this is mainly useful for tests
        cacheManager.destroyCache(cacheName);

        // All other configuration options come from the ehcache.xml
        final MutableConfiguration<K, V> configuration = new MutableConfiguration<K, V>().setTypes(keyType, valueType)
                                                                                         .setStoreByValue(false); // Store by reference to avoid copying large objects (e.g. catalog)
        final Cache<K, V> cache = cacheManager.createCache(cacheName, configuration);

        // Re-create the metrics to support dynamically created caches (e.g. for Shiro)
        metricRegistry.removeMatching(new MetricFilter() {
            @Override
            public boolean matches(final String name, final Metric metric) {
                return name != null && name.startsWith(PROP_METRIC_REG_JCACHE_STATISTICS);
            }
        });
        metricRegistry.register(PROP_METRIC_REG_JCACHE_STATISTICS, new JCacheGaugeSet());

        return cache;
    }
}
