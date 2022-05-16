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

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Simple {@link BiMap} implementation: This class extends {@link HashMap} and use most of its functionality except
 * for method that described in {@link BiMap}.
 */
public class SimpleHashBiMap<K, V> extends HashMap<K, V> implements BiMap<K, V> {

    static final Set<Collector.Characteristics> CH_ID = Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.IDENTITY_FINISH));

    public V put(final K key, final V value) {
        if (values().contains(value)) {
            throw new IllegalArgumentException("Value already present: " + value);
        }
        return super.put(key, value);
    }

    @Override
    public V forcePut(final K key, final V value) {
        return super.put(key, value);
    }

    @Override
    public BiMap<V, K> inverse() {
        return this.keySet()
                   .stream()
                   .collect(new CollectorImpl<>(SimpleHashBiMap::new,
                                                uniqKeysMapAccumulator(this::get, s -> s),
                                                uniqKeysMapMerger(),
                                                CH_ID));
    }

    @Override
    public Set<V> values() {
        return new HashSet<>(super.values());
    }

    /**
     * Verbatim copy of JDK CollectorImpl. Need to put here because it's not public.
     */
    static class CollectorImpl<T, A, R> implements Collector<T, A, R> {
        private final Supplier<A> supplier;
        private final BiConsumer<A, T> accumulator;
        private final BinaryOperator<A> combiner;
        private final Function<A, R> finisher = castingIdentity();
        private final Set<Characteristics> characteristics;

        CollectorImpl(final Supplier<A> supplier, final BiConsumer<A, T> accumulator, final BinaryOperator<A> combiner, final Set<Characteristics> characteristics) {
            this.supplier = supplier;
            this.accumulator = accumulator;
            this.combiner = combiner;
            this.characteristics = characteristics;
        }

        @Override
        public BiConsumer<A, T> accumulator() {
            return accumulator;
        }

        @Override
        public Supplier<A> supplier() {
            return supplier;
        }

        @Override
        public BinaryOperator<A> combiner() {
            return combiner;
        }

        @Override
        public Function<A, R> finisher() {
            return finisher;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return characteristics;
        }
    }
    private static <I, R> Function<I, R> castingIdentity() {
        return i -> (R) i;
    }

    /**
     * Verbatim copy of JDK {@code CollectorImpl#ununiqKeysMapAccumulator}.
     */
    private static <T, K, V> BiConsumer<Map<K, V>, T> uniqKeysMapAccumulator(final Function<? super T, ? extends K> keyMapper,
                                                                             final Function<? super T, ? extends V> valueMapper) {
        return (map, element) -> {
            final K k = keyMapper.apply(element);
            final V v = Objects.requireNonNull(valueMapper.apply(element));
            final V u = map.putIfAbsent(k, v);
            if (u != null) {
                throw new IllegalStateException(String.format("Duplicate key %s (attempted merging values %s and %s)", k, u, v));
            }
        };
    }

    /**
     * Verbatim copy of JDK {@code CollectorImpl#uniqKeysMapMerger}.
     */
    private static <K, V, M extends Map<K,V>> BinaryOperator<M> uniqKeysMapMerger() {
        return (m1, m2) -> {
            for (final Map.Entry<K,V> e : m2.entrySet()) {
                final K k = e.getKey();
                final V v = Objects.requireNonNull(e.getValue());
                final V u = m1.putIfAbsent(k, v);
                if (u != null) {
                    throw new IllegalStateException(String.format("Duplicate key %s (attempted merging values %s and %s)", k, u, v));
                }
            }
            return m1;
        };
    }
}
