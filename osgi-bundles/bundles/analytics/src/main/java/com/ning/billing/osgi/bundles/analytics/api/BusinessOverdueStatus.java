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

package com.ning.billing.osgi.bundles.analytics.api;

import org.joda.time.DateTime;

import com.ning.billing.ObjectType;
import com.ning.billing.osgi.bundles.analytics.model.BusinessOverdueStatusModelDao;

public class BusinessOverdueStatus extends BusinessEntityBase {

    private final ObjectType objectType;
    private final String accountKey;
    private final String status;
    private final DateTime startDate;
    private final DateTime endDate;

    public BusinessOverdueStatus(final BusinessOverdueStatusModelDao businessOverdueStatusModelDao) {
        super(businessOverdueStatusModelDao.getCreatedDate(),
              businessOverdueStatusModelDao.getCreatedBy(),
              businessOverdueStatusModelDao.getCreatedReasonCode(),
              businessOverdueStatusModelDao.getCreatedComments(),
              businessOverdueStatusModelDao.getAccountId(),
              businessOverdueStatusModelDao.getAccountName(),
              businessOverdueStatusModelDao.getAccountExternalKey());

        // TODO For now
        this.objectType = ObjectType.BUNDLE;

        this.accountKey = businessOverdueStatusModelDao.getAccountExternalKey();
        this.status = businessOverdueStatusModelDao.getStatus();
        this.startDate = businessOverdueStatusModelDao.getStartDate();
        this.endDate = businessOverdueStatusModelDao.getEndDate();
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public String getStatus() {
        return status;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessOverdueStatus");
        sb.append("{objectType=").append(objectType);
        sb.append(", accountKey='").append(accountKey).append('\'');
        sb.append(", status='").append(status).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
        sb.append('}');
        return sb.toString();
    }

    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BusinessOverdueStatus that = (BusinessOverdueStatus) o;

        if (accountKey != null ? !accountKey.equals(that.accountKey) : that.accountKey != null) {
            return false;
        }
        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) {
            return false;
        }
        if (status != null ? !status.equals(that.status) : that.status != null) {
            return false;
        }

        return true;
    }

    public int hashCode() {
        int result = objectType != null ? objectType.hashCode() : 0;
        result = 31 * result + (accountKey != null ? accountKey.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        return result;
    }
}
