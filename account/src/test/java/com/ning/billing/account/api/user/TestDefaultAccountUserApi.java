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

import com.ning.billing.account.AccountTestSuite;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.DefaultAccountEmail;
import com.ning.billing.account.api.DefaultBillCycleDay;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.AccountModelDao;
import com.ning.billing.account.dao.MockAccountDao;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcsapi.bus.InternalBus;

public class TestDefaultAccountUserApi extends AccountTestSuite {

    private final CallContextFactory factory = Mockito.mock(CallContextFactory.class);
    private final InternalCallContextFactory internalFactory = Mockito.mock(InternalCallContextFactory.class);
    private final CallContext callContext = Mockito.mock(CallContext.class);
    private final InternalTenantContext tenantContext = Mockito.mock(InternalTenantContext.class);

    private AccountDao accountDao;
    private DefaultAccountUserApi accountUserApi;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        accountDao = new MockAccountDao(Mockito.mock(InternalBus.class));
        accountUserApi = new DefaultAccountUserApi(factory, internalFactory, accountDao);
    }

    @Test(groups = "fast")
    public void testCreateAccount() throws Exception {
        final UUID id = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final String email = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString();
        final Integer firstNameLength = Integer.MAX_VALUE;
        final Currency currency = Currency.BRL;
        final BillCycleDay billCycleDay = new DefaultBillCycleDay(Integer.MIN_VALUE, Integer.MAX_VALUE);
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
        final Boolean isMigrated = true;
        final Boolean isNotifiedForInvoices = false;
        final AccountData data = new DefaultAccount(id, externalKey, email, name, firstNameLength, currency, billCycleDay,
                                                    paymentMethodId, timeZone, locale, address1, address2, companyName,
                                                    city, stateOrProvince, country, postalCode, phone, isMigrated, isNotifiedForInvoices);

        accountUserApi.createAccount(data, callContext);

        final AccountModelDao account = accountDao.getAccountByKey(externalKey, tenantContext);
        Assert.assertEquals(account.getExternalKey(), externalKey);
        Assert.assertEquals(account.getEmail(), email);
        Assert.assertEquals(account.getName(), name);
        Assert.assertEquals(account.getFirstNameLength(), firstNameLength);
        Assert.assertEquals(account.getCurrency(), currency);
        Assert.assertEquals(account.getBillingCycleDayLocal(), billCycleDay.getDayOfMonthLocal());
        Assert.assertEquals(account.getBillingCycleDayUtc(), billCycleDay.getDayOfMonthUTC());
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
        Assert.assertEquals(account.getMigrated(), isMigrated);
        Assert.assertEquals(account.getIsNotifiedForInvoices(), isNotifiedForInvoices);
    }

    @Test(groups = "fast")
    public void testAddEmail() throws Exception {
        final UUID accountId = UUID.randomUUID();

        // Verify the initial state
        Assert.assertEquals(accountDao.getEmailsByAccountId(accountId, tenantContext).size(), 0);

        // Add the first email
        final String emailAddress1 = UUID.randomUUID().toString();
        final AccountEmail email1 = new DefaultAccountEmail(accountId, emailAddress1);
        accountUserApi.addEmail(accountId, email1, callContext);
        Assert.assertEquals(accountDao.getEmailsByAccountId(accountId, tenantContext).size(), 1);

        // Add a second one
        final String emailAddress2 = UUID.randomUUID().toString();
        final AccountEmail email2 = new DefaultAccountEmail(accountId, emailAddress2);
        accountUserApi.addEmail(accountId, email2, callContext);
        Assert.assertEquals(accountDao.getEmailsByAccountId(accountId, tenantContext).size(), 2);

        // Remove the first second one
        accountUserApi.removeEmail(accountId, email1, callContext);
        Assert.assertEquals(accountDao.getEmailsByAccountId(accountId, tenantContext).size(), 1);
    }
}
