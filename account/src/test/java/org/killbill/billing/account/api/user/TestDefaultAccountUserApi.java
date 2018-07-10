/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import org.joda.time.DateTimeZone;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.AccountTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.DefaultAccount;
import org.killbill.billing.account.api.DefaultMutableAccountData;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.AccountCreationInternalEvent;
import org.killbill.billing.tenant.dao.TenantModelDao;
import org.killbill.billing.tenant.dao.TenantSqlDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.entity.Pagination;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.Subscribe;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.killbill.billing.account.AccountTestUtils.createAccountData;
import static org.killbill.billing.account.AccountTestUtils.createTestAccount;
import static org.killbill.billing.account.api.DefaultMutableAccountData.DEFAULT_BILLING_CYCLE_DAY_LOCAL;
import static org.testng.Assert.assertEquals;

public class TestDefaultAccountUserApi extends AccountTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "Test Account search")
    public void testSearch() throws Exception {
        final MutableAccountData mutableAccountData1 = createAccountData();
        mutableAccountData1.setEmail("john@acme.com");
        mutableAccountData1.setCompanyName("Acme, Inc.");
        final AccountModelDao account1ModelDao = new AccountModelDao(UUID.randomUUID(), mutableAccountData1);
        final AccountData accountData1 = new DefaultAccount(account1ModelDao);
        accountUserApi.createAccount(accountData1, callContext);

        final MutableAccountData mutableAccountData2 = createAccountData();
        mutableAccountData2.setEmail("bob@gmail.com");
        mutableAccountData2.setCompanyName("Acme, Inc.");
        final AccountModelDao account2ModelDao = new AccountModelDao(UUID.randomUUID(), mutableAccountData2);
        final AccountData accountData2 = new DefaultAccount(account2ModelDao);
        accountUserApi.createAccount(accountData2, callContext);

        final Pagination<Account> search1 = accountUserApi.searchAccounts("Inc.", 0L, 5L, callContext);
        Assert.assertEquals(search1.getCurrentOffset(), (Long) 0L);
        Assert.assertNull(search1.getNextOffset());
        Assert.assertEquals(search1.getMaxNbRecords(), (Long) 2L);
        Assert.assertEquals(search1.getTotalNbRecords(), (Long) 2L);
        Assert.assertEquals(ImmutableList.<Account>copyOf(search1.iterator()).size(), 2);

        final Pagination<Account> search2 = accountUserApi.searchAccounts("Inc.", 0L, 1L, callContext);
        Assert.assertEquals(search2.getCurrentOffset(), (Long) 0L);
        Assert.assertEquals(search2.getNextOffset(), (Long) 1L);
        Assert.assertEquals(search2.getMaxNbRecords(), (Long) 2L);
        Assert.assertEquals(search2.getTotalNbRecords(), (Long) 2L);
        Assert.assertEquals(ImmutableList.<Account>copyOf(search2.iterator()).size(), 1);

        final Pagination<Account> search3 = accountUserApi.searchAccounts("acme.com", 0L, 5L, callContext);
        Assert.assertEquals(search3.getCurrentOffset(), (Long) 0L);
        Assert.assertNull(search3.getNextOffset());
        Assert.assertEquals(search3.getMaxNbRecords(), (Long) 2L);
        Assert.assertEquals(search3.getTotalNbRecords(), (Long) 1L);
        Assert.assertEquals(ImmutableList.<Account>copyOf(search3.iterator()).size(), 1);

        // Exact search will fail
        final Pagination<Account> search4 = accountUserApi.searchAccounts("acme.com", -1L, 1L, callContext);
        Assert.assertEquals(search4.getCurrentOffset(), (Long) 0L);
        Assert.assertNull(search4.getNextOffset());
        // Not computed
        Assert.assertNull(search4.getMaxNbRecords());
        Assert.assertEquals(search4.getTotalNbRecords(), (Long) 0L);
        Assert.assertEquals(ImmutableList.<Account>copyOf(search4.iterator()).size(), 0);

        final Pagination<Account> search5 = accountUserApi.searchAccounts("john@acme.com", -1L, 1L, callContext);
        Assert.assertEquals(search5.getCurrentOffset(), (Long) 0L);
        Assert.assertNull(search5.getNextOffset());
        // Not computed
        Assert.assertNull(search5.getMaxNbRecords());
        Assert.assertEquals(search5.getTotalNbRecords(), (Long) 1L);
        Assert.assertEquals(ImmutableList.<Account>copyOf(search5.iterator()).size(), 1);
    }

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
                                                                                    clock.getUTCNow(), null, null, null, null, null, null,
                                                                                    null, null, null, null, null, false);
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


    @Test(groups = "slow", description = "Test Account update to reset notes")
    public void testAccountResetAccountNotes() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(account);
        mutableAccountData.setNotes(null);

        DefaultAccount newAccount = new DefaultAccount(account.getId(), mutableAccountData);
        accountUserApi.updateAccount(newAccount, callContext);

        final Account retrievedAccount = accountUserApi.getAccountById(account.getId(), callContext);

        Assert.assertEquals(retrievedAccount.getName(), account.getName());
        Assert.assertEquals(retrievedAccount.getFirstNameLength(), account.getFirstNameLength());
        Assert.assertEquals(retrievedAccount.getEmail(), account.getEmail());
        Assert.assertEquals(retrievedAccount.getBillCycleDayLocal(), account.getBillCycleDayLocal());
        Assert.assertEquals(retrievedAccount.getCurrency(), account.getCurrency());
        Assert.assertEquals(retrievedAccount.getPaymentMethodId(), account.getPaymentMethodId());
        Assert.assertEquals(retrievedAccount.getTimeZone(), account.getTimeZone());
        Assert.assertEquals(retrievedAccount.getLocale(), account.getLocale());
        Assert.assertEquals(retrievedAccount.getAddress1(), account.getAddress1());
        Assert.assertEquals(retrievedAccount.getAddress2(), account.getAddress2());
        Assert.assertEquals(retrievedAccount.getCompanyName(), account.getCompanyName());
        Assert.assertEquals(retrievedAccount.getCity(), account.getCity());
        Assert.assertEquals(retrievedAccount.getStateOrProvince(), account.getStateOrProvince());
        Assert.assertEquals(retrievedAccount.getPostalCode(), account.getPostalCode());
        Assert.assertEquals(retrievedAccount.getCountry(), account.getCountry());
        Assert.assertEquals(retrievedAccount.getPhone(), account.getPhone());
        Assert.assertEquals(retrievedAccount.isMigrated(), account.isMigrated());
        Assert.assertEquals(retrievedAccount.getParentAccountId(), account.getParentAccountId());
        Assert.assertEquals(retrievedAccount.isPaymentDelegatedToParent(), account.isPaymentDelegatedToParent());
        // Finally check account notes did get reset
        Assert.assertNull(retrievedAccount.getNotes());
    }


    @Test(groups = "slow", description = "Test failure on resetting externalKey", expectedExceptions = IllegalArgumentException.class)
    public void testAccountResetExternalKey() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(account);
        mutableAccountData.setExternalKey(null);

        DefaultAccount newAccount = new DefaultAccount(account.getId(), mutableAccountData);
        accountUserApi.updateAccount(newAccount, callContext);
    }


    @Test(groups = "slow", description = "Test failure on changing externalKey", expectedExceptions = IllegalArgumentException.class)
    public void testAccountChangeExternalKey() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(account);
        mutableAccountData.setExternalKey("somethingVeryDifferent");

        DefaultAccount newAccount = new DefaultAccount(account.getId(), mutableAccountData);
        accountUserApi.updateAccount(newAccount, callContext);
    }


    @Test(groups = "slow", description = "Test failure on resetting currency", expectedExceptions = IllegalArgumentException.class)
    public void testAccountResetCurrency() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(account);
        mutableAccountData.setCurrency(null);

        DefaultAccount newAccount = new DefaultAccount(account.getId(), mutableAccountData);
        accountUserApi.updateAccount(newAccount, callContext);
    }


    @Test(groups = "slow", description = "Test failure on changing currency", expectedExceptions = IllegalArgumentException.class)
    public void testAccountChangeCurrency() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(account);
        mutableAccountData.setCurrency(Currency.AFN);

        DefaultAccount newAccount = new DefaultAccount(account.getId(), mutableAccountData);
        accountUserApi.updateAccount(newAccount, callContext);
    }

    @Test(groups = "slow", description = "Test failure on resetting BCD", expectedExceptions = IllegalArgumentException.class)
    public void testAccountResetBCD() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(account);
        mutableAccountData.setBillCycleDayLocal(DEFAULT_BILLING_CYCLE_DAY_LOCAL);

        DefaultAccount newAccount = new DefaultAccount(account.getId(), mutableAccountData);
        accountUserApi.updateAccount(newAccount, callContext);
    }

    @Test(groups = "slow", description = "Test failure on resetting timeZone", expectedExceptions = IllegalArgumentException.class)
    public void testAccountResetTimeZone() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(account);
        mutableAccountData.setTimeZone(null);

        DefaultAccount newAccount = new DefaultAccount(account.getId(), mutableAccountData);
        accountUserApi.updateAccount(newAccount, callContext);
    }


    @Test(groups = "slow", description = "Test failure on changing timeZone", expectedExceptions = IllegalArgumentException.class)
    public void testAccountChangingTimeZone() throws Exception {
        final Account account = createAccount(new DefaultAccount(createTestAccount()));

        // Update the address and leave other fields null
        final MutableAccountData mutableAccountData = new DefaultMutableAccountData(account);
        mutableAccountData.setTimeZone(DateTimeZone.UTC);

        DefaultAccount newAccount = new DefaultAccount(account.getId(), mutableAccountData);
        accountUserApi.updateAccount(newAccount, callContext);
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

    @Test(groups = "slow", description = "Test un- and re-parenting")
    public void testUnAndReParenting() throws Exception {
        // Create child1
        final AccountModelDao childAccountModelDao1 = createTestAccount();
        Account childAccount1 = accountUserApi.createAccount(new DefaultAccount(childAccountModelDao1), callContext);
        Assert.assertNull(childAccount1.getParentAccountId());
        Assert.assertFalse(childAccount1.isPaymentDelegatedToParent());

        // Create parent
        final Account parentAccount = accountUserApi.createAccount(new DefaultAccount(createTestAccount()), callContext);
        Assert.assertNull(parentAccount.getParentAccountId());
        Assert.assertFalse(parentAccount.isPaymentDelegatedToParent());
        List<Account> childrenAccounts = accountUserApi.getChildrenAccounts(parentAccount.getId(), callContext);
        Assert.assertEquals(childrenAccounts.size(), 0);

        // Associate child1 to parent
        childAccountModelDao1.setId(childAccount1.getId());
        childAccountModelDao1.setParentAccountId(parentAccount.getId());
        childAccountModelDao1.setIsPaymentDelegatedToParent(true);
        accountUserApi.updateAccount(new DefaultAccount(childAccountModelDao1), callContext);

        // Verify mapping
        childAccount1 = accountUserApi.getAccountById(childAccount1.getId(), callContext);
        Assert.assertEquals(childAccount1.getParentAccountId(), parentAccount.getId());
        Assert.assertTrue(childAccount1.isPaymentDelegatedToParent());
        childrenAccounts = accountUserApi.getChildrenAccounts(parentAccount.getId(), callContext);
        Assert.assertEquals(childrenAccounts.size(), 1);
        Assert.assertEquals(childrenAccounts.get(0).getId(), childAccount1.getId());

        // Un-parent child1 from parent
        childAccountModelDao1.setParentAccountId(null);
        childAccountModelDao1.setIsPaymentDelegatedToParent(false);
        accountUserApi.updateAccount(new DefaultAccount(childAccountModelDao1), callContext);

        // Verify mapping
        childAccount1 = accountUserApi.getAccountById(childAccount1.getId(), callContext);
        Assert.assertNull(childAccount1.getParentAccountId());
        Assert.assertFalse(childAccount1.isPaymentDelegatedToParent());
        childrenAccounts = accountUserApi.getChildrenAccounts(parentAccount.getId(), callContext);
        Assert.assertEquals(childrenAccounts.size(), 0);

        // Create child2
        final AccountModelDao childAccountModelDao2 = createTestAccount();
        Account childAccount2 = accountUserApi.createAccount(new DefaultAccount(childAccountModelDao2), callContext);
        Assert.assertNull(childAccount2.getParentAccountId());
        Assert.assertFalse(childAccount2.isPaymentDelegatedToParent());

        // Associate child2 to parent
        childAccountModelDao2.setId(childAccount2.getId());
        childAccountModelDao2.setParentAccountId(parentAccount.getId());
        childAccountModelDao2.setIsPaymentDelegatedToParent(true);
        accountUserApi.updateAccount(new DefaultAccount(childAccountModelDao2), callContext);

        // Verify mapping
        childAccount1 = accountUserApi.getAccountById(childAccount1.getId(), callContext);
        Assert.assertNull(childAccount1.getParentAccountId());
        Assert.assertFalse(childAccount1.isPaymentDelegatedToParent());
        childAccount2 = accountUserApi.getAccountById(childAccount2.getId(), callContext);
        Assert.assertEquals(childAccount2.getParentAccountId(), parentAccount.getId());
        Assert.assertTrue(childAccount2.isPaymentDelegatedToParent());
        childrenAccounts = accountUserApi.getChildrenAccounts(parentAccount.getId(), callContext);
        Assert.assertEquals(childrenAccounts.size(), 1);
        Assert.assertEquals(childrenAccounts.get(0).getId(), childAccount2.getId());
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

    @Test(groups = "slow", description = "Test Account creation with same External Key in different tenants")
    public void testCreateAccountWithSameExternalKeyInDifferentTenants() throws Exception {
        final AccountData accountData = createAccountData();

        final Account account1 = accountUserApi.createAccount(accountData, callContext);
        try {
            // Same tenant
            accountUserApi.createAccount(accountData, callContext);
            Assert.fail();
        } catch (final AccountApiException e) {
            assertEquals(e.getCode(), ErrorCode.ACCOUNT_ALREADY_EXISTS.getCode());
        }

        final TenantSqlDao tenantSqlDao = dbi.onDemand(TenantSqlDao.class);
        final TenantModelDao tenant2 = new TenantModelDao();
        tenantSqlDao.create(tenant2, internalCallContext);
        final CallContext callContext2 = new DefaultCallContext(account1.getId(), tenant2.getId(), callContext.getUserName(), callContext.getCallOrigin(), callContext.getUserType(), callContext.getUserToken(), clock);
        final Account account2 = accountUserApi.createAccount(accountData, callContext2);

        Assert.assertEquals(account1.getExternalKey(), account2.getExternalKey());
        Assert.assertNotEquals(account1.getId(), account2.getId());
    }
}
