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

package com.ning.billing.account.api;

import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.AccountTestSuiteNoDB;
import com.ning.billing.catalog.api.Currency;

public class TestDefaultAccount extends AccountTestSuiteNoDB {

    @Test(groups = "fast", description="Test if Account constructor can accept null values")
    public void testConstructorAcceptsNullValues() throws Exception {
        final AccountData accountData = getNullAccountData();
        final Account account = new DefaultAccount(UUID.randomUUID(), accountData);

        Assert.assertEquals(account.getExternalKey(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getEmail(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getName(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getFirstNameLength(), DefaultAccount.DEFAULT_INTEGER_VALUE);
        Assert.assertEquals(account.getCurrency(), DefaultAccount.DEFAULT_CURRENCY_VALUE);
        Assert.assertEquals(account.getBillCycleDayLocal(), DefaultAccount.DEFAULT_INTEGER_VALUE);
        Assert.assertNull(account.getPaymentMethodId());
        Assert.assertEquals(account.getTimeZone(), DefaultAccount.DEFAULT_TIMEZONE_VALUE);
        Assert.assertEquals(account.getLocale(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getAddress1(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getAddress2(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getCompanyName(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getCity(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getStateOrProvince(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getCountry(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getPostalCode(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.getPhone(), DefaultAccount.DEFAULT_STRING_VALUE);
        Assert.assertEquals(account.isMigrated(), DefaultAccount.DEFAULT_MIGRATED_VALUE);
        Assert.assertEquals(account.isNotifiedForInvoices(), DefaultAccount.DEFAULT_NOTIFIED_FOR_INVOICES_VALUE);
    }

    @Test(groups = "fast", description="Test mergeWithDelegate Account api")
    public void testMergeWithDelegate() throws Exception {
        final AccountData accountData = getNullAccountData();
        final Account account = new DefaultAccount(UUID.randomUUID(), accountData);

        final AccountData secondAccountData = getAccountData();
        final Account secondAccount = new DefaultAccount(UUID.randomUUID(), secondAccountData);

        final Account finalAccount = account.mergeWithDelegate(secondAccount);
        checkAccountEquals(finalAccount, secondAccount);
    }

    @Test(groups = "fast", description="Test Account BCD merge")
    public void testBCDMerges() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final Currency currency = Currency.BRL;
        final String externalKey = UUID.randomUUID().toString();

        final AccountData accountDataWithNullBCD = getAccountData(null, currency, externalKey);
        final Account accountWithNullBCD = new DefaultAccount(accountId, accountDataWithNullBCD);
        // Null BCD -> 0 BCD
        Assert.assertEquals(accountWithNullBCD.getBillCycleDayLocal(), (Integer) 0);

        final AccountData accountDataWithZeroBCD = getAccountData(0, currency, externalKey);
        final Account accountWithZeroBCD = new DefaultAccount(accountId, accountDataWithZeroBCD);
        // Null BCD and 0 BCD -> 0 BCD
        Assert.assertEquals(accountWithNullBCD.mergeWithDelegate(accountWithZeroBCD).getBillCycleDayLocal(), (Integer) 0);

        final AccountData accountDataWithRealBCD = getAccountData(12, currency, externalKey);
        final Account accountWithRealBCD = new DefaultAccount(accountId, accountDataWithRealBCD);
        // Null BCD and real BCD -> real BCD
        Assert.assertEquals(accountWithNullBCD.mergeWithDelegate(accountWithRealBCD).getBillCycleDayLocal(), (Integer) 12);

        final AccountData accountDataWithAnotherRealBCD = getAccountData(20, currency, externalKey);
        final Account accountWithAnotherBCD = new DefaultAccount(accountId, accountDataWithAnotherRealBCD);
        // Same BCD
        Assert.assertEquals(accountWithAnotherBCD.mergeWithDelegate(accountWithAnotherBCD).getBillCycleDayLocal(), (Integer) 20);
        try {
            // Different BCD
            Assert.assertEquals(accountWithAnotherBCD.mergeWithDelegate(accountWithRealBCD).getBillCycleDayLocal(), (Integer) 20);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    private void checkAccountEquals(final Account finalAccount, final Account delegateAccount) {
        Assert.assertEquals(finalAccount.getExternalKey(), delegateAccount.getExternalKey());
        Assert.assertEquals(finalAccount.getEmail(), delegateAccount.getEmail());
        Assert.assertEquals(finalAccount.getName(), delegateAccount.getName());
        Assert.assertEquals(finalAccount.getFirstNameLength(), delegateAccount.getFirstNameLength());
        Assert.assertEquals(finalAccount.getCurrency(), delegateAccount.getCurrency());
        Assert.assertEquals(finalAccount.getBillCycleDayLocal(), delegateAccount.getBillCycleDayLocal());
        Assert.assertEquals(finalAccount.getPaymentMethodId(), delegateAccount.getPaymentMethodId());
        Assert.assertEquals(finalAccount.getTimeZone(), delegateAccount.getTimeZone());
        Assert.assertEquals(finalAccount.getLocale(), delegateAccount.getLocale());
        Assert.assertEquals(finalAccount.getAddress1(), delegateAccount.getAddress1());
        Assert.assertEquals(finalAccount.getAddress2(), delegateAccount.getAddress2());
        Assert.assertEquals(finalAccount.getCompanyName(), delegateAccount.getCompanyName());
        Assert.assertEquals(finalAccount.getCity(), delegateAccount.getCity());
        Assert.assertEquals(finalAccount.getStateOrProvince(), delegateAccount.getStateOrProvince());
        Assert.assertEquals(finalAccount.getCountry(), delegateAccount.getCountry());
        Assert.assertEquals(finalAccount.getPostalCode(), delegateAccount.getPostalCode());
        Assert.assertEquals(finalAccount.getPhone(), delegateAccount.getPhone());
        Assert.assertEquals(finalAccount.isMigrated(), delegateAccount.isMigrated());
        Assert.assertEquals(finalAccount.isNotifiedForInvoices(), delegateAccount.isNotifiedForInvoices());
    }

    private AccountData getAccountData() {
        return getAccountData(Integer.MIN_VALUE, Currency.AUD, UUID.randomUUID().toString());
    }

    private AccountData getAccountData(final Integer bcd, final Currency currency, final String externalKey) {
        final AccountData secondAccountData = Mockito.mock(AccountData.class);
        Mockito.when(secondAccountData.getExternalKey()).thenReturn(externalKey);
        Mockito.when(secondAccountData.getEmail()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.getName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.getFirstNameLength()).thenReturn(Integer.MAX_VALUE);
        Mockito.when(secondAccountData.getCurrency()).thenReturn(currency);
        Mockito.when(secondAccountData.getBillCycleDayLocal()).thenReturn(bcd);
        Mockito.when(secondAccountData.getPaymentMethodId()).thenReturn(UUID.randomUUID());
        Mockito.when(secondAccountData.getTimeZone()).thenReturn(DateTimeZone.forID("EST"));
        Mockito.when(secondAccountData.getLocale()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.getAddress1()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.getAddress2()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.getCompanyName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.getCity()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.getStateOrProvince()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.getCountry()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.getPostalCode()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.getPhone()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(secondAccountData.isMigrated()).thenReturn(true);
        Mockito.when(secondAccountData.isNotifiedForInvoices()).thenReturn(true);
        return secondAccountData;
    }

    private AccountData getNullAccountData() {
        // Make Mockito return null for all values
        return Mockito.mock(AccountData.class, new Answer<Object>() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                return null;
            }
        });
    }
}
