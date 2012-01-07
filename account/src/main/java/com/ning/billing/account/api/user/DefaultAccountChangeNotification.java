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

package com.ning.billing.account.api.user;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountChangeNotification;
import com.ning.billing.account.api.ChangedField;
import com.ning.billing.account.api.DefaultChangedField;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DefaultAccountChangeNotification implements AccountChangeNotification {
    private final List<ChangedField> changedFields;
    private final UUID id;

    public DefaultAccountChangeNotification(UUID id, Account oldData, Account newData) {
        this.id = id;
        this.changedFields = calculateChangedFields(oldData, newData);
    }

    @Override
    public UUID getAccountId() {
        return id;
    }

    @Override
    public List<ChangedField> getChangedFields() {
        return changedFields;
    }

    @Override
    public boolean hasChanges() {
        return (changedFields.size() > 0);
    }

    private List<ChangedField> calculateChangedFields(Account oldData, Account newData) {

        List<ChangedField> tmpChangedFields = new ArrayList<ChangedField>();

        addIfValueChanged(tmpChangedFields, "externalKey",
                oldData.getExternalKey(), newData.getExternalKey());

        addIfValueChanged(tmpChangedFields, "email",
                oldData.getEmail(), newData.getEmail());

        addIfValueChanged(tmpChangedFields, "firstName",
                oldData.getName(), newData.getName());

        addIfValueChanged(tmpChangedFields, "phone",
                oldData.getPhone(), newData.getPhone());

        addIfValueChanged(tmpChangedFields, "currency",
                (oldData.getCurrency() != null) ? oldData.getCurrency().toString() : null,
                 (newData.getCurrency() != null) ? newData.getCurrency().toString() : null);

        addIfValueChanged(tmpChangedFields,
                "billCycleDay",
                Integer.toString(oldData.getBillCycleDay()), Integer.toString(newData.getBillCycleDay()));

        addIfValueChanged(tmpChangedFields,"paymentProviderName",
                oldData.getPaymentProviderName(), newData.getPaymentProviderName());

        addIfValueChanged(tmpChangedFields, "locale", oldData.getLocale(), newData.getLocale());

        addIfValueChanged(tmpChangedFields, "timeZone",
                oldData.getTimeZone().toString(),
                newData.getTimeZone().toString());

        addIfValueChanged(tmpChangedFields, "nextBillingDate",
                oldData.getNextBillingDate().toString(),
                newData.getNextBillingDate().toString());

        return tmpChangedFields;
    }

    private void addIfValueChanged(List<ChangedField> inputList, String key, String oldData, String newData) {
        // If both null => no changes
        if (newData == null && oldData == null) {
            return;
        // If only one is null
        } else if (newData == null || oldData == null) {
            inputList.add(new DefaultChangedField(key, oldData, newData));
        // If neither are null we can safely compare values
        } else if (!newData.equals(oldData)) {
            inputList.add(new DefaultChangedField(key, oldData, newData));
        }
    }
}
