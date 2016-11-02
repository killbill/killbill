/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.account.api.user;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.AccountTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.DefaultAccount;
import org.killbill.billing.account.api.DefaultMutableAccountData;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.AccountCreationInternalEvent;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.killbill.billing.account.AccountTestUtils.createTestAccount;
import static org.testng.Assert.assertEquals;

public class TestDefaultAccountUserApi extends AccountTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "Test Account creation generates an event")
    public void testBusEvents() throws Exception {
        final AccountEventHandler eventHandler = new AccountEventHandler();
        bus.register(eventHandler);

        final AccountModelDao accountModelDao = createTestAccount();
        final AccountData defaultAccount = new DefaultAccount(accountModelDao);
        final Account account = createAccount(defaultAccount);

        await().atMost(10, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return eventHandler.getAccountCreationInternalEvents().size() == 1;
            }
        });
        final AccountCreationInternalEvent accountCreationInternalEvent = eventHandler.getAccountCreationInternalEvents().get(0);
        Assert.assertEquals(accountCreationInternalEvent.getId(), account.getId());
        // account_record_id is most likely 1, although, depending on the DB, we cannot be sure
        Assert.assertNotNull(accountCreationInternalEvent.getSearchKey1());
        Assert.assertEquals(accountCreationInternalEvent.getSearchKey2(), internalCallContext.getTenantRecordId());
    }

    @Test(groups = "slow", description = "Test Account update with null values")
    public void testShouldBeAbleToPassNullForSomeFieldsToAvoidUpdate() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(null, null, null, 0, null, null, false, 0, null,
                                                                                    null, null, null, null, null, null,
                                                                                    null, null, null, null, null, false, false);
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
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        final MutableAccountData otherAccount = new DefaultAccount(account.getId(), account).toMutableAccountData();
        otherAccount.setBillCycleDayLocal(account.getBillCycleDayLocal() + 2);

        accountUserApi.updateAccount(new DefaultAccount(account.getId(), otherAccount), callContext);
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class, description = "Test updating Account currency throws an exception")
    public void testShouldntBeAbleToUpdateCurrency() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        final MutableAccountData otherAccount = new DefaultAccount(account.getId(), account).toMutableAccountData();
        otherAccount.setCurrency(Currency.GBP);

        accountUserApi.updateAccount(new DefaultAccount(account.getId(), otherAccount), callContext);
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class, description = "Test updating Account externalKey throws an exception")
    public void testShouldntBeAbleToUpdateExternalKey() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        final MutableAccountData otherAccount = new DefaultAccount(account.getId(), account).toMutableAccountData();
        otherAccount.setExternalKey(UUID.randomUUID().toString());

        accountUserApi.updateAccount(new DefaultAccount(account.getId(), otherAccount), callContext);
    }

    private static final class AccountEventHandler {

        private final List<AccountCreationInternalEvent> accountCreationInternalEvents = new LinkedList<AccountCreationInternalEvent>();

        @Subscribe
        public void handleAccountCreationInternalEvent(final AccountCreationInternalEvent creationInternalEvent) {
            this.accountCreationInternalEvents.add(creationInternalEvent);
        }

        public List<AccountCreationInternalEvent> getAccountCreationInternalEvents() {
            return accountCreationInternalEvents;
        }
    }

    @Test(groups = "slow", description = "Test Account create Parent and Child")
    public void testCreateParentAndChildAccounts() throws Exception {

        final Account parentAccount = accountUserApi.createAccount(new DefaultAccount(createTestAccount()), callContext);

        final AccountModelDao childAccountModel = createTestAccount();
        childAccountModel.setParentAccountId(parentAccount.getId());
        childAccountModel.setIsPaymentDelegatedToParent(true);
        final AccountData childAccountData = new DefaultAccount(childAccountModel);
        final Account childAccount = accountUserApi.createAccount(childAccountData, callContext);

        final Account retrievedChildAccount = accountUserApi.getAccountById(childAccount.getId(), callContext);

        Assert.assertNull(parentAccount.getParentAccountId());
        Assert.assertNotNull(retrievedChildAccount.getParentAccountId());
        Assert.assertEquals(retrievedChildAccount.getId(), childAccount.getId());
        Assert.assertEquals(retrievedChildAccount.getParentAccountId(), parentAccount.getId());
        Assert.assertEquals(retrievedChildAccount.isPaymentDelegatedToParent(), childAccount.isPaymentDelegatedToParent());
    }

    @Test(groups = "slow", description = "Test Account create Child with a non existing Parent",
            expectedExceptions = AccountApiException.class, expectedExceptionsMessageRegExp = "Account does not exist for id .*")
    public void testCreateChildAccountWithInvalidParent() throws Exception {

        final AccountModelDao childAccountModel = createTestAccount();
        childAccountModel.setParentAccountId(UUID.randomUUID());
        final AccountData childAccountData = new DefaultAccount(childAccountModel);
        final Account childAccount = accountUserApi.createAccount(childAccountData, callContext);

    }

    @Test(groups = "slow", description = "Test Account creation with External Key over limit")
        public void testCreateAccountWithExternalKeyOverLimit() throws Exception {
        AccountModelDao accountModelDao = createTestAccount();
        // Set an externalKey of 256 characters (over limit)
        accountModelDao.setExternalKey("Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa. Cum sociis natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Donec quam felis, ultricies nec, pellentesque eu, pretium quis,.");
        final AccountData accountData = new DefaultAccount(accountModelDao);
        try {
            accountUserApi.createAccount(accountData, callContext);
            Assert.fail();
        } catch (final AccountApiException e) {
            assertEquals(e.getCode(), ErrorCode.EXTERNAL_KEY_LIMIT_EXCEEDED.getCode());
        }
    }
}
