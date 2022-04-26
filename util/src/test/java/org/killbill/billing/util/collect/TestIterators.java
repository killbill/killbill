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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestIterators extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void getLast() {
        final List<KeyValue> keyValues = List.of(new KeyValue("a", "1"), new KeyValue("b", "2"), new KeyValue("c", "3"));
        final KeyValue last = Iterators.getLast(keyValues.iterator());

        assertNotNull(last);
        assertEquals(last.getKey(), "c");
        assertEquals(last.getValue(), "3");
    }

    @Test(groups = "fast")
    void getLastWithEmptyList() {
        final List<KeyValue> keyValues = Collections.emptyList();
        try {
            Iterators.getLast(keyValues.iterator());
            fail("iterator is empty");
        } catch (final NoSuchElementException ignored) {}
    }

    @Test(groups = "fast")
    void transform() {
        final List<KeyValue> list = List.of(new KeyValue("a", "1"), new KeyValue("b", "2"), new KeyValue("c", "3"));
        final Iterator<String> keyOnly = Iterators.transform(list.iterator(), KeyValue::getKey);
        assertEquals(keyOnly.next(), "a");
        assertEquals(keyOnly.next(), "b");
        assertEquals(keyOnly.next(), "c");
    }

    @Test(groups = "fast")
    void toUnmodifiableList() {
        final Collection<KeyValue> set = new HashSet<>();
        set.add(new KeyValue("a", "1"));
        set.add(new KeyValue("b", "2"));
        set.add(new KeyValue("b", "2")); // Duplicate. Note that Set.of cannot add duplicate element (IllegalArgumentException: duplicate element)

        final List<KeyValue> list = Iterators.toUnmodifiableList(set.iterator());

        assertNotNull(list);
        assertEquals(list.size(), 2);
        assertTrue(list.contains(new KeyValue("a", "1")));
        assertTrue(list.contains(new KeyValue("b", "2")));
    }

    @Test(groups = "fast")
    void size() {
        final List<String> strings = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
        final int size = Iterators.size(strings.iterator());
        assertEquals(size, 10);
    }
}
