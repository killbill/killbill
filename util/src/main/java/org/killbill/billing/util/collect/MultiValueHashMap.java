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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MultiValueHashMap<K, E> extends HashMap<K, List<E>> implements MultiValueMap<K, E> {

    @Override
    public void putElement(final K key, final E... elements) {
        if (elements == null || elements.length == 0) {
            throw new IllegalArgumentException("MultiValueHashMap#putElement() contains null or empty element");
        }
        if (super.containsKey(key)) {
            super.get(key).addAll(List.of(elements));
        } else {
            super.put(key, new ArrayList<>(List.of(elements)));
        }
    }
}
