/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.account.api.user;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.DefaultMutableAccountData;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.account.dao.AccountDaoTestBase;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;

public class DefaultAccountUserApiTestWithDB extends AccountDaoTestBase {

    private AccountUserApi accountUserApi;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final DefaultCallContextFactory callContextFactory = new DefaultCallContextFactory(clock);
        accountUserApi = new DefaultAccountUserApi(callContextFactory, internalCallContextFactory, accountDao);
    }

    @Test(groups = "slow")
    public void testShouldBeAbleToPassNullForSomeFieldsToAvoidUpdate() throws Exception {
        final Account account = accountUserApi.createAccount(new DefaultAccount(createTestAccount()), callContext);

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(null, null, null, 0, null, null, null,
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
        Assert.assertEquals(retrievedAccount.getBillCycleDay().getDayOfMonthLocal(), account.getBillCycleDay().getDayOfMonthLocal());
        Assert.assertEquals(retrievedAccount.getBillCycleDay().getDayOfMonthUTC(), account.getBillCycleDay().getDayOfMonthUTC());
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class)
    public void testShouldntBeAbleToUpdateBillCycleDay() throws Exception {
        final Account account = accountUserApi.createAccount(new DefaultAccount(createTestAccount()), callContext);

        final MutableAccountData otherAccount = new DefaultAccount(account.getId(), account).toMutableAccountData();
        otherAccount.setBillCycleDay(new BillCycleDay() {
            @Override
            public int getDayOfMonthUTC() {
                return account.getBillCycleDay().getDayOfMonthUTC() + 2;
            }

            @Override
            public int getDayOfMonthLocal() {
                return account.getBillCycleDay().getDayOfMonthLocal() + 2;
            }
        });

        accountUserApi.updateAccount(new DefaultAccount(account.getId(), otherAccount), callContext);
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class)
    public void testShouldntBeAbleToUpdateCurrency() throws Exception {
        final Account account = accountUserApi.createAccount(new DefaultAccount(createTestAccount()), callContext);

        final MutableAccountData otherAccount = new DefaultAccount(account.getId(), account).toMutableAccountData();
        otherAccount.setCurrency(Currency.GBP);

        accountUserApi.updateAccount(new DefaultAccount(account.getId(), otherAccount), callContext);
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class)
    public void testShouldntBeAbleToUpdateExternalKey() throws Exception {
        final Account account = accountUserApi.createAccount(new DefaultAccount(createTestAccount()), callContext);

        final MutableAccountData otherAccount = new DefaultAccount(account.getId(), account).toMutableAccountData();
        otherAccount.setExternalKey(UUID.randomUUID().toString());

        accountUserApi.updateAccount(new DefaultAccount(account.getId(), otherAccount), callContext);
    }
}
