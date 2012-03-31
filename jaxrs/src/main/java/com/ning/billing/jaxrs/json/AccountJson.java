/*
 * Copyright 2010-2011 Ning, Inc.
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

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.catalog.api.Currency;

public class AccountJson {

    // Missing city, locale, postalCode from https://home.ninginc.com:8443/display/REVINFRA/Killbill+1.0+APIs

    private final String acountId;
    private final String name;
    private final Integer length;
    private final String externalKey;
    private final String email;
    private final Integer billCycleDay;
    private final String currency;
    private final String paymentProvider;
    private final String timeZone;
    private final String address1;
    private final String address2;
    private final String company;
    private final String state;
    private final String country;
    private final String phone;


    public AccountJson(Account account) {
        this.acountId = account.getId().toString();
        this.name = account.getName();
        this.length = account.getFirstNameLength();
        this.externalKey = account.getExternalKey();
        this.email = account.getEmail();
        this.billCycleDay = account.getBillCycleDay();
        this.currency = account.getCurrency().toString();
        this.paymentProvider = account.getPaymentProviderName();
        this.timeZone = account.getTimeZone().toString();
        this.address1 = account.getAddress1();
        this.address2 = account.getAddress2();
        this.company = account.getCompanyName();
        this.state = account.getStateOrProvince();
        this.country = account.getCountry();
        this.phone = account.getPhone();
    }

    public AccountData toAccountData() {
        return new AccountData() {
            @Override
            public DateTimeZone getTimeZone() {
                return (timeZone != null) ? DateTimeZone.forID(timeZone) : null;
            }
            @Override
            public String getStateOrProvince() {
                return state;
            }
            @Override
            public String getPostalCode() {
                return null;
            }
            @Override
            public String getPhone() {
                return phone;
            }
            @Override
            public String getPaymentProviderName() {
                return paymentProvider;
            }
            @Override
            public String getName() {
                return name;
            }
            @Override
            public String getLocale() {
                return null;
            }
            @Override
            public int getFirstNameLength() {
                return length;
            }
            @Override
            public String getExternalKey() {
                return externalKey;
            }
            @Override
            public String getEmail() {
                return email;
            }
            @Override
            public Currency getCurrency() {
                Currency result =  (currency != null) ? Currency.valueOf(currency) : Currency.USD;
                return result;
            }
            @Override
            public String getCountry() {
                return country;
            }
            @Override
            public String getCompanyName() {
                return company;
            }
            @Override
            public String getCity() {
                return null;
            }
            @Override
            public int getBillCycleDay() {
                return billCycleDay;
            }
            @Override
            public String getAddress2() {
                return address2;
            }
            @Override
            public String getAddress1() {
                return address1;
            }
        };
    }


    @JsonCreator
    public AccountJson(@JsonProperty("account_id") String acountId,
            @JsonProperty("name") String name,
            @JsonProperty("first_name_length") Integer length,
            @JsonProperty("external_key") String externalKey,
            @JsonProperty("email") String email,
            @JsonProperty("billing_day") Integer billCycleDay,
            @JsonProperty("currency") String currency,
            @JsonProperty("payment_provider") String paymentProvider,
            @JsonProperty("timezone") String timeZone,
            @JsonProperty("address1") String address1,
            @JsonProperty("address2") String address2,
            @JsonProperty("company") String company,
            @JsonProperty("state") String state,
            @JsonProperty("country") String country,
            @JsonProperty("phone") String phone) {
        super();
        this.acountId = acountId;
        this.name = name;
        this.length = length;
        this.externalKey = externalKey;
        this.email = email;
        this.billCycleDay = billCycleDay;
        this.currency = currency;
        this.paymentProvider = paymentProvider;
        this.timeZone = timeZone;
        this.address1 = address1;
        this.address2 = address2;
        this.company = company;
        this.state = state;
        this.country = country;
        this.phone = phone;
    }

    public String getAcountId() {
        return acountId;
    }

    public String getName() {
        return name;
    }

    public Integer getLength() {
        return length;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public String getEmail() {
        return email;
    }

    public Integer getBillCycleDay() {
        return billCycleDay;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public String getCompany() {
        return company;
    }

    public String getState() {
        return state;
    }

    public String getCountry() {
        return country;
    }

    public String getPhone() {
        return phone;
    }
 }
