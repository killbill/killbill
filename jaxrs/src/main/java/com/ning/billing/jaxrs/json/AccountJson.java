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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.catalog.api.Currency;

public class AccountJson extends AccountJsonSimple {

    // STEPH Missing city, locale, postalCode from https://home.ninginc.com:8443/display/REVINFRA/Killbill+1.0+APIs

    private final String name;
    
    private final Integer length;
        
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
        super(account.getId().toString(), account.getExternalKey());
        this.name = account.getName();
        this.length = account.getFirstNameLength();
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
            public boolean isMigrated() {
                return false;
            }

            @Override
            public boolean isNotifiedForInvoices() {
                return false;
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

    // Seems like Jackson (JacksonJsonProvider.readFrom(Class<Object>, Type, Annotation[], MediaType, MultivaluedMap<String,String>, InputStream) line: 443)
    // needs us to define a default CTOR to instanciate the class first.
    public AccountJson() {
        super();
        this.name = null;
        this.length = null;
        this.email = null;
        this.billCycleDay = null;
        this.currency = null;
        this.paymentProvider = null;
        this.timeZone = null;
        this.address1 = null;
        this.address2 = null;
        this.company = null;
        this.state = null;
        this.country = null;
        this.phone = null;
    }

    @JsonCreator
    public AccountJson(@JsonProperty("accountId") String accountId,
            @JsonProperty("name") String name,
            @JsonProperty("firstNameLength") Integer length,
            @JsonProperty("externalKey") String externalKey,
            @JsonProperty("email") String email,
            @JsonProperty("billingDay") Integer billCycleDay,
            @JsonProperty("currency") String currency,
            @JsonProperty("paymentProvider") String paymentProvider,
            @JsonProperty("timezone") String timeZone,
            @JsonProperty("address1") String address1,
            @JsonProperty("address2") String address2,
            @JsonProperty("company") String company,
            @JsonProperty("state") String state,
            @JsonProperty("country") String country,
            @JsonProperty("phone") String phone) {
        super(accountId, externalKey);
        this.name = name;
        this.length = length;
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

    public String getName() {
        return name;
    }

    public Integer getLength() {
        return length;
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((accountId == null) ? 0 : accountId.hashCode());
		result = prime * result
				+ ((address1 == null) ? 0 : address1.hashCode());
		result = prime * result
				+ ((address2 == null) ? 0 : address2.hashCode());
		result = prime * result
				+ ((billCycleDay == null) ? 0 : billCycleDay.hashCode());
		result = prime * result + ((company == null) ? 0 : company.hashCode());
		result = prime * result + ((country == null) ? 0 : country.hashCode());
		result = prime * result
				+ ((currency == null) ? 0 : currency.hashCode());
		result = prime * result + ((email == null) ? 0 : email.hashCode());
		result = prime * result
				+ ((externalKey == null) ? 0 : externalKey.hashCode());
		result = prime * result + ((length == null) ? 0 : length.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result
				+ ((paymentProvider == null) ? 0 : paymentProvider.hashCode());
		result = prime * result + ((phone == null) ? 0 : phone.hashCode());
		result = prime * result + ((state == null) ? 0 : state.hashCode());
		result = prime * result
				+ ((timeZone == null) ? 0 : timeZone.hashCode());
		return result;
	}

	// Used to check POST versus GET
	public boolean equalsNoId(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AccountJson other = (AccountJson) obj;
		if (address1 == null) {
			if (other.address1 != null)
				return false;
		} else if (!address1.equals(other.address1))
			return false;
		if (address2 == null) {
			if (other.address2 != null)
				return false;
		} else if (!address2.equals(other.address2))
			return false;
		if (billCycleDay == null) {
			if (other.billCycleDay != null)
				return false;
		} else if (!billCycleDay.equals(other.billCycleDay))
			return false;
		if (company == null) {
			if (other.company != null)
				return false;
		} else if (!company.equals(other.company))
			return false;
		if (country == null) {
			if (other.country != null)
				return false;
		} else if (!country.equals(other.country))
			return false;
		if (currency == null) {
			if (other.currency != null)
				return false;
		} else if (!currency.equals(other.currency))
			return false;
		if (email == null) {
			if (other.email != null)
				return false;
		} else if (!email.equals(other.email))
			return false;
		if (externalKey == null) {
			if (other.externalKey != null)
				return false;
		} else if (!externalKey.equals(other.externalKey))
			return false;
		if (length == null) {
			if (other.length != null)
				return false;
		} else if (!length.equals(other.length))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (paymentProvider == null) {
			if (other.paymentProvider != null)
				return false;
		} else if (!paymentProvider.equals(other.paymentProvider))
			return false;
		if (phone == null) {
			if (other.phone != null)
				return false;
		} else if (!phone.equals(other.phone))
			return false;
		if (state == null) {
			if (other.state != null)
				return false;
		} else if (!state.equals(other.state))
			return false;
		if (timeZone == null) {
			if (other.timeZone != null)
				return false;
		} else if (!timeZone.equals(other.timeZone))
			return false;
		return true;
	}

	@Override
	public boolean equals(Object obj) {
		if (equalsNoId(obj) == false) {
			return false;
		} else {
			AccountJson other = (AccountJson) obj;
			if (accountId == null) {
				if (other.accountId != null)
					return false;
			} else if (!accountId.equals(other.accountId))
				return false;
		}
		return true;
	}
 }
