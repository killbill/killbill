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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.google.inject.Inject;
import com.ning.billing.util.clock.Clock;
import org.joda.time.DateTime;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.customfield.CustomizableEntityBase;
import com.ning.billing.util.tag.DefaultTag;
import com.ning.billing.util.tag.DefaultTagStore;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDescription;
import org.joda.time.DateTimeZone;

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
    private final BigDecimal balance;
    private final DefaultTagStore tags;
    private final DateTime createdDate;
    private final DateTime updatedDate;

    public DefaultAccount(final AccountData data) {
        this(UUID.randomUUID(), data.getExternalKey(), data.getEmail(), data.getName(),
                data.getFirstNameLength(), data.getPhone(), data.getCurrency(), data.getBillCycleDay(),
                data.getPaymentProviderName(), BigDecimal.ZERO, null, null);
    }

    public DefaultAccount(final UUID id, final AccountData data) {
        this(id, data.getExternalKey(), data.getEmail(), data.getName(),
                data.getFirstNameLength(), data.getPhone(), data.getCurrency(), data.getBillCycleDay(),
                data.getPaymentProviderName(), BigDecimal.ZERO, null, null);
    }

    public DefaultAccount(UUID id,
                          String externalKey,
                          String email,
                          String name,
                          int firstNameLength,
                          String phone,
                          Currency currency,
                          int billCycleDay,
                          String paymentProviderName,
                          BigDecimal balance) {
        this(id, externalKey, email, name, firstNameLength, phone, currency, billCycleDay, paymentProviderName, balance, null, null);
    }

    public DefaultAccount(final UUID id, final String externalKey, final String email,
                          final String name, final int firstNameLength,
                          final String phone, final Currency currency, final int billCycleDay, final String paymentProviderName,
                          final BigDecimal balance, final DateTime createdDate, final DateTime updatedDate) {
        super(id);
        this.externalKey = externalKey;
        this.email = email;
        this.name = name;
        this.firstNameLength = firstNameLength;
        this.phone = phone;
        this.currency = currency;
        this.billCycleDay = billCycleDay;
        this.paymentProviderName = paymentProviderName;
        this.balance = balance;
        DateTime now = new DateTime(DateTimeZone.UTC);
        this.createdDate = (createdDate == null) ? now : createdDate;
        this.updatedDate = (updatedDate == null) ? now : updatedDate;

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
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public List<Tag> getTagList() {
        return tags.getEntityList();
    }

    @Override
    public boolean hasTag(final String tagName) {
        return tags.containsTag(tagName);
    }

    @Override
    public void addTag(final TagDescription description, final String addedBy, final DateTime dateAdded) {
        Tag tag = new DefaultTag(description, addedBy, dateAdded);
        tags.add(tag) ;
    }

    @Override
    public void addTags(final List<Tag> tags) {
        if (tags != null) {
            this.tags.add(tags);
        }
    }

    @Override
    public void clearTags() {
        this.tags.clear();
    }

    @Override
    public void removeTag(final TagDescription description) {
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
    public BigDecimal getBalance() {
        return balance;
    }

    @Override
    public String toString() {
        return "DefaultAccount [externalKey=" + externalKey + ", email=" + email + ", name=" + name + ", firstNameLength=" + firstNameLength + ", phone=" + phone + ", currency=" + currency + ", billCycleDay=" + billCycleDay + ", paymentProviderName=" + paymentProviderName + ", balance=" + balance + ", tags=" + tags + ", createdDate=" + createdDate + ", updatedDate=" + updatedDate + "]";
    }
}