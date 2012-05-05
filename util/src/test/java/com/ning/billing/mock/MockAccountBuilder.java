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

package com.ning.billing.mock;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

public class MockAccountBuilder {
    private final UUID id;
    private String externalKey;
    private String email;
    private String name;
    private int firstNameLength;
    private Currency currency;
    private int billingCycleDay;
    private String paymentProviderName;
    private DateTimeZone timeZone;
    private String locale;
    private String address1;
    private String address2;
    private String companyName;
    private String city;
    private String stateOrProvince;
    private String country;
    private String postalCode;
    private String phone;
    private boolean migrated;
    private boolean isNotifiedForInvoices;
    private String createdBy;
    private DateTime createdDate;
    private String updatedBy;
    private DateTime updatedDate;

    public MockAccountBuilder() {
        this(UUID.randomUUID());
    }

    public MockAccountBuilder(final UUID id) {
        this.id = id;
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

    public MockAccountBuilder billingCycleDay(final int billingCycleDay) {
        this.billingCycleDay = billingCycleDay;
        return this;
    }

    public MockAccountBuilder currency(final Currency currency) {
        this.currency = currency;
        return this;
    }

    public MockAccountBuilder paymentProviderName(final String paymentProviderName) {
        this.paymentProviderName = paymentProviderName;
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

    public MockAccountBuilder migrated(final boolean migrated) {
        this.migrated = migrated;
        return this;
    }

    public MockAccountBuilder isNotifiedForInvoices(final boolean isNotifiedForInvoices) {
        this.isNotifiedForInvoices = isNotifiedForInvoices;
        return this;
    }

    public MockAccountBuilder createdBy(final String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public MockAccountBuilder createdDate(final DateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public MockAccountBuilder updatedBy(final String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }

    public MockAccountBuilder updatedDate(final DateTime updatedDate) {
        this.updatedDate = updatedDate;
        return this;
    }

    public Account build() {
        return new Account(){
            
            @Override
            public String getExternalKey() {
                return externalKey;
            }

            @Override
            public String getName() {
               
                return name;
            }

            @Override
            public int getFirstNameLength() {
               
                return firstNameLength;
            }

            @Override
            public String getEmail() {
               
                return email;
            }

            @Override
            public int getBillCycleDay() {
               
                return billingCycleDay;
            }

            @Override
            public Currency getCurrency() {
               
                return currency;
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
            public boolean isMigrated() {
               
                return migrated;
            }

            @Override
            public boolean isNotifiedForInvoices() {
               
                return isNotifiedForInvoices;
            }

            @Override
            public String getFieldValue(String fieldName) {
               
                return null;
            }

            @Override
            public void setFieldValue(String fieldName, String fieldValue) {
               
                
            }

            @Override
            public void saveFieldValue(String fieldName, String fieldValue, CallContext context) {
               
                
            }

            @Override
            public List<CustomField> getFieldList() {
               
                return null;
            }

            @Override
            public void setFields(List<CustomField> fields) {
               
                
            }

            @Override
            public void saveFields(List<CustomField> fields, CallContext context) {
               
                
            }

            @Override
            public void clearFields() {
               
                
            }

            @Override
            public void clearPersistedFields(CallContext context) {
               
                
            }

            @Override
            public String getObjectName() {
               
                return null;
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
            public UUID getId() {
               
                return id;
            }

            @Override
            public String getCreatedBy() {
               
                return createdBy;
            }

            @Override
            public DateTime getCreatedDate() {
               
                return createdDate;
            }

            @Override
            public List<Tag> getTagList() {
               
                return null;
            }

            @Override
            public boolean hasTag(TagDefinition tagDefinition) {
               
                return false;
            }

            @Override
            public boolean hasTag(ControlTagType controlTagType) {
               
                return false;
            }

            @Override
            public void addTag(TagDefinition definition) {
               
                
            }

            @Override
            public void addTags(List<Tag> tags) {
               
                
            }

            @Override
            public void addTagsFromDefinitions(List<TagDefinition> tagDefinitions) {
               
                
            }

            @Override
            public void clearTags() {
               
                
            }

            @Override
            public void removeTag(TagDefinition definition) {
               
                
            }

            @Override
            public boolean generateInvoice() {
               
                return true;
            }

            @Override
            public boolean processPayment() {
               
                return true;
            }

            @Override
            public BlockingState getBlockingState() {
               
                return null;
            }

            @Override
            public MutableAccountData toMutableAccountData() {
               
                throw new NotImplementedException();
            }
            
            
        };
        
       
    }
}
