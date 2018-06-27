/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.account.api.user;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.AccountCreationInternalEvent;
import org.killbill.billing.events.BusEventBase;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

public class DefaultAccountCreationEvent extends BusEventBase implements AccountCreationInternalEvent {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = ISODateTimeFormat.dateTimeParser();

    private final UUID id;
    private final AccountData data;

    @JsonCreator
    public DefaultAccountCreationEvent(@JsonProperty("data") final DefaultAccountData data,
                                       @JsonProperty("id") final UUID id,
                                       @JsonProperty("searchKey1") final Long searchKey1,
                                       @JsonProperty("searchKey2") final Long searchKey2,
                                       @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.id = id;
        this.data = data;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.ACCOUNT_CREATE;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public AccountData getData() {
        return data;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((data == null) ? 0 : data.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DefaultAccountCreationEvent other = (DefaultAccountCreationEvent) obj;
        if (data == null) {
            if (other.data != null) {
                return false;
            }
        } else if (!data.equals(other.data)) {
            return false;
        }
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
    }

    public static class DefaultAccountData implements AccountData {

        private final String externalKey;
        private final String name;
        private final Integer firstNameLength;
        private final String email;
        private final Integer billCycleDayLocal;
        private final String currency;
        private final UUID parentAccountId;
        private final Boolean isPaymentDelegatedToParent;
        private final UUID paymentMethodId;
        private final String referenceTime;
        private final String timeZone;
        private final String locale;
        private final String address1;
        private final String address2;
        private final String companyName;
        private final String city;
        private final String stateOrProvince;
        private final String postalCode;
        private final String country;
        private final String phone;
        private final String notes;
        private final Boolean isMigrated;

        public DefaultAccountData(final AccountModelDao d) {
            this(d.getExternalKey(),
                 d.getName(),
                 d.getFirstNameLength(),
                 d.getEmail(),
                 d.getBillingCycleDayLocal(),
                 d.getCurrency() != null ? d.getCurrency().name() : null,
                 d.getParentAccountId(),
                 d.getIsPaymentDelegatedToParent(),
                 d.getPaymentMethodId(),
                 d.getReferenceTime() != null ? d.getReferenceTime().toString() : null,
                 d.getTimeZone() != null ? d.getTimeZone().getID() : null,
                 d.getLocale(),
                 d.getAddress1(),
                 d.getAddress2(),
                 d.getCompanyName(),
                 d.getCity(),
                 d.getStateOrProvince(),
                 d.getPostalCode(),
                 d.getCountry(),
                 d.getPhone(),
                 d.getNotes(),
                 d.getMigrated());
        }

        @JsonCreator
        public DefaultAccountData(@JsonProperty("externalKey") final String externalKey,
                                  @JsonProperty("name") final String name,
                                  @JsonProperty("firstNameLength") final Integer firstNameLength,
                                  @JsonProperty("email") final String email,
                                  @JsonProperty("billCycleDayLocal") final Integer billCycleDayLocal,
                                  @JsonProperty("currency") final String currency,
                                  @JsonProperty("parentAccountId") final UUID parentAccountId,
                                  @JsonProperty("isPaymentDelegatedToParent") final Boolean isPaymentDelegatedToParent,
                                  @JsonProperty("paymentMethodId") final UUID paymentMethodId,
                                  @JsonProperty("referenceTime") final String referenceTime,
                                  @JsonProperty("timeZone") final String timeZone,
                                  @JsonProperty("locale") final String locale,
                                  @JsonProperty("address1") final String address1,
                                  @JsonProperty("address2") final String address2,
                                  @JsonProperty("companyName") final String companyName,
                                  @JsonProperty("city") final String city,
                                  @JsonProperty("stateOrProvince") final String stateOrProvince,
                                  @JsonProperty("postalCode") final String postalCode,
                                  @JsonProperty("country") final String country,
                                  @JsonProperty("phone") final String phone,
                                  @JsonProperty("notes") final String notes,
                                  @JsonProperty("isMigrated") final Boolean isMigrated) {
            this.externalKey = externalKey;
            this.name = name;
            this.firstNameLength = firstNameLength;
            this.email = email;
            this.billCycleDayLocal = billCycleDayLocal;
            this.currency = currency;
            this.parentAccountId = parentAccountId;
            this.isPaymentDelegatedToParent = isPaymentDelegatedToParent;
            this.paymentMethodId = paymentMethodId;
            this.referenceTime = referenceTime;
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
            this.notes = notes;
            this.isMigrated = isMigrated;
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
            return billCycleDayLocal;
        }

        @Override
        public Currency getCurrency() {
            if (Strings.emptyToNull(currency) == null) {
                return null;
            } else {
                return Currency.valueOf(currency);
            }
        }

        @Override
        public UUID getParentAccountId() {
            return parentAccountId;
        }

        @Override
        @JsonIgnore
        public Boolean isPaymentDelegatedToParent() {
            return isPaymentDelegatedToParent;
        }

        @JsonIgnore
        @Override
        public DateTimeZone getTimeZone() {
            if (Strings.emptyToNull(timeZone) == null) {
                return null;
            } else {
                return DateTimeZone.forID(timeZone);
            }
        }

        @JsonProperty("timeZone")
        public String getTimeZoneString() {
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
        public String getNotes() {
            return notes;
        }

        @Override
        public UUID getPaymentMethodId() {
            return paymentMethodId;
        }

        @Override
        public DateTime getReferenceTime() {
            return DATE_TIME_FORMATTER.parseDateTime(referenceTime);
        }

        @Override
        @JsonIgnore
        public Boolean isMigrated() {
            return isMigrated;
        }

        // These getters are for Jackson serialization only

        public Boolean getIsMigrated() {
            return isMigrated;
        }

        public Boolean getIsPaymentDelegatedToParent() {
            return isPaymentDelegatedToParent;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final DefaultAccountData that = (DefaultAccountData) o;

            if (billCycleDayLocal != null ? !billCycleDayLocal.equals(that.billCycleDayLocal) : that.billCycleDayLocal != null) {
                return false;
            }
            if (isMigrated != null ? !isMigrated.equals(that.isMigrated) : that.isMigrated != null) {
                return false;
            }
            if (address1 != null ? !address1.equals(that.address1) : that.address1 != null) {
                return false;
            }
            if (address2 != null ? !address2.equals(that.address2) : that.address2 != null) {
                return false;
            }
            if (city != null ? !city.equals(that.city) : that.city != null) {
                return false;
            }
            if (companyName != null ? !companyName.equals(that.companyName) : that.companyName != null) {
                return false;
            }
            if (country != null ? !country.equals(that.country) : that.country != null) {
                return false;
            }
            if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
                return false;
            }
            if (parentAccountId != null ? !parentAccountId.equals(that.parentAccountId) : that.parentAccountId != null) {
                return false;
            }
            if (isPaymentDelegatedToParent != null ? !isPaymentDelegatedToParent.equals(that.isPaymentDelegatedToParent) : that.isPaymentDelegatedToParent != null) {
                return false;
            }
            if (email != null ? !email.equals(that.email) : that.email != null) {
                return false;
            }
            if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
                return false;
            }
            if (firstNameLength != null ? !firstNameLength.equals(that.firstNameLength) : that.firstNameLength != null) {
                return false;
            }
            if (locale != null ? !locale.equals(that.locale) : that.locale != null) {
                return false;
            }
            if (name != null ? !name.equals(that.name) : that.name != null) {
                return false;
            }
            if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
                return false;
            }
            if (phone != null ? !phone.equals(that.phone) : that.phone != null) {
                return false;
            }
            if (notes != null ? !notes.equals(that.notes) : that.notes != null) {
                return false;
            }
            if (postalCode != null ? !postalCode.equals(that.postalCode) : that.postalCode != null) {
                return false;
            }
            if (stateOrProvince != null ? !stateOrProvince.equals(that.stateOrProvince) : that.stateOrProvince != null) {
                return false;
            }
            if (referenceTime != null ? referenceTime.compareTo(that.referenceTime) != 0 : that.referenceTime != null) {
                return false;
            }
            if (timeZone != null ? !timeZone.equals(that.timeZone) : that.timeZone != null) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = externalKey != null ? externalKey.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (firstNameLength != null ? firstNameLength.hashCode() : 0);
            result = 31 * result + (email != null ? email.hashCode() : 0);
            result = 31 * result + (billCycleDayLocal != null ? billCycleDayLocal.hashCode() : 0);
            result = 31 * result + (currency != null ? currency.hashCode() : 0);
            result = 31 * result + (parentAccountId != null ? parentAccountId.hashCode() : 0);
            result = 31 * result + (isPaymentDelegatedToParent != null ? isPaymentDelegatedToParent.hashCode() : 0);
            result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
            result = 31 * result + (referenceTime != null ? referenceTime.hashCode() : 0);
            result = 31 * result + (timeZone != null ? timeZone.hashCode() : 0);
            result = 31 * result + (locale != null ? locale.hashCode() : 0);
            result = 31 * result + (address1 != null ? address1.hashCode() : 0);
            result = 31 * result + (address2 != null ? address2.hashCode() : 0);
            result = 31 * result + (companyName != null ? companyName.hashCode() : 0);
            result = 31 * result + (city != null ? city.hashCode() : 0);
            result = 31 * result + (stateOrProvince != null ? stateOrProvince.hashCode() : 0);
            result = 31 * result + (postalCode != null ? postalCode.hashCode() : 0);
            result = 31 * result + (country != null ? country.hashCode() : 0);
            result = 31 * result + (phone != null ? phone.hashCode() : 0);
            result = 31 * result + (notes != null ? notes.hashCode() : 0);
            result = 31 * result + (isMigrated != null ? isMigrated.hashCode() : 0);
            return result;
        }
    }
}
