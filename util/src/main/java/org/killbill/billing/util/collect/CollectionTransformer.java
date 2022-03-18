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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.killbill.billing.util.Preconditions;

/**
 * Utility class to help transform any type of collection (including iterable) to another, like:
 * <ul>
 *     <li>{@link #iterableToList(Iterable)}</li>
 *     <li>{@link #iteratorToList(Iterator)}</li>
 * </ul>
 *
 * Or transform collection's element from on to another, like:
 * <ul>
 *     <li>{@link #transformIterator(Iterator, Function)}</li>
 * </ul>
 */
public final class CollectionTransformer {

    public static <E> List<E> iterableToList(final Iterable<? extends E> elements) {
        Preconditions.checkNotNull(elements);
        return StreamSupport.stream(elements.spliterator(), false).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Verbatim copy of what guava's did in {@link com.google.common.collect.ImmutableList#copyOf(Iterator)}
     */
    public static <E> List<E> iteratorToList(final Iterator<? extends E> elements) {
        // We special-case for 0 or 1 elements, but going further is madness.
        if (!elements.hasNext()) {
            return Collections.emptyList();
        }
        final E first = elements.next();
        if (!elements.hasNext()) {
            return List.of(first);
        } else {
            final List<E> result = new ArrayList<>();
            result.add(first);
            elements.forEachRemaining(result::add);
            return result;
        }
    }

    /**
     * Returns a view containing the result of applying {@code function} to each element of {@code
     * fromIterator}.
     *
     * <p>The returned iterator supports {@code remove()} if {@code fromIterator} does. After a
     * successful {@code remove()} call, {@code fromIterator} no longer contains the corresponding
     * element.
     */
    public static <F, T> Iterator<T> transformIterator(
            final Iterator<F> fromIterator,
            final Function<? super F, ? extends T> function) {
        Preconditions.checkNotNull(function);
        return new TransformedIterator<F, T>(fromIterator) {
            @Override
            T transform(final F from) {
                return function.apply(from);
            }
        };
    }
}
