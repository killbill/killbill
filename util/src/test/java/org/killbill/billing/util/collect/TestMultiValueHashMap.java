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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestMultiValueHashMap {

    @Test(groups = "fast")
    public void putElement() {
        final MultiValueMap<String, KeyValue> multiValueMap = new MultiValueHashMap<>();
        multiValueMap.putElement("one", new KeyValue("a", "A"));
        multiValueMap.putElement("two", new KeyValue("b", "B"), new KeyValue("c", "C"));

        assertEquals(multiValueMap.size(), 2);
        assertEquals(multiValueMap.get("one").size(), 1);
        assertEquals(multiValueMap.get("two").size(), 2);

        multiValueMap.putElement("two", new KeyValue("c", "C")); // same element, to prove that values size will keep increase.

        assertEquals(multiValueMap.get("two").size(), 3);
    }

    @Test(groups = "fast")
    public void putElementThenThrowIllegalArgs() {
        final MultiValueMap<String, KeyValue> multiValueMap = new MultiValueHashMap<>();

        try {
            multiValueMap.putElement("one", null);
            fail("IllegalArgumentException should be thrown because null element");
        } catch (final IllegalArgumentException ignored) {}

        try {
            multiValueMap.putElement("one");
            fail("IllegalArgumentException should be thrown because empty element");
        } catch (final IllegalArgumentException ignored) {}
    }
}
