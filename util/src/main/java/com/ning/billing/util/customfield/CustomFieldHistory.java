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

package com.ning.billing.util.customfield;

import com.ning.billing.util.entity.ChangeType;
import org.joda.time.DateTime;

import java.util.UUID;

public class CustomFieldHistory implements CustomField {
    private final UUID historyId = UUID.randomUUID();
    private final CustomField field;
    private final ChangeType changeType;

    public CustomFieldHistory(CustomField field, ChangeType changeType) {
        this.field = field;
        this.changeType = changeType;
    }

    public UUID getHistoryId() {
        return historyId;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    @Override
    public String getName() {
        return field.getName();
    }

    @Override
    public String getValue() {
        return field.getValue();
    }

    @Override
    public void setValue(String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getId() {
        return field.getId();
    }

    @Override
    public String getCreatedBy() {
        return field.getCreatedBy();
    }

    @Override
    public DateTime getCreatedDate() {
        return field.getCreatedDate();
    }
}
