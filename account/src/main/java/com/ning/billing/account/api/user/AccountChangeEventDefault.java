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

public class AccountChangeEventDefault implements AccountChangeNotification {
    private final List<ChangedField> changedFields;
    private final UUID id;

    public AccountChangeEventDefault(UUID id, Account oldData, Account newData) {
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
        List<ChangedField> changedFields = new ArrayList<ChangedField>();

        if (!newData.getExternalKey().equals(oldData.getExternalKey())) {
            changedFields.add(new DefaultChangedField("externalKey", newData.getExternalKey(), oldData.getExternalKey()));
        }
        if (!newData.getEmail().equals(oldData.getEmail())) {
            changedFields.add(new DefaultChangedField("email", newData.getEmail(), oldData.getEmail()));
        }
        if (!newData.getName().equals(oldData.getName())) {
            changedFields.add(new DefaultChangedField("firstName", newData.getName(), oldData.getName()));
        }
        if (!newData.getPhone().equals(oldData.getPhone())) {
            changedFields.add(new DefaultChangedField("phone", newData.getPhone(), oldData.getPhone()));
        }
        if (!newData.getCurrency().equals(oldData.getCurrency())) {
            changedFields.add(new DefaultChangedField("currency", newData.getCurrency().toString(), oldData.getCurrency().toString()));
        }
        if (newData.getBillCycleDay() != oldData.getBillCycleDay()) {
            changedFields.add(new DefaultChangedField("billCycleDay", Integer.toString(newData.getBillCycleDay()),
                                                               Integer.toString(oldData.getBillCycleDay())));
        }

        return changedFields;
    }
}
