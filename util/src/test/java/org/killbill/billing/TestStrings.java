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

package org.killbill.billing;

import org.killbill.commons.utils.Strings;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TestStrings extends UtilTestSuiteNoDB {

    @DataProvider(name = "testContainsUpperCase")
    public Object[][] testContainsUpperCaseData() {
        return new Object[][] {
                {"this_is_lower_case", false},
                {"lower-case-with-dash", false},
                {"lower-case-with_number-123", false},
                {"lower-case_with_other_char-!@#$", false},
                {"ALL_UPPER_CASE", true},
                {"lower_UPPER_comBINED", true},
                {null, false},
                {"", false}
        };
    }

    @Test(groups = "fast", dataProvider = "testContainsUpperCase")
    public void testContainsUpperCase(final String sample, final boolean valid) {
        Assert.assertEquals(Strings.containsUpperCase(sample), valid);
    }
}
