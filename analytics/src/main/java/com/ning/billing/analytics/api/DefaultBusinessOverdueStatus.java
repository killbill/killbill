/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics.api;

import org.joda.time.DateTime;

import com.ning.billing.ObjectType;
import com.ning.billing.analytics.model.BusinessOverdueStatusModelDao;
import com.ning.billing.util.entity.EntityBase;

public class DefaultBusinessOverdueStatus extends EntityBase implements BusinessOverdueStatus {

    private final ObjectType objectType;
    private final String accountKey;
    private final String status;
    private final DateTime startDate;
    private final DateTime endDate;

    public DefaultBusinessOverdueStatus(final BusinessOverdueStatusModelDao businessOverdueStatusModelDao) {
        // TODO
        super(businessOverdueStatusModelDao.getBundleId());
        this.objectType = ObjectType.BUNDLE;

        this.accountKey = businessOverdueStatusModelDao.getAccountKey();
        this.status = businessOverdueStatusModelDao.getStatus();
        this.startDate = businessOverdueStatusModelDao.getStartDate();
        this.endDate = businessOverdueStatusModelDao.getEndDate();
    }

    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public String getAccountKey() {
        return accountKey;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public DateTime getStartDate() {
        return startDate;
    }

    @Override
    public DateTime getEndDate() {
        return endDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultBusinessOverdueStatus");
        sb.append("{objectType=").append(objectType);
        sb.append(", id='").append(id).append('\'');
        sb.append(", accountKey='").append(accountKey).append('\'');
        sb.append(", status='").append(status).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
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

        final DefaultBusinessOverdueStatus that = (DefaultBusinessOverdueStatus) o;

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

    @Override
    public int hashCode() {
        int result = objectType != null ? objectType.hashCode() : 0;
        result = 31 * result + (accountKey != null ? accountKey.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        return result;
    }
}
