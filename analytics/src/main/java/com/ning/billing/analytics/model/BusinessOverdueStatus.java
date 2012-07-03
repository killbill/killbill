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

package com.ning.billing.analytics.model;

import org.joda.time.DateTime;

public class BusinessOverdueStatus {
    private final String accountKey;
    private final String externalKey;
    private final String status;
    private final DateTime startDate;
    private final DateTime endDate;

    public BusinessOverdueStatus(final String accountKey, final DateTime endDate, final String externalKey,
                                 final DateTime startDate, final String status) {
        this.accountKey = accountKey;
        this.endDate = endDate;
        this.externalKey = externalKey;
        this.startDate = startDate;
        this.status = status;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessOverdueStatus");
        sb.append("{accountKey=").append(accountKey);
        sb.append(", endDate='").append(endDate).append('\'');
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", status='").append(status).append('\'');
        sb.append(", startDate=").append(startDate);
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

        final BusinessOverdueStatus that = (BusinessOverdueStatus) o;

        if (accountKey != null ? !accountKey.equals(that.accountKey) : that.accountKey != null) {
            return false;
        }
        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
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
        int result = accountKey != null ? accountKey.hashCode() : 0;
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        return result;
    }
}
