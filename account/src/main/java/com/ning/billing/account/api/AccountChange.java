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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AccountChange implements IAccountChange {
    private final List<IChangedField> changedFields;
    private final UUID id;

    public AccountChange(UUID id, IAccountData oldData, IAccountData newData) {
        this.id = id;
        this.changedFields = calculateChangedFields(oldData, newData);
    }

    @Override
    public UUID getAccountId() {
        return id;
    }

    @Override
    public List<IChangedField> getChangedFields() {
        return changedFields;
    }

    @Override
    public boolean hasChanges() {
        return (changedFields.size() > 0);
    }

    private List<IChangedField> calculateChangedFields(IAccountData oldData, IAccountData newData) {
        List<IChangedField> changedFields = new ArrayList<IChangedField>();

        if (!newData.getExternalKey().equals(oldData.getExternalKey())) {
            changedFields.add(new ChangedField("externalKey", newData.getExternalKey(), oldData.getExternalKey()));
        }
        if (!newData.getEmail().equals(oldData.getEmail())) {
            changedFields.add(new ChangedField("email", newData.getEmail(), oldData.getEmail()));
        }
        if (!newData.getFirstName().equals(oldData.getFirstName())) {
            changedFields.add(new ChangedField("firstName", newData.getFirstName(), oldData.getFirstName()));
        }
        if (!newData.getLastName().equals(oldData.getLastName())) {
            changedFields.add(new ChangedField("lastName", newData.getLastName(), oldData.getLastName()));
        }
        if (!newData.getPhone().equals(oldData.getPhone())) {
            changedFields.add(new ChangedField("phone", newData.getPhone(), oldData.getPhone()));
        }
        if (!newData.getCurrency().equals(oldData.getCurrency())) {
            changedFields.add(new ChangedField("currency", newData.getCurrency().toString(), oldData.getCurrency().toString()));
        }
        if (newData.getBillCycleDay() != oldData.getBillCycleDay()) {
            changedFields.add(new ChangedField("billCycleDay", Integer.toString(newData.getBillCycleDay()),
                                                               Integer.toString(oldData.getBillCycleDay())));
        }

        return changedFields;
    }
}
