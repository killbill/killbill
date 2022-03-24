/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.util.metrics;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.cache.management.CacheStatisticsMXBean;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.killbill.commons.metrics.api.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Initially forked from Dropwizard Metrics (Apache License 2.0).
// Copyright (c) 2010-2013 Coda Hale, Yammer.com, 2014-2021 Dropwizard Team
public class JCacheGaugeFactory {

    private static final String PROP_METRIC_REG_JCACHE_STATISTICS = "jcache.statistics.";

    private static final String M_BEAN_COORDINATES = "javax.cache:type=CacheStatistics,CacheManager=*,Cache=";

    private static final Logger logger = LoggerFactory.getLogger(JCacheGaugeFactory.class);

    public static Map<String, Gauge<Object>> forCache(final String cacheName) {
        final Set<ObjectInstance> cacheBeans = getCacheBeans(cacheName);
        if (cacheBeans == null || cacheBeans.isEmpty()) {
            logger.warn("Unable to retrieve statis for cache {}. Are JCache statistics enabled?", cacheName);
            return Collections.emptyMap();
        }
        if (cacheBeans.size() > 1) {
            logger.warn("Multiple CacheManager detected for cache {}", cacheName);
        }
        final ObjectInstance cacheBean = cacheBeans.iterator().next();
        final ObjectName objectName = cacheBean.getObjectName();

        final List<String> availableStatsNames = retrieveStatsNames();

        final Map<String, Gauge<Object>> gauges = new HashMap<>(cacheBeans.size() * availableStatsNames.size());
        for (final String statsName : availableStatsNames) {
            final Gauge<Object> jmxAttributeGauge = new JmxAttributeGauge(objectName, statsName);
            gauges.put(PROP_METRIC_REG_JCACHE_STATISTICS + cacheName + "." + toSpinalCase(statsName), jmxAttributeGauge);
        }

        return Collections.unmodifiableMap(gauges);
    }

    private static Set<ObjectInstance> getCacheBeans(final String cacheName) {
        final String mBeanCoordinates = M_BEAN_COORDINATES + cacheName;
        try {
            return ManagementFactory.getPlatformMBeanServer().queryMBeans(ObjectName.getInstance(mBeanCoordinates), null);
        } catch (final MalformedObjectNameException e) {
            logger.error("Unable to retrieve {}. Are JCache statistics enabled?", mBeanCoordinates);
            throw new RuntimeException(e);
        }
    }

    private static List<String> retrieveStatsNames() {
        final Method[] methods = CacheStatisticsMXBean.class.getDeclaredMethods();
        final List<String> availableStatsNames = new ArrayList<>(methods.length);

        for (final Method method : methods) {
            final String methodName = method.getName();
            if (methodName.startsWith("get")) {
                availableStatsNames.add(methodName.substring(3));
            }
        }
        return availableStatsNames;
    }

    private static String toSpinalCase(final String camelCase) {
        return camelCase.replaceAll("(.)(\\p{Upper})", "$1-$2").toLowerCase(Locale.US);
    }
}