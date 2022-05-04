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
import java.util.List;
import java.util.NoSuchElementException;

import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

public class TestIterables extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testGetLast() {
        final List<KeyValue> keyValues = List.of(new KeyValue("a", "1"), new KeyValue("b", "2"), new KeyValue("c", "3"));
        final KeyValue last = Iterables.getLast(keyValues);

        assertNotNull(last);
        assertEquals(last.getKey(), "c");
        assertEquals(last.getValue(), "3");
    }

    @Test(groups = "fast")
    public void testGetLastWithEmptyList() {
        final List<KeyValue> keyValues = Collections.emptyList();
        try {
            Iterables.getLast(keyValues);
            fail("iterable is empty");
        } catch (final NoSuchElementException ignored) {}
    }

    @Test(groups = "fast")
    public void testConcat() {
        final Iterable<String> a = List.of("a", "b", "c");
        final Iterable<String> b = List.of("d", "e");
        final Iterable<String> c = List.of("1", "2", "3");

        final Iterable<String> result = Iterables.concat(a, b, c);

        assertNotNull(result);
        assertEquals(Iterators.size(result.iterator()), 8);
    }

    @Test(groups = "fast")
    public void testConcatWithNullElement() {
        final Iterable<String> a = List.of("a", "b", "c");
        final Iterable<String> b = List.of("1", "2", "3");

        try {
            Iterables.concat(a, null, b);
            fail("One of iterable element is null");
        } catch (NullPointerException ignored) {}
    }

    @Test(groups = "fast")
    public void testIsEmpty() {
        final Iterable<String> a = List.of("a", "b", "c");
        final Iterable<String> b = Collections.emptyList();

        assertFalse(Iterables.isEmpty(a));
        assertTrue(Iterables.isEmpty(b));
    }

    @Test(groups = "fast")
    public void testSize() {
        final Iterable<String> a = List.of("a", "b", "c");
        final Iterable<String> b = Collections.emptyList();

        assertEquals(Iterables.size(a), 3);
        assertEquals(Iterables.size(b), 0);
    }

    @Test(groups = "fast")
    public void testContains() {
        final Iterable<String> strings = List.of("a", "b", "c");
        final Iterable<String> empty = Collections.emptyList();
        final Iterable<KeyValue> keyValues = List.of(new KeyValue("a", "1"),
                                                     new KeyValue("b", "2"));

        assertTrue(Iterables.contains(strings, "a"));
        assertFalse(Iterables.contains(strings, "d"));

        assertFalse(Iterables.contains(empty, "a"));

        assertTrue(Iterables.contains(keyValues, new KeyValue("b", "2")));
        assertFalse(Iterables.contains(keyValues, new KeyValue("a", "2")));
    }
}
