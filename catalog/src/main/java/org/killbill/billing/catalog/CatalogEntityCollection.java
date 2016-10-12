/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.catalog;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.killbill.billing.catalog.api.CatalogEntity;

import com.google.common.collect.Ordering;

public class CatalogEntityCollection<T extends CatalogEntity> implements Collection<T> {

    private final Map<String, T> data;

    public CatalogEntityCollection() {
        this.data = new TreeMap<String, T>(Ordering.<String>natural());
    }

    public CatalogEntityCollection(final T[] entities) {
        this.data = new TreeMap<String, T>(Ordering.<String>natural());
        for (final T cur : entities) {
            addEntry(cur);
        }
    }


    public CatalogEntityCollection(final Iterable<T> entities) {
        this.data = new TreeMap<String, T>(Ordering.<String>natural());
        for (final T cur : entities) {
            addEntry(cur);
        }
    }

    //
    // Returning such entries will be log(N)
    //
    public T findByName(final String entryName) {
        return data.get(entryName);
    }

    public Collection<T> getEntries() {
        return data.values();
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public boolean contains(final Object o) {
        return data.containsKey(((CatalogEntity) o).getName());
    }

    @Override
    public Iterator iterator() {

        // Build an iterator that will return ordered using natural ordering with regard to CatalogEntity#name
        final Iterator<String> keyIterator = data.keySet().iterator();
        final Iterator it = new Iterator() {
            private String prevKey = null;
            @Override
            public boolean hasNext() {
                return keyIterator.hasNext();
            }
            @Override
            public Object next() {
                prevKey = keyIterator.next();
                return data.get(prevKey);
            }
            @Override
            public void remove() {
                if (prevKey != null) {
                    keyIterator.remove();
                    data.remove(prevKey);
                }
            }
        };
        return it;
    }

    @Override
    public boolean add(final T t) {
        addEntry(t);
        return true;
    }

    @Override
    public boolean remove(final Object o) {
        return removeEntry((T) o);
    }

    @Override
    public boolean addAll(final Collection c) {
        final Iterator iterator = c.iterator();
        while (iterator.hasNext()) {
            final T cur = (T) iterator.next();
            addEntry(cur);
        }
        return true;
    }

    @Override
    public void clear() {
        data.clear();
    }

    @Override
    public boolean retainAll(final Collection c) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public boolean removeAll(final Collection c) {
        final Iterator iterator = c.iterator();
        while (iterator.hasNext()) {
            final CatalogEntity cur = (CatalogEntity) iterator.next();
            data.remove(cur.getName());
        }
        return true;
    }

    @Override
    public boolean containsAll(final Collection c) {
        final Iterator iterator = c.iterator();
        while (iterator.hasNext()) {
            final CatalogEntity cur = (CatalogEntity) iterator.next();
            if (!data.containsKey(cur.getName())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Object[] toArray(final Object[] a) {
        return data.values().toArray(a);
    }

    @Override
    public Object[] toArray() {
        return data.values().toArray(new Object[data.size()]);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CatalogEntityCollection)) {
            return false;
        }

        final CatalogEntityCollection<?> that = (CatalogEntityCollection<?>) o;
        return data != null ? data.equals(that.data) : that.data == null;
    }

    @Override
    public int hashCode() {
        return data != null ? data.hashCode() : 0;
    }

    private void addEntry(final T entry) {
        data.put(entry.getName(), entry);
    }

    private boolean removeEntry(final T entry) {
        return data.remove(entry.getName()) != null;
    }

}
