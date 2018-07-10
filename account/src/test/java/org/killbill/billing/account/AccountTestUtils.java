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

package org.killbill.billing.account;

import java.util.Locale;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.DefaultMutableAccountData;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.catalog.api.Currency;
import org.testng.Assert;

public abstract class AccountTestUtils {

    public static void checkAccountsEqual(final AccountData retrievedAccount, final AccountData account) {
        final UUID fakeId = UUID.randomUUID();
        checkAccountsEqual(new AccountModelDao(fakeId, retrievedAccount), new AccountModelDao(fakeId, account));
    }

    public static void checkAccountsEqual(final AccountModelDao retrievedAccount, final AccountModelDao account) {
        if (retrievedAccount == null || account == null) {
            Assert.assertNull(retrievedAccount);
            Assert.assertNull(account);
            return;
        }

        // Check all fields but createdDate/updatedDate (comes from the callcontext)
        Assert.assertEquals(retrievedAccount.getId(), account.getId());
        Assert.assertEquals(retrievedAccount.getExternalKey(), account.getExternalKey());
        Assert.assertEquals(retrievedAccount.getEmail(), account.getEmail());
        Assert.assertEquals(retrievedAccount.getName(), account.getName());
        Assert.assertEquals(retrievedAccount.getFirstNameLength(), account.getFirstNameLength());
        Assert.assertEquals(retrievedAccount.getCurrency(), account.getCurrency());
        Assert.assertEquals(retrievedAccount.getBillingCycleDayLocal(), account.getBillingCycleDayLocal());
        Assert.assertEquals(retrievedAccount.getPaymentMethodId(), account.getPaymentMethodId());
        Assert.assertEquals(retrievedAccount.getTimeZone(), account.getTimeZone());
        Assert.assertEquals(retrievedAccount.getLocale(), account.getLocale());
        Assert.assertEquals(retrievedAccount.getAddress1(), account.getAddress1());
        Assert.assertEquals(retrievedAccount.getAddress2(), account.getAddress2());
        Assert.assertEquals(retrievedAccount.getCompanyName(), account.getCompanyName());
        Assert.assertEquals(retrievedAccount.getCity(), account.getCity());
        Assert.assertEquals(retrievedAccount.getStateOrProvince(), account.getStateOrProvince());
        Assert.assertEquals(retrievedAccount.getCountry(), account.getCountry());
        Assert.assertEquals(retrievedAccount.getPostalCode(), account.getPostalCode());
        Assert.assertEquals(retrievedAccount.getPhone(), account.getPhone());
        Assert.assertEquals(retrievedAccount.getMigrated(), account.getMigrated());
    }

    public static AccountModelDao createTestAccount() {
        return createTestAccount(31, UUID.randomUUID().toString().substring(0, 4));
    }

    public static AccountModelDao createTestAccount(final String phone) {
        return createTestAccount(31, phone);
    }

    public static AccountModelDao createTestAccount(final int billCycleDay) {
        return createTestAccount(billCycleDay, UUID.randomUUID().toString().substring(0, 4));
    }

    private static AccountModelDao createTestAccount(final int billCycleDayLocal, final String phone) {
        final AccountData accountData = createAccountData(billCycleDayLocal, phone);
        return new AccountModelDao(UUID.randomUUID(), accountData);
    }

    public static MutableAccountData createAccountData() {
        return createAccountData(31, UUID.randomUUID().toString().substring(0, 4));
    }

    private static MutableAccountData createAccountData(final int billCycleDayLocal, final String phone) {
        final String externalKey = UUID.randomUUID().toString();
        final String email = UUID.randomUUID().toString().substring(0, 4) + '@' + UUID.randomUUID().toString().substring(0, 4);
        final String name = UUID.randomUUID().toString();
        final String locale = Locale.GERMANY.toString();
        final DateTimeZone timeZone = DateTimeZone.forID("America/Los_Angeles");
        final int firstNameLength = name.length();
        final Currency currency = Currency.MXN;
        final UUID paymentMethodId = UUID.randomUUID();
        final String address1 = UUID.randomUUID().toString();
        final String address2 = UUID.randomUUID().toString();
        final String companyName = UUID.randomUUID().toString();
        final String city = UUID.randomUUID().toString();
        final String stateOrProvince = UUID.randomUUID().toString();
        final String country = Locale.GERMANY.getCountry();
        final String postalCode = UUID.randomUUID().toString().substring(0, 4);
        final String notes = UUID.randomUUID().toString();

        return new DefaultMutableAccountData(externalKey, email, name, firstNameLength, currency, null, false,
                                             billCycleDayLocal, paymentMethodId, null, timeZone,
                                             locale, address1, address2, companyName, city, stateOrProvince,
                                             country, postalCode, phone, notes, false);
    }
}
