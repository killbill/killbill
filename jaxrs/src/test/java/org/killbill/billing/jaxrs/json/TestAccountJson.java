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

package org.killbill.billing.jaxrs.json;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.mock.MockAccountBuilder;

public class TestAccountJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final String name = UUID.randomUUID().toString();
        final Integer length = 12;
        final String externalKey = UUID.randomUUID().toString();
        final String email = UUID.randomUUID().toString();
        final Integer billCycleDayLocal = 6;
        final Currency currency = Currency.USD;
        final UUID paymentMethodId = UUID.randomUUID();
        final DateTime referenceTime = new DateTime();
        final String timeZone = UUID.randomUUID().toString();
        final String address1 = UUID.randomUUID().toString();
        final String address2 = UUID.randomUUID().toString();
        final String postalCode = UUID.randomUUID().toString();
        final String company = UUID.randomUUID().toString();
        final String city = UUID.randomUUID().toString();
        final String state = UUID.randomUUID().toString();
        final String country = UUID.randomUUID().toString();
        final String locale = UUID.randomUUID().toString();
        final String phone = UUID.randomUUID().toString();
        final String notes = UUID.randomUUID().toString();
        final Boolean isMigrated = true;
        final Boolean isNotifiedForInvoice = false;
        final UUID parentAccountId = UUID.randomUUID();

        final AccountJson accountJson = new AccountJson(accountId, name, length, externalKey,
                                                        email, billCycleDayLocal, currency, parentAccountId, true, paymentMethodId,
                                                        referenceTime, timeZone, address1, address2, postalCode, company, city, state,
                                                        country, locale, phone, notes, isMigrated, null, null, null);
        Assert.assertEquals(accountJson.getAccountId(), accountId);
        Assert.assertEquals(accountJson.getName(), name);
        Assert.assertEquals(accountJson.getFirstNameLength(), length);
        Assert.assertEquals(accountJson.getExternalKey(), externalKey);
        Assert.assertEquals(accountJson.getEmail(), email);
        Assert.assertEquals(accountJson.getBillCycleDayLocal(), billCycleDayLocal);
        Assert.assertEquals(accountJson.getCurrency(), currency);
        Assert.assertEquals(accountJson.getPaymentMethodId(), paymentMethodId);
        Assert.assertEquals(accountJson.getTimeZone(), timeZone);
        Assert.assertEquals(accountJson.getAddress1(), address1);
        Assert.assertEquals(accountJson.getAddress2(), address2);
        Assert.assertEquals(accountJson.getPostalCode(), postalCode);
        Assert.assertEquals(accountJson.getCompany(), company);
        Assert.assertEquals(accountJson.getCity(), city);
        Assert.assertEquals(accountJson.getState(), state);
        Assert.assertEquals(accountJson.getCountry(), country);
        Assert.assertEquals(accountJson.getLocale(), locale);
        Assert.assertEquals(accountJson.getPhone(), phone);
        Assert.assertEquals(accountJson.getNotes(), notes);
        Assert.assertEquals(accountJson.isMigrated(), isMigrated);
        Assert.assertEquals(accountJson.getParentAccountId(), parentAccountId);
        Assert.assertEquals(accountJson.isPaymentDelegatedToParent(), Boolean.TRUE);

        final String asJson = mapper.writeValueAsString(accountJson);
        final AccountJson fromJson = mapper.readValue(asJson, AccountJson.class);
        Assert.assertEquals(fromJson, accountJson);
    }

    @Test(groups = "fast")
    public void testFromAccount() throws Exception {
        final MockAccountBuilder accountBuilder = new MockAccountBuilder();
        accountBuilder.address1(UUID.randomUUID().toString());
        accountBuilder.address2(UUID.randomUUID().toString());
        final int bcd = 4;
        accountBuilder.billingCycleDayLocal(bcd);
        accountBuilder.city(UUID.randomUUID().toString());
        accountBuilder.companyName(UUID.randomUUID().toString());
        accountBuilder.country(UUID.randomUUID().toString());
        accountBuilder.currency(Currency.GBP);
        accountBuilder.email(UUID.randomUUID().toString());
        accountBuilder.externalKey(UUID.randomUUID().toString());
        accountBuilder.firstNameLength(12);
        accountBuilder.locale(UUID.randomUUID().toString());
        accountBuilder.migrated(true);
        accountBuilder.name(UUID.randomUUID().toString());
        accountBuilder.paymentMethodId(UUID.randomUUID());
        accountBuilder.phone(UUID.randomUUID().toString());
        accountBuilder.postalCode(UUID.randomUUID().toString());
        accountBuilder.stateOrProvince(UUID.randomUUID().toString());
        accountBuilder.timeZone(DateTimeZone.UTC);
        accountBuilder.parentAccountId(UUID.randomUUID());
        final Account account = accountBuilder.build();

        final AccountJson accountJson = new AccountJson(account, null, null, null);
        Assert.assertEquals(accountJson.getAddress1(), account.getAddress1());
        Assert.assertEquals(accountJson.getAddress2(), account.getAddress2());
        Assert.assertEquals(accountJson.getBillCycleDayLocal(), (Integer) bcd);
        Assert.assertEquals(accountJson.getCountry(), account.getCountry());
        Assert.assertEquals(accountJson.getLocale(), account.getLocale());
        Assert.assertEquals(accountJson.getCompany(), account.getCompanyName());
        Assert.assertEquals(accountJson.getCity(), account.getCity());
        Assert.assertEquals(accountJson.getCurrency(), account.getCurrency());
        Assert.assertEquals(accountJson.getEmail(), account.getEmail());
        Assert.assertEquals(accountJson.getExternalKey(), account.getExternalKey());
        Assert.assertEquals(accountJson.getName(), account.getName());
        Assert.assertEquals(accountJson.getPaymentMethodId(), account.getPaymentMethodId());
        Assert.assertEquals(accountJson.getPhone(), account.getPhone());
        Assert.assertEquals(accountJson.isMigrated(), account.isMigrated());
        Assert.assertEquals(accountJson.getState(), account.getStateOrProvince());
        Assert.assertEquals(accountJson.getTimeZone(), account.getTimeZone().toString());
        Assert.assertEquals(accountJson.getParentAccountId(), account.getParentAccountId());
    }
}
