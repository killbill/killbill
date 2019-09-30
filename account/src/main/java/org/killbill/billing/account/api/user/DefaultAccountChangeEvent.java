/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.DefaultChangedField;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.events.AccountChangeInternalEvent;
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.ChangedField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class DefaultAccountChangeEvent extends BusEventBase implements AccountChangeInternalEvent {

    private final List<ChangedField> changedFields;
    private final UUID accountId;

    @JsonCreator
    public DefaultAccountChangeEvent(@JsonProperty("changeFields") final List<ChangedField> changedFields,
                                     @JsonProperty("accountId") final UUID accountId,
                                     @JsonProperty("searchKey1") final Long searchKey1,
                                     @JsonProperty("searchKey2") final Long searchKey2,
                                     @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.accountId = accountId;
        this.changedFields = changedFields;
    }

    public DefaultAccountChangeEvent(final UUID id,
                                     final AccountModelDao oldData,
                                     final AccountModelDao newData,
                                     final Long searchKey1,
                                     final Long searchKey2,
                                     final UUID userToken,
                                     final DateTime changeDate) {
        super(searchKey1, searchKey2, userToken);
        this.accountId = id;
        this.changedFields = calculateChangedFields(oldData, newData, changeDate);
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.ACCOUNT_CHANGE;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @JsonDeserialize(contentAs = DefaultChangedField.class)
    @Override
    public List<ChangedField> getChangedFields() {
        return changedFields;
    }

    @JsonIgnore
    @Override
    public boolean hasChanges() {
        return (!changedFields.isEmpty());
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("DefaultAccountChangeEvent{");
        sb.append("changedFields=").append(changedFields);
        sb.append(", accountId=").append(accountId);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((accountId == null) ? 0 : accountId.hashCode());
        result = prime * result
                 + ((changedFields == null) ? 0 : changedFields.hashCode());
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
        final DefaultAccountChangeEvent other = (DefaultAccountChangeEvent) obj;
        if (accountId == null) {
            if (other.accountId != null) {
                return false;
            }
        } else if (!accountId.equals(other.accountId)) {
            return false;
        }
        if (changedFields == null) {
            if (other.changedFields != null) {
                return false;
            }
        } else if (!changedFields.equals(other.changedFields)) {
            return false;
        }
        return true;
    }

    private List<ChangedField> calculateChangedFields(final AccountModelDao oldData, final AccountModelDao newData, final DateTime changeDate) {
        final List<ChangedField> tmpChangedFields = new ArrayList<ChangedField>();

        addIfValueChanged(tmpChangedFields, "externalKey",
                          oldData.getExternalKey(), newData.getExternalKey(), changeDate);

        addIfValueChanged(tmpChangedFields, "email",
                          oldData.getEmail(), newData.getEmail(), changeDate);

        addIfValueChanged(tmpChangedFields, "firstName",
                          oldData.getName(), newData.getName(), changeDate);

        addIfValueChanged(tmpChangedFields, "currency",
                          (oldData.getCurrency() != null) ? oldData.getCurrency().toString() : null,
                          (newData.getCurrency() != null) ? newData.getCurrency().toString() : null, changeDate);

        addIfValueChanged(tmpChangedFields,
                          "billCycleDayLocal",
                          String.valueOf(oldData.getBillingCycleDayLocal()), String.valueOf(newData.getBillingCycleDayLocal()), changeDate);

        addIfValueChanged(tmpChangedFields, "paymentMethodId",
                          (oldData.getPaymentMethodId() != null) ? oldData.getPaymentMethodId().toString() : null,
                          (newData.getPaymentMethodId() != null) ? newData.getPaymentMethodId().toString() : null, changeDate);

        addIfValueChanged(tmpChangedFields, "locale", oldData.getLocale(), newData.getLocale(), changeDate);

        addIfValueChanged(tmpChangedFields, "timeZone",
                          (oldData.getTimeZone() == null) ? null : oldData.getTimeZone().toString(),
                          (newData.getTimeZone() == null) ? null : newData.getTimeZone().toString(), changeDate);

        addIfValueChanged(tmpChangedFields, "address1", oldData.getAddress1(), newData.getAddress1(), changeDate);
        addIfValueChanged(tmpChangedFields, "address2", oldData.getAddress2(), newData.getAddress2(), changeDate);
        addIfValueChanged(tmpChangedFields, "city", oldData.getCity(), newData.getCity(), changeDate);
        addIfValueChanged(tmpChangedFields, "stateOrProvince", oldData.getStateOrProvince(), newData.getStateOrProvince(), changeDate);
        addIfValueChanged(tmpChangedFields, "country", oldData.getCountry(), newData.getCountry(), changeDate);
        addIfValueChanged(tmpChangedFields, "postalCode", oldData.getPostalCode(), newData.getPostalCode(), changeDate);
        addIfValueChanged(tmpChangedFields, "phone", oldData.getPhone(), newData.getPhone(), changeDate);

        return tmpChangedFields;
    }

    private void addIfValueChanged(final Collection<ChangedField> inputList, final String key, final String oldData, final String newData, final DateTime changeDate) {
        // If both null => no changes
        if (newData == null && oldData == null) {
            // If only one is null
        } else if (newData == null || oldData == null) {
            inputList.add(new DefaultChangedField(key, oldData, newData, changeDate));
            // If neither are null we can safely compare values
        } else if (!newData.equals(oldData)) {
            inputList.add(new DefaultChangedField(key, oldData, newData, changeDate));
        }
    }
}
