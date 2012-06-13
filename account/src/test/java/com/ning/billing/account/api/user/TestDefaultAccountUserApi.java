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

import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.DefaultAccountEmail;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.AccountEmailDao;
import com.ning.billing.account.dao.MockAccountDao;
import com.ning.billing.account.dao.MockAccountEmailDao;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;

public class TestDefaultAccountUserApi {
    private final CallContextFactory factory = Mockito.mock(CallContextFactory.class);
    private final CallContext callContext = Mockito.mock(CallContext.class);

    private AccountDao accountDao;
    private AccountEmailDao accountEmailDao;
    private DefaultAccountUserApi accountUserApi;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        accountDao = new MockAccountDao(Mockito.mock(Bus.class));
        accountEmailDao = new MockAccountEmailDao();
        accountUserApi = new DefaultAccountUserApi(factory, accountDao, accountEmailDao);
    }

    @Test(groups = "fast")
    public void testCreateAccount() throws Exception {
        final UUID id = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final String email = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString();
        final int firstNameLength = Integer.MAX_VALUE;
        final Currency currency = Currency.BRL;
        final int billCycleDay = Integer.MIN_VALUE;
        final UUID paymentMethodId = UUID.randomUUID();
        final DateTimeZone timeZone = DateTimeZone.UTC;
        final String locale = UUID.randomUUID().toString();
        final String address1 = UUID.randomUUID().toString();
        final String address2 = UUID.randomUUID().toString();
        final String companyName = UUID.randomUUID().toString();
        final String city = UUID.randomUUID().toString();
        final String stateOrProvince = UUID.randomUUID().toString();
        final String country = UUID.randomUUID().toString();
        final String postalCode = UUID.randomUUID().toString();
        final String phone = UUID.randomUUID().toString();
        final boolean isMigrated = true;
        final boolean isNotifiedForInvoices = false;
        final AccountData data = new DefaultAccount(id, externalKey, email, name, firstNameLength, currency, billCycleDay,
                                                    paymentMethodId, timeZone, locale, address1, address2, companyName,
                                                    city, stateOrProvince, country, postalCode, phone, isMigrated, isNotifiedForInvoices);

        accountUserApi.createAccount(data, callContext);

        final Account account = accountDao.getAccountByKey(externalKey);
        Assert.assertEquals(account.getExternalKey(), externalKey);
        Assert.assertEquals(account.getEmail(), email);
        Assert.assertEquals(account.getName(), name);
        Assert.assertEquals(account.getFirstNameLength(), firstNameLength);
        Assert.assertEquals(account.getCurrency(), currency);
        Assert.assertEquals(account.getBillCycleDay(), billCycleDay);
        Assert.assertEquals(account.getPaymentMethodId(), paymentMethodId);
        Assert.assertEquals(account.getTimeZone(), timeZone);
        Assert.assertEquals(account.getLocale(), locale);
        Assert.assertEquals(account.getAddress1(), address1);
        Assert.assertEquals(account.getAddress2(), address2);
        Assert.assertEquals(account.getCompanyName(), companyName);
        Assert.assertEquals(account.getCity(), city);
        Assert.assertEquals(account.getStateOrProvince(), stateOrProvince);
        Assert.assertEquals(account.getCountry(), country);
        Assert.assertEquals(account.getPostalCode(), postalCode);
        Assert.assertEquals(account.getPhone(), phone);
        Assert.assertEquals(account.isMigrated(), isMigrated);
        Assert.assertEquals(account.isNotifiedForInvoices(), isNotifiedForInvoices);
    }

    @Test(groups = "fast")
    public void testAddEmail() throws Exception {
        final UUID accountId = UUID.randomUUID();

        // Verify the initial state
        Assert.assertEquals(accountEmailDao.getEmails(accountId).size(), 0);

        // Add the first email
        final String email1 = UUID.randomUUID().toString();
        accountUserApi.addEmail(accountId, new DefaultAccountEmail(accountId, email1), callContext);
        Assert.assertEquals(accountEmailDao.getEmails(accountId).size(), 1);

        // Add a second one
        final String email2 = UUID.randomUUID().toString();
        accountUserApi.addEmail(accountId, new DefaultAccountEmail(accountId, email2), callContext);
        Assert.assertEquals(accountEmailDao.getEmails(accountId).size(), 2);

        // Remove the first second one
        accountUserApi.removeEmail(accountId, new DefaultAccountEmail(accountId, email1), callContext);
        Assert.assertEquals(accountEmailDao.getEmails(accountId).size(), 1);
    }
}
