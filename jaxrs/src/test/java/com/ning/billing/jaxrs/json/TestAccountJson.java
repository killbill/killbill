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

package com.ning.billing.jaxrs.json;

import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.jaxrs.JaxrsTestSuite;
import com.ning.billing.mock.MockAccountBuilder;

public class TestAccountJson extends JaxrsTestSuite {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String accountId = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString();
        final Integer length = 12;
        final String externalKey = UUID.randomUUID().toString();
        final String email = UUID.randomUUID().toString();
        final Integer billCycleDay = 6;
        final String currency = UUID.randomUUID().toString();
        final String paymentMethodId = UUID.randomUUID().toString();
        final String timeZone = UUID.randomUUID().toString();
        final String address1 = UUID.randomUUID().toString();
        final String address2 = UUID.randomUUID().toString();
        final String company = UUID.randomUUID().toString();
        final String state = UUID.randomUUID().toString();
        final String country = UUID.randomUUID().toString();
        final String phone = UUID.randomUUID().toString();

        final AccountJson accountJson = new AccountJson(accountId, name, length, externalKey,
                                                        email, billCycleDay, currency, paymentMethodId,
                                                        timeZone, address1, address2, company, state,
                                                        country, phone);
        Assert.assertEquals(accountJson.getAccountId(), accountId);
        Assert.assertEquals(accountJson.getName(), name);
        Assert.assertEquals(accountJson.getLength(), length);
        Assert.assertEquals(accountJson.getExternalKey(), externalKey);
        Assert.assertEquals(accountJson.getEmail(), email);
        Assert.assertEquals(accountJson.getBillCycleDay(), billCycleDay);
        Assert.assertEquals(accountJson.getCurrency(), currency);
        Assert.assertEquals(accountJson.getPaymentMethodId(), paymentMethodId);
        Assert.assertEquals(accountJson.getTimeZone(), timeZone);
        Assert.assertEquals(accountJson.getAddress1(), address1);
        Assert.assertEquals(accountJson.getAddress2(), address2);
        Assert.assertEquals(accountJson.getCompany(), company);
        Assert.assertEquals(accountJson.getState(), state);
        Assert.assertEquals(accountJson.getCountry(), country);
        Assert.assertEquals(accountJson.getPhone(), phone);

        final String asJson = mapper.writeValueAsString(accountJson);
        Assert.assertEquals(asJson, "{\"accountId\":\"" + accountJson.getAccountId() + "\",\"name\":\"" + accountJson.getName() + "\"," +
                "\"externalKey\":\"" + accountJson.getExternalKey() + "\",\"email\":\"" + accountJson.getEmail() + "\"," +
                "\"currency\":\"" + accountJson.getCurrency() + "\",\"paymentMethodId\":\"" + accountJson.getPaymentMethodId() + "\"," +
                "\"address1\":\"" + accountJson.getAddress1() + "\",\"address2\":\"" + accountJson.getAddress2() + "\"," +
                "\"company\":\"" + accountJson.getCompany() + "\",\"state\":\"" + accountJson.getState() + "\"," +
                "\"country\":\"" + accountJson.getCountry() + "\",\"phone\":\"" + accountJson.getPhone() + "\"," +
                "\"length\":" + accountJson.getLength() + ",\"billCycleDay\":" + accountJson.getBillCycleDay() + "," +
                "\"timeZone\":\"" + accountJson.getTimeZone() + "\"}");

        final AccountJson fromJson = mapper.readValue(asJson, AccountJson.class);
        Assert.assertEquals(fromJson, accountJson);
    }

    @Test(groups = "fast")
    public void testFromAccount() throws Exception {
        final MockAccountBuilder accountBuilder = new MockAccountBuilder();
        accountBuilder.address1(UUID.randomUUID().toString());
        accountBuilder.address2(UUID.randomUUID().toString());
        accountBuilder.billingCycleDay(4);
        accountBuilder.city(UUID.randomUUID().toString());
        accountBuilder.companyName(UUID.randomUUID().toString());
        accountBuilder.country(UUID.randomUUID().toString());
        accountBuilder.currency(Currency.GBP);
        accountBuilder.email(UUID.randomUUID().toString());
        accountBuilder.externalKey(UUID.randomUUID().toString());
        accountBuilder.firstNameLength(12);
        accountBuilder.isNotifiedForInvoices(true);
        accountBuilder.locale(UUID.randomUUID().toString());
        accountBuilder.migrated(true);
        accountBuilder.name(UUID.randomUUID().toString());
        accountBuilder.paymentMethodId(UUID.randomUUID());
        accountBuilder.phone(UUID.randomUUID().toString());
        accountBuilder.postalCode(UUID.randomUUID().toString());
        accountBuilder.stateOrProvince(UUID.randomUUID().toString());
        accountBuilder.timeZone(DateTimeZone.UTC);
        final Account account = accountBuilder.build();

        final AccountJson accountJson = new AccountJson(account);
        Assert.assertEquals(accountJson.getAddress1(), account.getAddress1());
        Assert.assertEquals(accountJson.getAddress2(), account.getAddress2());
        Assert.assertEquals((int) accountJson.getBillCycleDay(), (int) account.getBillCycleDay());
        Assert.assertEquals(accountJson.getCountry(), account.getCountry());
        Assert.assertEquals(accountJson.getCompany(), account.getCompanyName());
        Assert.assertEquals(accountJson.getCurrency(), account.getCurrency().toString());
        Assert.assertEquals(accountJson.getEmail(), account.getEmail());
        Assert.assertEquals(accountJson.getExternalKey(), account.getExternalKey());
        Assert.assertEquals(accountJson.getName(), account.getName());
        Assert.assertEquals(accountJson.getPaymentMethodId(), account.getPaymentMethodId().toString());
        Assert.assertEquals(accountJson.getPhone(), account.getPhone());
        Assert.assertEquals(accountJson.getState(), account.getStateOrProvince());
        Assert.assertEquals(accountJson.getTimeZone(), account.getTimeZone().toString());
    }
}
