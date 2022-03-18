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

package org.killbill.billing.util.collect;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>This is necessary because seems like {@link Map#of()} not maintaining its
 * insertion order, while Guava {@link com.google.common.collect.ImmutableMap}
 * keep the order.
 * </p>
 *
 * <p>FIXME-1615 :
 * This problem found when Testing
 * <code>TestPluginProperties#testBuildPluginProperties()</code>. We can
 * eliminate this class by changing assertions in
 * <code>TestPluginProperties</code>. Currently no other part of code that
 * use {@link Map#of()} and strictly need to maintain insertion order.
 * </p>
 */
public final class Maps {

    public static <K, V> Map<K, V> of(final K key, final V value) {
        final Map<K, V> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    public static <K, V> Map<K, V> of(final K k1, final V v1, final K k2, final V v2) {
        final Map<K, V> result = new LinkedHashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        return result;
    }

    public static <K, V> Map<K, V> of(final K k1, final V v1, final K k2, final V v2, final K k3, final V v3) {
        final Map<K, V> result = new LinkedHashMap<>();
        result.put(k1, v1);
        result.put(k2, v2);
        result.put(k3, v3);
        return result;
    }
}
