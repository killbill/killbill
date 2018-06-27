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

package org.killbill.billing.mock;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.util.account.AccountDateTimeUtils;

public class MockAccountBuilder {

    private final UUID id;
    private String externalKey = "";
    private String email = "";
    private String name = "";
    private int firstNameLength;
    private Currency currency = Currency.USD;
    private UUID parentAccountId;
    private boolean isPaymentDelegatedToParent = false;
    private int billingCycleDayLocal;
    private UUID paymentMethodId;
    private DateTime referenceTime = new DateTime(DateTimeZone.UTC);
    private DateTimeZone timeZone = DateTimeZone.UTC;
    private String locale = "";
    private String address1 = "";
    private String address2 = "";
    private String companyName = "";
    private String city = "";
    private String stateOrProvince = "";
    private String country = "";
    private String postalCode = "";
    private String phone = "";
    private String notes = "";
    private boolean migrated;
    private DateTime createdDate = new DateTime(DateTimeZone.UTC);
    private DateTime updatedDate = new DateTime(DateTimeZone.UTC);

    public MockAccountBuilder() {
        this(UUID.randomUUID());
    }

    public MockAccountBuilder(final UUID id) {
        this.id = id;
    }

    public MockAccountBuilder(final AccountData data) {
        this.address1(data.getAddress1());
        this.address2(data.getAddress2());
        this.billingCycleDayLocal(data.getBillCycleDayLocal());
        this.city(data.getCity());
        this.companyName(data.getCompanyName());
        this.country(data.getCountry());
        this.currency(data.getCurrency());
        this.parentAccountId(data.getParentAccountId());
        this.isPaymentDelegatedToParent(data.isPaymentDelegatedToParent());
        this.email(data.getEmail());
        this.externalKey(data.getExternalKey());
        this.firstNameLength(data.getFirstNameLength());
        this.locale(data.getLocale());
        this.migrated(data.isMigrated());
        this.name(data.getName());
        this.paymentMethodId(data.getPaymentMethodId());
        this.phone(data.getPhone());
        this.notes(data.getNotes());
        this.postalCode(data.getPostalCode());
        this.stateOrProvince(data.getStateOrProvince());
        this.referenceTime(data.getReferenceTime());
        this.timeZone(data.getTimeZone());
        if (data instanceof Account) {
            this.id = ((Account) data).getId();
            this.createdDate(((Account) data).getCreatedDate());
            this.updatedDate(((Account) data).getUpdatedDate());
        } else {
            this.id = UUID.randomUUID();
        }
    }

    public MockAccountBuilder externalKey(final String externalKey) {
        this.externalKey = externalKey;
        return this;
    }

    public MockAccountBuilder email(final String email) {
        this.email = email;
        return this;
    }

    public MockAccountBuilder name(final String name) {
        this.name = name;
        return this;
    }

    public MockAccountBuilder firstNameLength(final int firstNameLength) {
        this.firstNameLength = firstNameLength;
        return this;
    }

    public MockAccountBuilder billingCycleDayLocal(final int billingCycleDayLocal) {
        this.billingCycleDayLocal = billingCycleDayLocal;
        return this;
    }

    public MockAccountBuilder currency(final Currency currency) {
        this.currency = currency;
        return this;
    }

    public MockAccountBuilder parentAccountId(final UUID parentAccountId) {
        this.parentAccountId = parentAccountId;
        return this;
    }

    public MockAccountBuilder isPaymentDelegatedToParent(final boolean isPaymentDelegatedToParent) {
        this.isPaymentDelegatedToParent = isPaymentDelegatedToParent;
        return this;
    }

    public MockAccountBuilder paymentMethodId(final UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
        return this;
    }

    public MockAccountBuilder referenceTime(final DateTime referenceTime) {
        this.referenceTime = referenceTime;
        return this;
    }

    public MockAccountBuilder timeZone(final DateTimeZone timeZone) {
        this.timeZone = timeZone;
        return this;
    }

    public MockAccountBuilder locale(final String locale) {
        this.locale = locale;
        return this;
    }

    public MockAccountBuilder address1(final String address1) {
        this.address1 = address1;
        return this;
    }

    public MockAccountBuilder address2(final String address2) {
        this.address2 = address2;
        return this;
    }

    public MockAccountBuilder companyName(final String companyName) {
        this.companyName = companyName;
        return this;
    }

    public MockAccountBuilder city(final String city) {
        this.city = city;
        return this;
    }

    public MockAccountBuilder stateOrProvince(final String stateOrProvince) {
        this.stateOrProvince = stateOrProvince;
        return this;
    }

    public MockAccountBuilder postalCode(final String postalCode) {
        this.postalCode = postalCode;
        return this;
    }

    public MockAccountBuilder country(final String country) {
        this.country = country;
        return this;
    }

    public MockAccountBuilder phone(final String phone) {
        this.phone = phone;
        return this;
    }

    public MockAccountBuilder notes(final String notes) {
        this.notes = notes;
        return this;
    }

    public MockAccountBuilder migrated(final boolean migrated) {
        this.migrated = migrated;
        return this;
    }

    public MockAccountBuilder createdDate(final DateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public MockAccountBuilder updatedDate(final DateTime updatedDate) {
        this.updatedDate = updatedDate;
        return this;
    }

    public Account build() {
        return new Account() {
            @Override
            public DateTime getCreatedDate() {
                return createdDate;
            }

            @Override
            public DateTime getUpdatedDate() {
                return updatedDate;
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
            public Integer getFirstNameLength() {
                return firstNameLength;
            }

            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public Integer getBillCycleDayLocal() {
                return billingCycleDayLocal;
            }

            @Override
            public Currency getCurrency() {
                return currency;
            }

            @Override
            public UUID getPaymentMethodId() {
                return paymentMethodId;
            }

            @Override
            public DateTimeZone getTimeZone() {
                return timeZone;
            }

            @Override
            public DateTimeZone getFixedOffsetTimeZone() {
                return AccountDateTimeUtils.getFixedOffsetTimeZone(this);
            }

            @Override
            public DateTime getReferenceTime() {
                return referenceTime;
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
            public String getNotes() {
                return notes;
            }

            @Override
            public Boolean isMigrated() {
                return migrated;
            }

            @Override
            public UUID getParentAccountId() {
                return parentAccountId;
            }

            @Override
            public Boolean isPaymentDelegatedToParent() {
                return isPaymentDelegatedToParent;
            }

            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public MutableAccountData toMutableAccountData() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Account mergeWithDelegate(final Account delegate) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
