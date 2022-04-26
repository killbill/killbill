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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.killbill.billing.util.Preconditions;

/**
 * Contains subset of Guava's {@code com.google.common.collect.Iterables} functionality.
 */
public final class Iterables {

    /**
     * Verbatim copy of {@code com.google.common.collect.Iterables#getLast(Iterable)}.
     *
     * Returns the last element of {@code iterable}. If {@code iterable} is a {@link List} with {@link RandomAccess}
     * support, then this operation is guaranteed to be {@code O(1)}.
     *
     * @return the last element of {@code iterable}
     * @throws NoSuchElementException if the iterable is empty
     */
    public static <T> T getLast(final Iterable<T> iterable) {
        if (iterable instanceof List) {
            final List<T> list = (List<T>) iterable;
            if (list.isEmpty()) {
                throw new NoSuchElementException("Cannot Iterables#getLast(iterable) on empty list");
            }
            return list.get(list.size() - 1);
        }
        return Iterators.getLast(iterable.iterator());
    }

    /**
     * Concat two or more iterable into single {@link Iterable}.
     *
     * @param iterables two or more iterable to concat
     * @param <T> the iterable element
     * @return combined iterable
     */
    public static <T> Iterable<T> concat(final Iterable<T>... iterables) {
        Preconditions.checkNotNull(iterables);
        final List<T> result = new ArrayList<>();
        for (final Iterable<T> iterable : iterables) {
            Preconditions.checkNotNull(iterable, "One of iterable in Iterables#concat() is null");
            iterable.forEach(result::add);
        }
        return result;
    }

    /**
     * Convert {@link Iterable} to immutable {@link List}. If any stream operation needed, use {@link #toStream(Iterable)}
     * instead of this method.
     *
     * @param iterable to convert
     * @param <T> iterable element
     * @return converted iterable as immutable {@link List}
     */
    public static <T> List<T> toUnmodifiableList(final Iterable<? extends T> iterable) {
        return toStream(iterable).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Convert {@link Iterable} to non-parallel {@link Stream} for further processing.
     * @param iterable iterable to convert
     * @param <E> iterable element
     * @return java {@link Stream} , for further processing
     */
    public static <E> Stream<E> toStream(final Iterable<E> iterable) {
        Preconditions.checkNotNull(iterable);
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Verbatim copy of {@code com.google.common.collect.Iterables#isEmpty(Iterable)}.
     *
     * Determines if the given iterable contains no elements.
     *
     * <p>There is no precise {@link Iterator} equivalent to this method, since one can only ask an iterator whether it
     * has any elements <i>remaining</i> (which one does using {@link Iterator#hasNext}).
     *
     * @return {@code true} if the iterable contains no elements
     */
    public static boolean isEmpty(final Iterable<?> iterable) {
        if (iterable instanceof Collection) {
            return ((Collection<?>) iterable).isEmpty();
        }
        return !iterable.iterator().hasNext();
    }

    /** Returns the number of elements in {@code iterable}. */
    public static int size(final Iterable<?> iterable) {
        return (iterable instanceof Collection)
               ? ((Collection<?>) iterable).size()
               : Iterators.size(iterable.iterator());
    }
}
