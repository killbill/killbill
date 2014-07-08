/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.dao.PluginPropertySerializer.PluginPropertySerializerException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestPluginPropertySerializer {

    @Test(groups = "fast")
    public void testNoPluginProperty() throws PluginPropertySerializerException {
        final List<PluginProperty> input = new ArrayList<PluginProperty>();

        final byte[] serialized = PluginPropertySerializer.serialize(input);
        final Iterable<PluginProperty> deserialized = PluginPropertySerializer.deserialize(serialized);
        int i = 0;
        for (PluginProperty cur : deserialized) {
            Assert.assertEquals(cur, input.get(i++));
        }
    }

    @Test(groups = "fast")
    public void testSimplePluginProperty() throws PluginPropertySerializerException {
        final List<PluginProperty> input = new ArrayList<PluginProperty>();
        input.add(new PluginProperty("foo", "bar", false));

        final byte[] serialized = PluginPropertySerializer.serialize(input);
        final Iterable<PluginProperty> deserialized = PluginPropertySerializer.deserialize(serialized);
        int i = 0;
        for (PluginProperty cur : deserialized) {
            Assert.assertEquals(cur, input.get(i++));
        }
    }

    @Test(groups = "fast")
    public void testLotsPluginProperty() throws PluginPropertySerializerException {
        final List<PluginProperty> input = new ArrayList<PluginProperty>();
        for (int i = 0; i < 100; i++) {
            input.add(new PluginProperty("foo-" + i, "bar-" + i, false));
        }

        final byte[] serialized = PluginPropertySerializer.serialize(input);
        final Iterable<PluginProperty> deserialized = PluginPropertySerializer.deserialize(serialized);
        int i = 0;
        for (PluginProperty cur : deserialized) {
            Assert.assertEquals(cur, input.get(i++));
        }
    }

    @Test(groups = "fast", enabled = true)
    public void testPluginPropertyWithComplexValue() throws PluginPropertySerializerException {
        final HashMap<String, BigDecimal> something = new HashMap<String, BigDecimal>();
        something.put("yoyo", new BigDecimal("0.0"));
        something.put("what", new BigDecimal("10.0"));
        final List<PluginProperty> input = new ArrayList<PluginProperty>();
        input.add(new PluginProperty("prev", "simple", false));
        input.add(new PluginProperty("foo", something, false));
        input.add(new PluginProperty("next", "easy", false));

        final byte[] serialized = PluginPropertySerializer.serialize(input);
        final Iterable<PluginProperty> deserialized = PluginPropertySerializer.deserialize(serialized);
        int i = 0;
        for (PluginProperty cur : deserialized) {
            if (i == 0 || i == 2) {
                Assert.assertEquals(cur, input.get(i));
            } else {
                Assert.assertEquals(cur.getKey(), "foo");
                Assert.assertTrue(cur.getValue() instanceof Map);
                final Map<String, BigDecimal> mappedValue = (Map<String, BigDecimal>) cur.getValue();
                Assert.assertTrue(mappedValue.containsKey("yoyo"));
                Assert.assertTrue(mappedValue.containsKey("what"));
                Assert.assertTrue(mappedValue.get("yoyo").compareTo(BigDecimal.ZERO) == 0);
                Assert.assertTrue(mappedValue.get("what").compareTo(BigDecimal.TEN) == 0);
            }
            i++;
        }

    }
}
