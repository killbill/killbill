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

import java.util.List;
import java.util.Map;

/**
 * Simple {@link Map} extension where the value is a {@link List}. The value will contain duplicate value.
 */
public interface MultiValueMap<K, E> extends Map<K, List<E>> {

    /**
     * Add value to {@link List} associated with the key.
     * @param key map key
     * @param elements value to add to the {@link List}
     */
    void putElement(K key, E... elements);
}
