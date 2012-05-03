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

package com.ning.billing.account.api;

import java.util.List;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.entity.ExtendedEntityBase;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.junction.api.BlockingState;

import javax.annotation.Nullable;

public class DefaultAccount extends ExtendedEntityBase implements Account {
    private final String externalKey;
	private final String email;
	private final String name;
	private final int firstNameLength;
	private final Currency currency;
	private final int billCycleDay;
	private final String paymentProviderName;
	private final DateTimeZone timeZone;
	private final String locale;
	private final String address1;
	private final String address2;
	private final String companyName;
	private final String city;
	private final String stateOrProvince;
	private final String country;
	private final String postalCode;
	private final String phone;
    private final String updatedBy;
    private final DateTime updatedDate;
    
    
	//intended for creation and migration
	public DefaultAccount(final String createdBy, final DateTime createdDate,
                          final String updatedBy, final DateTime updatedDate,
                          final AccountData data) {
		this(UUID.randomUUID(), createdBy, createdDate, updatedBy, updatedDate, data);
	}

    public DefaultAccount(final AccountData data) {
		this(UUID.randomUUID(), null, null, null, null, data);
	}

    public DefaultAccount(final UUID id, final AccountData data) {
		this(id, null, null, null, null, data);
	}

	/**
	 * This call is used to update an existing account
	 *  
	 * @param id UUID id of the existing account to update
	 * @param data AccountData new data for the existing account
	 */
	public DefaultAccount(final UUID id, @Nullable final String createdBy, @Nullable final DateTime createdDate,
                          @Nullable final String updatedBy, @Nullable final DateTime updatedDate,
                          final AccountData data) {
		this(id, data.getExternalKey(), data.getEmail(), data.getName(), data.getFirstNameLength(),
				data.getCurrency(), data.getBillCycleDay(), data.getPaymentProviderName(),
				data.getTimeZone(), data.getLocale(),
				data.getAddress1(), data.getAddress2(), data.getCompanyName(),
				data.getCity(), data.getStateOrProvince(), data.getCountry(),
				data.getPostalCode(), data.getPhone(), createdBy, createdDate,
                updatedBy, updatedDate);
	}

	/**
	 * This call is used for testing and update from an existing account
	 * @param id
	 * @param externalKey
	 * @param email
	 * @param name
	 * @param firstNameLength
	 * @param currency
	 * @param billCycleDay
	 * @param paymentProviderName
	 * @param timeZone
	 * @param locale
	 * @param address1
	 * @param address2
	 * @param companyName
	 * @param city
	 * @param stateOrProvince
	 * @param country
	 * @param postalCode
	 * @param phone
	 * @param createdDate
	 * @param updatedDate
	 */
	public DefaultAccount(final UUID id, final String externalKey, final String email,
                          final String name, final int firstNameLength,
                          final Currency currency, final int billCycleDay, final String paymentProviderName,
                          final DateTimeZone timeZone, final String locale,
                          final String address1, final String address2, final String companyName,
                          final String city, final String stateOrProvince, final String country,
                          final String postalCode, final String phone,
                          final String createdBy, final DateTime createdDate,
                          final String updatedBy, final DateTime updatedDate) {

		super(id, createdBy, createdDate);
		this.externalKey = externalKey;
		this.email = email;
		this.name = name;
		this.firstNameLength = firstNameLength;
		this.currency = currency;
		this.billCycleDay = billCycleDay;
		this.paymentProviderName = paymentProviderName;
		this.timeZone = timeZone;
		this.locale = locale;
		this.address1 = address1;
		this.address2 = address2;
		this.companyName = companyName;
		this.city = city;
		this.stateOrProvince = stateOrProvince;
		this.postalCode = postalCode;
		this.country = country;
		this.phone = phone;
        this.updatedBy = updatedBy;
        this.updatedDate = updatedDate;
	}

    @Override
    public void saveFieldValue(String fieldName, String fieldValue, CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveFields(List<CustomField> fields, CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearPersistedFields(CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
	public String getObjectName() {
		return ObjectType;
	}

	@Override
	public String getExternalKey() {
		return externalKey;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getEmail() {
		return email;
	}

	@Override
	public int getFirstNameLength() {
		return firstNameLength;
	}

	@Override
	public Currency getCurrency() {
		return currency;
	}

	@Override
	public int getBillCycleDay() {
		return billCycleDay;
	}

	@Override
	public String getPaymentProviderName() {
		return paymentProviderName;
	}

	@Override
	public DateTimeZone getTimeZone() {
		return timeZone;
	}

	@Override
	public String getLocale() {
		return locale;
	}

	@Override
	public String getAddress1() {
		return address1;
	}

	@Override
	public String getAddress2() {
		return address2;
	}

	@Override
	public String getCompanyName() {
		return companyName;
	}

	@Override
	public String getCity() {
		return city;
	}

	@Override
	public String getStateOrProvince() {
		return stateOrProvince;
	}

	@Override
	public String getPostalCode() {
		return postalCode;
	}

	@Override
	public String getCountry() {
		return country;
	}

    @Override
    public String getUpdatedBy() {
        return updatedBy;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    @Override
	public String getPhone() {
		return phone;
	}

    @Override
	public MutableAccountData toMutableAccountData() {
	    return new DefaultMutableAccountData(this);
	}
    
	@Override
	public String toString() {
		return "DefaultAccount [externalKey=" + externalKey +
                ", email=" + email +
				", name=" + name +
				", firstNameLength=" + firstNameLength +
				", phone=" + phone +
				", currency=" + currency +
				", billCycleDay=" + billCycleDay +
				", paymentProviderName=" + paymentProviderName +
				", timezone=" + timeZone +
				", locale=" +  locale +
				", address1=" + address1 +
				", address2=" + address2 +
				", companyName=" + companyName +
				", city=" + city +
				", stateOrProvince=" + stateOrProvince +
				", postalCode=" + postalCode +
				", country=" + country +
				", tags=" + tags +
				", fields=" + fields +
				"]";
	}

	@Override
	public BlockingState getBlockingState() {
	    throw new UnsupportedOperationException();
	}
}