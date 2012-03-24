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

package com.ning.billing.analytics;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.DefaultMutableAccountData;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

public class MockAccount implements Account
{
    private final UUID id;
    private final String accountKey;
    private final Currency currency;

    public MockAccount(final UUID id, final String accountKey, final Currency currency)
    {
        this.id = id;
        this.accountKey = accountKey;
        this.currency = currency;
    }

    @Override
    public int getFirstNameLength() {
        return 0;
    }

    @Override
    public String getEmail()
    {
        return "test@test.com";
    }

    @Override
    public String getPhone()
    {
        return "408-555-6665";
    }

    @Override
    public String getExternalKey()
    {
        return accountKey;
    }

    @Override
    public String getName() {
        return "firstName lastName";
    }

    @Override
    public int getBillCycleDay()
    {
        return 12;
    }

    @Override
    public Currency getCurrency()
    {
        return currency;
    }

    @Override
    public String getPaymentProviderName() {
        return "PayPal";
    }

    @Override
    public DateTimeZone getTimeZone() {
        return DateTimeZone.forID("Pacific/Fiji");
    }

    @Override
    public String getLocale() {
        return "EN-US";
    }

    @Override
    public String getAddress1() {
        return null;
    }

    @Override
    public String getAddress2() {
        return null;
    }

    @Override
    public String getCompanyName() {
        return null;
    }

    @Override
    public String getCity() {
        return null;
    }

    @Override
    public String getStateOrProvince() {
        return null;
    }

    @Override
    public String getPostalCode() {
        return null;
    }

    @Override
    public String getCountry() {
        return null;
    }

    @Override
    public UUID getId()
    {
        return id;
    }

    @Override
    public String getFieldValue(String fieldName) {
        throw new NotImplementedException();
    }

    @Override
    public void setFieldValue(String fieldName, String fieldValue) {
        throw new NotImplementedException();
    }

    @Override
    public List<CustomField> getFieldList() {
        throw new NotImplementedException();
    }

    @Override
    public void addFields(List<CustomField> fields) {
        throw new NotImplementedException();
    }

    @Override
    public void clearFields() {
        throw new NotImplementedException();
    }

    @Override
    public String getObjectName() {
        throw new NotImplementedException();
    }

    @Override
    public List<Tag> getTagList() {
        throw new NotImplementedException();
    }

    @Override
    public boolean hasTag(String tagName) {
        throw new NotImplementedException();
    }

    @Override
    public void addTag(TagDefinition definition, String addedBy, DateTime dateAdded) {
        throw new NotImplementedException();
    }

    @Override
    public void addTags(List<Tag> tags) {
        throw new NotImplementedException();
    }

    @Override
    public void clearTags() {
        throw new NotImplementedException();
    }

    @Override
    public void removeTag(TagDefinition definition) {
        throw new NotImplementedException();
    }

    @Override
    public boolean generateInvoice() {
        throw new NotImplementedException();
    }

    @Override
    public boolean processPayment() {
        throw new NotImplementedException();
    }

    @Override
    public DateTime getCreatedDate() {
        return new DateTime(DateTimeZone.UTC);
    }

    @Override
    public DateTime getUpdatedDate() {
        return new DateTime(DateTimeZone.UTC);
    }

    @Override
    public DefaultMutableAccountData toMutableAccountData() {
        throw new NotImplementedException();
    }

}
