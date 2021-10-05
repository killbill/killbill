/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.account.dao;

import org.killbill.billing.account.AccountTestSuiteNoDB;
import org.killbill.billing.account.AccountTestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestAccountModelDao extends AccountTestSuiteNoDB {

    @Test(groups = "fast")
    public void testShouldBeAbleToUpdateBCD() {
        final AccountModelDao currentAccount = AccountTestUtils.createTestAccount(17);
        final AccountModelDao newAccount = AccountTestUtils.createTestAccount(20);
        newAccount.setExternalKey(currentAccount.getExternalKey());

        try {
            newAccount.validateAccountUpdateInput(currentAccount, true, false);
            Assert.fail("Should not be a able to update the BCD");
        } catch (final IllegalArgumentException e) {
            // Expected
        }

        newAccount.validateAccountUpdateInput(currentAccount, true, true);
    }
}