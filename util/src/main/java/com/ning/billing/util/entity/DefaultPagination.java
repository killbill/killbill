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

package com.ning.billing.util.entity;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableList;

public class DefaultPagination<T> implements Pagination<T> {

    private final Long currentOffset;
    private final Long approximateNbResults;
    private final Long approximateTotalNbResults;
    private final Iterator<T> delegateIterator;

    // Builder when the streaming API can't be used
    public static <T> Pagination<T> build(final Long offset, final Long limit, final Collection<T> elements) {
        final List<T> allResults = ImmutableList.<T>copyOf(elements);

        final List<T> results;
        if (offset >= allResults.size()) {
            results = ImmutableList.<T>of();
        } else if (offset + limit > allResults.size()) {
            results = allResults.subList(offset.intValue(), allResults.size());
        } else {
            results = allResults.subList(offset.intValue(), offset.intValue() + limit.intValue());
        }
        return new DefaultPagination<T>(offset, (long) results.size(), (long) allResults.size(), results.iterator());
    }

    // Constructor for DAO -> API bridge
    public DefaultPagination(final Pagination original, final Iterator<T> delegate) {
        this(original.getCurrentOffset(), original.getNbResults(), original.getTotalNbResults(), delegate);
    }

    // Constructor for DAO getAll calls
    public DefaultPagination(final Long approximateTotalNbResults, final Iterator<T> results) {
        this(0L, approximateTotalNbResults, approximateTotalNbResults, results);
    }

    public DefaultPagination(final Long currentOffset, final Long approximateNbResults,
                             final Long approximateTotalNbResults, final Iterator<T> delegateIterator) {
        this.currentOffset = currentOffset;
        this.approximateNbResults = approximateNbResults;
        this.approximateTotalNbResults = approximateTotalNbResults;
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
        return currentOffset + approximateNbResults;
    }

    @Override
    public Long getTotalNbResults() {
        return approximateTotalNbResults;
    }

    @Override
    public Long getNbResults() {
        return approximateNbResults;
    }

    @Override
    public Long getNbResultsFromOffset() {
        return approximateNbResults - getNextOffset() + 1;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultPagination{");
        sb.append("currentOffset=").append(currentOffset);
        sb.append(", nextOffset=").append(getNextOffset());
        sb.append(", approximateNbResults=").append(approximateNbResults);
        sb.append(", approximateTotalNbResults=").append(approximateTotalNbResults);
        sb.append(", approximateNbResultsFromOffset=").append(getNbResultsFromOffset());
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

        if (approximateNbResults != null ? !approximateNbResults.equals(that.approximateNbResults) : that.approximateNbResults != null) {
            return false;
        }
        if (approximateTotalNbResults != null ? !approximateTotalNbResults.equals(that.approximateTotalNbResults) : that.approximateTotalNbResults != null) {
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
        result = 31 * result + (approximateNbResults != null ? approximateNbResults.hashCode() : 0);
        result = 31 * result + (approximateTotalNbResults != null ? approximateTotalNbResults.hashCode() : 0);
        result = 31 * result + (delegateIterator != null ? delegateIterator.hashCode() : 0);
        return result;
    }
}
