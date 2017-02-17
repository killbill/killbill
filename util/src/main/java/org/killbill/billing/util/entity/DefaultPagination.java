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

package org.killbill.billing.util.entity;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;

// Assumes the original offset starts at zero.
public class DefaultPagination<T> implements Pagination<T> {

    private final Long currentOffset;
    private final Long limit;
    private final Long totalNbRecords;
    private final Long maxNbRecords;
    private final Iterator<T> delegateIterator;

    // Builders when the streaming API can't be used (should only be used for tests)
    // Notes: elements should be the entire records set (regardless of filtering) otherwise maxNbRecords won't be accurate
    public static <T> Pagination<T> build(final Long offset, final Long limit, final Collection<T> elements) {
        return build(offset, limit, elements.size(), elements);
    }

    public static <T> Pagination<T> build(final Long offset, final Long limit, final Integer maxNbRecords, final Collection<T> elements) {
        final List<T> allResults = ImmutableList.<T>copyOf(elements);

        final List<T> results;
        if (offset >= allResults.size()) {
            results = ImmutableList.<T>of();
        } else if (offset + limit > allResults.size()) {
            results = allResults.subList(offset.intValue(), allResults.size());
        } else {
            results = allResults.subList(offset.intValue(), offset.intValue() + limit.intValue());
        }
        return new DefaultPagination<T>(offset, limit, (long) results.size(), (long) maxNbRecords, results.iterator());
    }

    // Constructor for DAO -> API bridge
    public DefaultPagination(final Pagination original, final Long limit, final Iterator<T> delegate) {
        this(original.getCurrentOffset(), limit, original.getTotalNbRecords(), original.getMaxNbRecords(), delegate);
    }

    // Constructor for DAO getAll calls
    public DefaultPagination(final Long maxNbRecords, final Iterator<T> results) {
        this(0L, Long.MAX_VALUE, maxNbRecords, maxNbRecords, results);
    }

    public DefaultPagination(final Long currentOffset, final Long limit,
                             @Nullable final Long totalNbRecords, @Nullable final Long maxNbRecords,
                             final Iterator<T> delegateIterator) {
        this.currentOffset = currentOffset;
        // See DefaultPaginationSqlDaoHelper
        this.limit = Math.abs(limit);
        this.totalNbRecords = totalNbRecords;
        this.maxNbRecords = maxNbRecords;
        this.delegateIterator = delegateIterator;
    }

    @Override
    public Iterator<T> iterator() {
        return delegateIterator;
    }

    @Override
    public Long getCurrentOffset() {
        return currentOffset;
    }

    @Override
    public Long getNextOffset() {
        final long candidate = currentOffset + limit;
        if (totalNbRecords != null && candidate >= totalNbRecords) {
            // No more results
            return null;
        } else {
            // When we don't know the total number of records, the next offset
            // returned here won't make sense once the last result is returned.
            // It is the responsibility of the client to handle the pagination stop condition
            // in that case (i.e. check if there is no more results).
            return candidate;
        }
    }

    @Override
    public Long getMaxNbRecords() {
        return maxNbRecords;
    }

    @Override
    public Long getTotalNbRecords() {
        return totalNbRecords;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultPagination{");
        sb.append("currentOffset=").append(currentOffset);
        sb.append(", nextOffset=").append(getNextOffset());
        sb.append(", totalNbRecords=").append(totalNbRecords);
        sb.append(", maxNbRecords=").append(maxNbRecords);
        sb.append('}');
        return sb.toString();
    }

    // Expensive! Will compare the content of the iterator
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultPagination that = (DefaultPagination) o;

        if (totalNbRecords != null ? !totalNbRecords.equals(that.totalNbRecords) : that.totalNbRecords != null) {
            return false;
        }
        if (maxNbRecords != null ? !maxNbRecords.equals(that.maxNbRecords) : that.maxNbRecords != null) {
            return false;
        }
        if (currentOffset != null ? !currentOffset.equals(that.currentOffset) : that.currentOffset != null) {
            return false;
        }
        if (delegateIterator != null ? !ImmutableList.<T>copyOf(delegateIterator).equals(ImmutableList.<T>copyOf(that.delegateIterator)) : that.delegateIterator != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = currentOffset != null ? currentOffset.hashCode() : 0;
        result = 31 * result + (totalNbRecords != null ? totalNbRecords.hashCode() : 0);
        result = 31 * result + (maxNbRecords != null ? maxNbRecords.hashCode() : 0);
        result = 31 * result + (delegateIterator != null ? delegateIterator.hashCode() : 0);
        return result;
    }
}
