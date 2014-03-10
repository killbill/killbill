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

package org.killbill.billing.account.api.user;

import java.util.UUID;

import org.killbill.billing.account.AccountTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.DefaultAccount;
import org.killbill.billing.account.api.DefaultMutableAccountData;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.catalog.api.Currency;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.killbill.billing.account.AccountTestUtils.createTestAccount;

public class TestDefaultAccountUserApi extends AccountTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "Test Account update with null values")
    public void testShouldBeAbleToPassNullForSomeFieldsToAvoidUpdate() throws Exception {
        final Account account = accountUserApi.createAccount(new DefaultAccount(createTestAccount()), callContext);

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(null, null, null, 0, null, 0, null,
                                                                                    null, null, null, null, null, null,
                                                                                    null, null, null, null, false, false);
        final String newAddress1 = UUID.randomUUID().toString();
        mutableAccountData.setAddress1(newAddress1);

        accountUserApi.updateAccount(account.getId(), mutableAccountData, callContext);

        final Account retrievedAccount = accountUserApi.getAccountById(account.getId(), callContext);
        Assert.assertEquals(retrievedAccount.getAddress1(), newAddress1);
        Assert.assertEquals(retrievedAccount.getAddress2(), account.getAddress2());
        Assert.assertEquals(retrievedAccount.getCurrency(), account.getCurrency());
        Assert.assertEquals(retrievedAccount.getExternalKey(), account.getExternalKey());
        Assert.assertEquals(retrievedAccount.getBillCycleDayLocal(), account.getBillCycleDayLocal());
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class, description = "Test updating Account BCD does throws an exception")
    public void testShouldntBeAbleToUpdateBillCycleDay() throws Exception {
        final Account account = accountUserApi.createAccount(new DefaultAccount(createTestAccount()), callContext);

        final MutableAccountData otherAccount = new DefaultAccount(account.getId(), account).toMutableAccountData();
        otherAccount.setBillCycleDayLocal(account.getBillCycleDayLocal() + 2);

        accountUserApi.updateAccount(new DefaultAccount(account.getId(), otherAccount), callContext);
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class, description = "Test updating Account currency throws an exception")
    public void testShouldntBeAbleToUpdateCurrency() throws Exception {
        final Account account = accountUserApi.createAccount(new DefaultAccount(createTestAccount()), callContext);

        final MutableAccountData otherAccount = new DefaultAccount(account.getId(), account).toMutableAccountData();
        otherAccount.setCurrency(Currency.GBP);

        accountUserApi.updateAccount(new DefaultAccount(account.getId(), otherAccount), callContext);
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class, description = "Test updating Account externalKey throws an exception")
    public void testShouldntBeAbleToUpdateExternalKey() throws Exception {
        final Account account = accountUserApi.createAccount(new DefaultAccount(createTestAccount()), callContext);

        final MutableAccountData otherAccount = new DefaultAccount(account.getId(), account).toMutableAccountData();
        otherAccount.setExternalKey(UUID.randomUUID().toString());

        accountUserApi.updateAccount(new DefaultAccount(account.getId(), otherAccount), callContext);
    }
}
