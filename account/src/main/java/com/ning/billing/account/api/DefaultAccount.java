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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.customfield.CustomizableEntityBase;
import com.ning.billing.util.tag.DefaultTagStore;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
 
public class DefaultAccount extends CustomizableEntityBase implements Account {
	//public final static String OBJECT_TYPE = "Account";

	private final String externalKey;
	private final String email;
	private final String name;
	private final int firstNameLength;
	private final Currency currency;
	private final int billCycleDay;
	private final String paymentProviderName;
	private final DefaultTagStore tags;
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
	private final DateTime createdDate;
	private final DateTime updatedDate;

	//intended for creation
	public DefaultAccount(final AccountData data) {
		this(UUID.randomUUID(), data, null, null);
	}
	
	// Intended for migration
	public DefaultAccount(final AccountData data, DateTime createdDate, DateTime updatedDate) {
		this(UUID.randomUUID(), data, createdDate, updatedDate);
	}

	//intended for update
	public DefaultAccount(final UUID id, final AccountData data, DateTime createdDate, DateTime updatedDate) {
		this(id, data.getExternalKey(), data.getEmail(), data.getName(), data.getFirstNameLength(),
				data.getCurrency(), data.getBillCycleDay(), data.getPaymentProviderName(),
				data.getTimeZone(), data.getLocale(),
				data.getAddress1(), data.getAddress2(), data.getCompanyName(),
				data.getCity(), data.getStateOrProvince(), data.getCountry(),
				data.getPostalCode(), data.getPhone(), createdDate, updatedDate);
	}

	public DefaultAccount(final UUID id, final String externalKey, final String email, final String name, final int firstNameLength,
			final Currency currency, final int billCycleDay, final String paymentProviderName,
			final DateTimeZone timeZone, final String locale,
			final String address1, final String address2, final String companyName,
			final String city,
			final String stateOrProvince, final String country, final String postalCode, final String phone, DateTime createdDate, DateTime updatedDate) {

		super(id);
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
		this.createdDate = createdDate == null ? new DateTime(DateTimeZone.UTC) : createdDate;
		this.updatedDate = updatedDate == null ? new DateTime(DateTimeZone.UTC) : updatedDate;
		this.tags = new DefaultTagStore(id, getObjectName());
	}

	@Override
	public String getObjectName() {
		return "Account";
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

	public DefaultTagStore getTags() {
		return tags;
	}

	@Override
	public DateTime getCreatedDate() {
		return createdDate;
	}

	@Override
	public DateTime getUpdatedDate() {
		return updatedDate;
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
	public String getPhone() {
		return phone;
	}

	@Override
	public List<Tag> getTagList() {
		return tags.getEntityList();
	}

	@Override
	public boolean hasTag(String tagName) {
		return tags.containsTag(tagName);
	}

	@Override
	public void addTag(TagDefinition definition, String addedBy, DateTime dateAdded) {
		Tag tag = new DescriptiveTag(definition, addedBy, dateAdded);
		tags.add(tag) ;
	}

	@Override
	public void addTags(List<Tag> tags) {
		if (tags != null) {
			this.tags.add(tags);
		}
	}

	@Override
	public void clearTags() {
		this.tags.clear();
	}

	@Override
	public void removeTag(TagDefinition definition) {
		tags.remove(definition.getName());
	}

	@Override
	public boolean generateInvoice() {
		return tags.generateInvoice();
	}

	@Override
	public boolean processPayment() {
		return tags.processPayment();
	}

	@Override
	public String toString() {
		return "DefaultAccount [externalKey=" + externalKey + ", email=" + email + 
				", name=" + name + ", " +
				"firstNameLength=" + firstNameLength + 
				", phone=" + phone + ", " +
				"currency=" + currency + 
				", billCycleDay=" + billCycleDay + 
				", paymentProviderName=" + paymentProviderName + 
				", timezone=" + timeZone +
				", locale=" +  locale +
				", address1" + address1 +
				", address2" + address2 +
				", companyName" + companyName +
				", city" + city +
				", stateOrProvince" + stateOrProvince +
				", postalCode" + postalCode +
				", country" +
				", tags=" + tags + 
				", createdDate=" + createdDate + 
				", updatedDate=" + updatedDate + "]";
	}
}