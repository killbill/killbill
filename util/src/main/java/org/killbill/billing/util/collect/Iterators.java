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
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.killbill.billing.util.Preconditions;

/**
 * Contains subset of Guava's {@link com.google.common.collect.Iterators} functionality.
 */
public final class Iterators {

    /**
     * Verbatim copy of Guava's {@link com.google.common.collect.Iterators#getLast(Iterator)}
     *
     * Advances {@code iterator} to the end, returning the last element.
     *
     * @return the last element of {@code iterator}
     * @throws NoSuchElementException if the iterator is empty
     */
    public static <T> T getLast(final Iterator<T> iterator) {
        while (true) {
            final T current = iterator.next();
            if (!iterator.hasNext()) {
                return current;
            }
        }
    }

    /**
     * Returns a view containing the result of applying {@code function} to each element of {@code fromIterator}.
     *
     * <p>The returned iterator supports {@code remove()} if {@code fromIterator} does. After a
     * successful {@code remove()} call, {@code fromIterator} no longer contains the corresponding
     * element.
     */
    public static <F, T> Iterator<T> transform(final Iterator<F> fromIterator, final Function<? super F, ? extends T> function) {
        Preconditions.checkNotNull(function);
        return new TransformedIterator<F, T>(fromIterator) {
            @Override
            T transform(final F from) {
                return function.apply(from);
            }
        };
    }

    /**
     * Verbatim copy of what guava's did in {@link com.google.common.collect.ImmutableList#copyOf(Iterator)}.
     *
     * Returns an immutable list containing the given elements, in order.
     *
     * @throws NullPointerException if {@code elements} contains a null element
     */
    public static <E> List<E> toUnmodifiableList(final Iterator<? extends E> elements) {
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
            return List.copyOf(result);
        }
    }

    /**
     * Get size of {@link Iterator}. See also Guava's {@link com.google.common.collect.Iterators#size(Iterator)}.
     *
     * @param iterator to compute its size.
     * @return the size of this iterator.
     */
    public static int size(final Iterator<?> iterator) {
        int count = 0;
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }

    /**
     * Create a {@link Stream} from {@link Iterator}. Iterator first converted to {@link Spliterators} using:
     * <code>Spliterators.spliteratorUnknownSize(attributesIterator, Spliterator.ORDERED)</code>
     *
     * @param iterator to transform to {@link Stream}
     * @param <E> iterator element
     * @return new {@link Stream}
     */
    public static <E> Stream<E> toStream(final Iterator<E> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }
}
