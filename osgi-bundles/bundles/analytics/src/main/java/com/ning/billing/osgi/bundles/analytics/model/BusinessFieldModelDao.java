/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.osgi.bundles.analytics.model;

import java.util.UUID;

import org.joda.time.DateTime;

public abstract class BusinessFieldModelDao extends BusinessModelDaoBase {

    private final Long customFieldRecordId;
    private final String name;
    private final String value;

    protected BusinessFieldModelDao(final Long customFieldRecordId,
                                    final String name,
                                    final String value,
                                    final DateTime createdDate,
                                    final String createdBy,
                                    final String createdReasonCode,
                                    final String createdComments,
                                    final UUID accountId,
                                    final String accountName,
                                    final String accountExternalKey,
                                    final Long accountRecordId,
                                    final Long tenantRecordId) {
        super(createdDate,
              createdBy,
              createdReasonCode,
              createdComments,
              accountId,
              accountName,
              accountExternalKey,
              accountRecordId,
              tenantRecordId);
        this.customFieldRecordId = customFieldRecordId;
        this.name = name;
        this.value = value;
    }

    public Long getCustomFieldRecordId() {
        return customFieldRecordId;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessFieldModelDao");
        sb.append("{customFieldRecordId=").append(customFieldRecordId);
        sb.append(", name='").append(name).append('\'');
        sb.append(", value='").append(value).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final BusinessFieldModelDao that = (BusinessFieldModelDao) o;

        if (customFieldRecordId != null ? !customFieldRecordId.equals(that.customFieldRecordId) : that.customFieldRecordId != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (customFieldRecordId != null ? customFieldRecordId.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
