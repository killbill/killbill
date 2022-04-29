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

import java.util.Set;

import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestSets extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void difference() {
        final Set<String> s1 = Set.of("a", "b", "c", "d", "e");
        final Set<String> s2 = Set.of("a", "c", "d");

        final Set<String> diff = Sets.difference(s1, s2);

        Assert.assertEquals(diff.size(), 2);
        Assert.assertEquals(diff, Set.of("b", "e"));
    }
}
