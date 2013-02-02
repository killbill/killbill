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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;

import com.ning.billing.account.api.Account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountJsonWithBalanceAndCBA extends AccountJsonWithBalance {

    private final BigDecimal accountCBA;

    public AccountJsonWithBalanceAndCBA(final Account account, final BigDecimal accountBalance, final BigDecimal accountCBA) {
        super(account, accountBalance);
        this.accountCBA = accountCBA;
    }

    @JsonCreator
    public AccountJsonWithBalanceAndCBA(@JsonProperty("accountId") final String accountId,
                                        @JsonProperty("name") final String name,
                                        @JsonProperty("firstNameLength") final Integer length,
                                        @JsonProperty("externalKey") final String externalKey,
                                        @JsonProperty("email") final String email,
                                        @JsonProperty("billCycleDay") final BillCycleDayJson billCycleDay,
                                        @JsonProperty("currency") final String currency,
                                        @JsonProperty("paymentMethodId") final String paymentMethodId,
                                        @JsonProperty("timezone") final String timeZone,
                                        @JsonProperty("address1") final String address1,
                                        @JsonProperty("address2") final String address2,
                                        @JsonProperty("postalCode") final String postalCode,
                                        @JsonProperty("company") final String company,
                                        @JsonProperty("city") final String city,
                                        @JsonProperty("state") final String state,
                                        @JsonProperty("country") final String country,
                                        @JsonProperty("locale") final String locale,
                                        @JsonProperty("phone") final String phone,
                                        @JsonProperty("isMigrated") final Boolean isMigrated,
                                        @JsonProperty("isNotifiedForInvoices") final Boolean isNotifiedForInvoices,
                                        @JsonProperty("accountBalance") final BigDecimal accountBalance,
                                        @JsonProperty("accountCBA") final BigDecimal accountCBA) {
        super(accountId, name, length, externalKey, email, billCycleDay, currency, paymentMethodId, timeZone, address1,
              address2, postalCode, company, city, state, country, locale, phone, isMigrated, isNotifiedForInvoices, accountBalance);
        this.accountCBA = accountCBA;
    }

    public BigDecimal getAccountCBA() {
        return accountCBA;
    }
}
