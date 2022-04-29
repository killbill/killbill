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

import java.util.Set;
import java.util.stream.Collectors;

import org.killbill.billing.util.Preconditions;

public final class Sets {

    /**
     * Will return a set of elements that exists in {@code set1} but not exist in {@code set2}. Both parameters cannot
     * be null.
     */
    public static <E> Set<E> difference(final Set<E> set1, final Set<E> set2) {
        Preconditions.checkNotNull(set1, "set1 in Sets#difference() is null");
        Preconditions.checkNotNull(set2, "set2 in Sets#difference() is null");
        return set1.stream()
                   .filter(element -> !set2.contains(element))
                   .collect(Collectors.toUnmodifiableSet());
    }
}
