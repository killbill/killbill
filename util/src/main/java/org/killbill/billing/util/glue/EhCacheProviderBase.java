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

import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;

import org.killbill.billing.util.config.definition.EhCacheConfig;
import org.killbill.xmlloader.UriAccessor;

import com.codahale.metrics.MetricRegistry;

abstract class EhCacheProviderBase extends CacheProviderBase {

    final URL xmlConfigurationURL;

    EhCacheProviderBase(final MetricRegistry metricRegistry, final EhCacheConfig cacheConfig) {
        super(metricRegistry);

        try {
            xmlConfigurationURL = UriAccessor.toURL(cacheConfig.getCacheConfigLocation());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    <K, V> void createCache(final CacheManager cacheManager, final String cacheName, final Class<K> keyType, final Class<V> valueType) {
        // All other configuration options come from the ehcache.xml
        final Configuration configuration = new MutableConfiguration<K, V>().setTypes(keyType, valueType)
                                                                            .setStoreByValue(false); // Store by reference to avoid copying large objects (e.g. catalog)
        super.createCache(cacheManager, cacheName, configuration);
    }
}
