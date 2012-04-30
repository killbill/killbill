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

package com.ning.billing.junction.plumbing.api;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

public class BlockingAccount implements Account {
    private final Account account;
    private BlockingState blockingState = null;
    private BlockingApi blockingApi;

    public BlockingAccount( Account account, BlockingApi blockingApi) {
        this.account = account;
        this.blockingApi = blockingApi;
    }

    public List<Tag> getTagList() {
        return account.getTagList();
    }

    public String getUpdatedBy() {
        return account.getUpdatedBy();
    }

    public UUID getId() {
        return account.getId();
    }

    public String getCreatedBy() {
        return account.getCreatedBy();
    }

    public boolean hasTag(String tagName) {
        return account.hasTag(tagName);
    }

    public DateTime getUpdatedDate() {
        return account.getUpdatedDate();
    }

    public DateTime getCreatedDate() {
        return account.getCreatedDate();
    }

    public void addTag(TagDefinition definition) {
        account.addTag(definition);
    }

    public String getFieldValue(String fieldName) {
        return account.getFieldValue(fieldName);
    }

    public String getExternalKey() {
        return account.getExternalKey();
    }

    public String getName() {
        return account.getName();
    }

    public void addTags(List<Tag> tags) {
        account.addTags(tags);
    }

    public void setFieldValue(String fieldName, String fieldValue) {
        account.setFieldValue(fieldName, fieldValue);
    }

    public int getFirstNameLength() {
        return account.getFirstNameLength();
    }

    public void clearTags() {
        account.clearTags();
    }

    public String getEmail() {
        return account.getEmail();
    }

    public void removeTag(TagDefinition definition) {
        account.removeTag(definition);
    }

    public void saveFieldValue(String fieldName, String fieldValue, CallContext context) {
        account.saveFieldValue(fieldName, fieldValue, context);
    }

    public int getBillCycleDay() {
        return account.getBillCycleDay();
    }

    public boolean generateInvoice() {
        return account.generateInvoice();
    }

    public Currency getCurrency() {
        return account.getCurrency();
    }

    public boolean processPayment() {
        return account.processPayment();
    }

    public List<CustomField> getFieldList() {
        return account.getFieldList();
    }

    public String getPaymentProviderName() {
        return account.getPaymentProviderName();
    }

    public MutableAccountData toMutableAccountData() {
        return account.toMutableAccountData();
    }

    public void setFields(List<CustomField> fields) {
        account.setFields(fields);
    }

    public DateTimeZone getTimeZone() {
        return account.getTimeZone();
    }

    public String getLocale() {
        return account.getLocale();
    }

    public BlockingState getBlockingState() {
        if(blockingState == null) {
            blockingState = blockingApi.getBlockingStateFor(account);
        }
        return blockingState;
    }

    public void saveFields(List<CustomField> fields, CallContext context) {
        account.saveFields(fields, context);
    }

    public String getAddress1() {
        return account.getAddress1();
    }

    public String getAddress2() {
        return account.getAddress2();
    }

    public void clearFields() {
        account.clearFields();
    }

    public String getCompanyName() {
        return account.getCompanyName();
    }

    public void clearPersistedFields(CallContext context) {
        account.clearPersistedFields(context);
    }

    public String getCity() {
        return account.getCity();
    }

    public String getStateOrProvince() {
        return account.getStateOrProvince();
    }

    public String getObjectName() {
        return account.getObjectName();
    }

    public String getPostalCode() {
        return account.getPostalCode();
    }

    public String getCountry() {
        return account.getCountry();
    }

    public String getPhone() {
        return account.getPhone();
    }

}
