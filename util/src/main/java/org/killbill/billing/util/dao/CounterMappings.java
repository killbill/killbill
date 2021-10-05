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

package org.killbill.billing.util.dao;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CounterMappings {

    private final String key;
    private final Integer total;

    public CounterMappings(final String key, final Integer count) {
        this.key = key;
        this.total = count;
    }

    public String getKey() {
        return key;
    }

    public Integer getTotal() {
        return total;
    }

    public static Map<String, Integer> toMap(final Iterable<CounterMappings> mappings) {
        final Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        for (final CounterMappings mapping : mappings) {
            result.put(mapping.getKey(), mapping.getTotal());
        }
        return result;
    }
}
