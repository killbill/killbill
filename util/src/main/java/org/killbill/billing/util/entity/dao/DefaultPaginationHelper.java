/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.util.entity.dao;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.killbill.billing.BillingExceptionBase;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.commons.utils.collect.Iterators;
import org.killbill.billing.util.customfield.ShouldntHappenException;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPaginationHelper {

    private static final Logger log = LoggerFactory.getLogger(DefaultPaginationHelper.class);

    public abstract static class EntityPaginationBuilder<E extends Entity, T extends BillingExceptionBase> {

        public abstract Pagination<E> build(final Long offset, final Long limit, final String pluginName) throws T;
    }

    public static <E extends Entity, T extends BillingExceptionBase> Pagination<E> getEntityPaginationFromPlugins(final boolean maxStatsCrossPlugins, final Iterable<String> plugins, final Long offset, final Long limit, final EntityPaginationBuilder<E, T> entityPaginationBuilder) {
        // Note that we cannot easily do streaming here, since we would have to rely on the statistics
        // returned by the Pagination objects from the plugins and we probably don't want to do that (if
        // one plugin gets it wrong, it may starve the others).
        final List<E> allResults = new LinkedList<E>();
        Long totalNbRecords = 0L;
        Long maxNbRecords = 0L;

        // Search in all plugins (we treat the full set of results as a union with respect to offset/limit)
        boolean firstSearch = true;
        for (final String pluginName : plugins) {
            try {
                final Pagination<E> pages;
                if (allResults.size() >= limit) {
                    // We have enough results, we just keep going (limit 1) to get the stats
                    pages = entityPaginationBuilder.build(firstSearch ? offset : 0L, 1L, pluginName);
                    closeDatabaseConnection(pages);
                } else {
                    pages = entityPaginationBuilder.build(firstSearch ? offset : 0L, limit - allResults.size(), pluginName);
                    pages.forEach(allResults::add);
                }
                // Make sure not to start at 0 for subsequent plugins if previous ones didn't yield any result
                firstSearch = allResults.isEmpty();
                totalNbRecords += pages.getTotalNbRecords();
                if (!maxStatsCrossPlugins) {
                    maxNbRecords += pages.getMaxNbRecords();
                } else {
                    // getPayments and getPaymentMethods return MaxNbRecords across all plugins -- make sure we don't double count
                    maxNbRecords = Math.max(maxNbRecords, pages.getMaxNbRecords());
                }
            } catch (final BillingExceptionBase e) {
                log.warn("Error while searching plugin='{}'", pluginName, e);
                // Non-fatal, continue to search other plugins
            }
        }

        return new DefaultPagination<E>(offset, limit, totalNbRecords, maxNbRecords, allResults.iterator());
    }

    public abstract static class SourcePaginationBuilder<O, T extends BillingExceptionBase> {

        public abstract Pagination<O> build() throws T;
    }

    public static <E extends Entity, O, T extends BillingExceptionBase> Pagination<E> getEntityPagination(final Long limit,
                                                                                                          final SourcePaginationBuilder<O, T> sourcePaginationBuilder,
                                                                                                          final Function<O, E> function) throws T {
        final Pagination<O> modelsDao = sourcePaginationBuilder.build();

        return new DefaultPagination<E>(modelsDao, limit, Iterables.toStream(modelsDao).map(function).filter(Objects::nonNull).iterator());
    }

    public static <E extends Entity, O, T extends BillingExceptionBase> Pagination<E> getEntityPaginationNoException(final Long limit,
                                                                                                                     final SourcePaginationBuilder<O, T> sourcePaginationBuilder,
                                                                                                                     final Function<O, E> function) {
        try {
            return getEntityPagination(limit, sourcePaginationBuilder, function);
        } catch (final BillingExceptionBase e) {
            throw new ShouldntHappenException("No exception expected" + e);
        }
    }

    /**
     * Iterate all element to avoid memory leak.
     */
    private static void closeDatabaseConnection(final Iterable<?> page) {
        Iterators.toUnmodifiableList(page.iterator());
    }
}
