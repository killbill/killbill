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

import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;

public class TestSimpleHashBiMap extends UtilTestSuiteNoDB {

    private static BiMap<Integer, String> topCountryByPopulation() {
        final BiMap<Integer, String> result = new SimpleHashBiMap<>();
        result.put(1, "China");
        result.put(2, "India");
        result.put(3, "United States");
        result.put(4, "Indonesia");
        result.put(5, "Pakistan");
        return result;
    }

    @Test(groups = "fast")
    public void testPut() {
        final BiMap<Integer, String> countries = topCountryByPopulation();
        countries.put(6, "Brazil");
        Assert.assertEquals(countries.size(), 6);

        try {
            countries.put(7, "Brazil");
            fail("Value: 'Brazil' already exist");
        } catch (final IllegalArgumentException ignored) {}
    }

    @Test(groups = "fast")
    public void testForcePut() {
        final BiMap<Integer, String> countries = topCountryByPopulation();
        countries.put(6, "Brazil");
        countries.forcePut(6, "Brazil");
        countries.forcePut(7, "Brazil");

        Assert.assertEquals(countries.size(), 6);
        Assert.assertNull(countries.get(6)); // because replaced by "7"
        Assert.assertEquals(countries.get(7), "Brazil");
    }

    @Test(groups = "fast")
    public void testInverse() {
        final BiMap<Integer, String> result = topCountryByPopulation();
        result.put(6, "Brazil");

        final BiMap<String, Integer> inversed = result.inverse();
        Assert.assertEquals(6, inversed.size());
        Assert.assertEquals((int) inversed.get("Indonesia"), 4);
        Assert.assertEquals((int) inversed.get("India"), 2);
        Assert.assertEquals((int) inversed.get("Brazil"), 6);
        Assert.assertEquals((int) inversed.get("United States"), 3);
    }

    @Test(groups = "fast")
    public void testForcePutAndInverse() {
        final BiMap<Integer, String> countries = topCountryByPopulation();
        countries.put(6, "Brazil");
        Assert.assertEquals(countries.size(), 6);
        countries.forcePut(6, "Brazil");
        Assert.assertEquals(countries.size(), 6);
        countries.forcePut(7, "Brazil");
        Assert.assertEquals(countries.size(), 6);

        final BiMap<String, Integer> inversed = countries.inverse();
        Assert.assertEquals((int) inversed.get("Indonesia"), 4);
        Assert.assertEquals((int) inversed.get("India"), 2);
        Assert.assertEquals((int) inversed.get("Brazil"), 7);
        Assert.assertEquals((int) inversed.get("United States"), 3);
    }
}
