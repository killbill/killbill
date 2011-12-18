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

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.customfield.CustomizableEntityBase;
import com.ning.billing.util.tag.DefaultTag;
import com.ning.billing.util.tag.DefaultTagStore;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDescription;

public class DefaultAccount extends CustomizableEntityBase implements Account {
    public final static String OBJECT_TYPE = "Account";

    private final String externalKey;
    private final String email;
    private final String name;
    private final int firstNameLength;
    private final String phone;
    private final Currency currency;
    private final int billCycleDay;
    private final String paymentProviderName;
    private final DefaultTagStore tags;

    public DefaultAccount(AccountData data) {
        this(UUID.randomUUID(), data.getExternalKey(), data.getEmail(), data.getName(),
                data.getFirstNameLength(), data.getPhone(), data.getCurrency(), data.getBillCycleDay(),
                data.getPaymentProviderName());
    }

    public DefaultAccount(UUID id, AccountData data) {
        this(id, data.getExternalKey(), data.getEmail(), data.getName(),
                data.getFirstNameLength(), data.getPhone(), data.getCurrency(), data.getBillCycleDay(),
                data.getPaymentProviderName());
    }

    public DefaultAccount(UUID id, String externalKey, String email, String name, int firstNameLength,
                          String phone, Currency currency, int billCycleDay, String paymentProviderName) {
        super(id);
        this.externalKey = externalKey;
        this.email = email;
        this.name = name;
        this.firstNameLength = firstNameLength;
        this.phone = phone;
        this.currency = currency;
        this.billCycleDay = billCycleDay;
        this.paymentProviderName = paymentProviderName;

        this.tags = new DefaultTagStore(id, getObjectName());
    }

    @Override
    public String getObjectName() {
        return OBJECT_TYPE;
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
    public String getPhone() {
        return phone;
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
    public List<Tag> getTagList() {
        return tags.getEntityList();
    }

    @Override
    public boolean hasTag(String tagName) {
        return tags.containsTag(tagName);
    }

    @Override
    public void addTag(TagDescription description, String addedBy, DateTime dateAdded) {
        Tag tag = new DefaultTag(description, addedBy, dateAdded);
        tags.add(tag) ;
    }

    @Override
    public void addTags(List<Tag> tags) {
        this.tags.add(tags);
    }

    @Override
    public void clearTags() {
        this.tags.clear();
    }

    @Override
    public void removeTag(TagDescription description) {
        tags.remove(description.getName());
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
        return "DefaultAccount [externalKey=" + externalKey + ", email=" + email + ", name=" + name + ", firstNameLength=" + firstNameLength + ", phone=" + phone + ", currency=" + currency + ", billCycleDay=" + billCycleDay + ", paymentProviderName=" + paymentProviderName + ", tags=" + tags + "]";
    }

}
