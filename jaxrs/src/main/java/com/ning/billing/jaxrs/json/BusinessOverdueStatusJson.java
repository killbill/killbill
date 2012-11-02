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

package com.ning.billing.jaxrs.json;

import org.joda.time.DateTime;

import com.ning.billing.analytics.api.BusinessOverdueStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BusinessOverdueStatusJson extends JsonBase {

    private final String objectType;
    private final String id;
    private final String accountKey;
    private final String status;
    private final DateTime startDate;
    private final DateTime endDate;

    @JsonCreator
    public BusinessOverdueStatusJson(@JsonProperty("objectType") final String objectType,
                                     @JsonProperty("id") final String id,
                                     @JsonProperty("accountKey") final String accountKey,
                                     @JsonProperty("status") final String status,
                                     @JsonProperty("startDate") final DateTime startDate,
                                     @JsonProperty("endDate") final DateTime endDate) {
        this.objectType = objectType;
        this.id = id;
        this.accountKey = accountKey;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public BusinessOverdueStatusJson(final BusinessOverdueStatus businessOverdueStatus) {
        this(businessOverdueStatus.getObjectType().toString(),
             businessOverdueStatus.getId().toString(),
             businessOverdueStatus.getAccountKey(),
             businessOverdueStatus.getStatus(),
             businessOverdueStatus.getStartDate(),
             businessOverdueStatus.getEndDate());
    }

    public String getObjectType() {
        return objectType;
    }

    public String getId() {
        return id;
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessOverdueStatusJson");
        sb.append("{objectType='").append(objectType).append('\'');
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

        final BusinessOverdueStatusJson that = (BusinessOverdueStatusJson) o;

        if (accountKey != null ? !accountKey.equals(that.accountKey) : that.accountKey != null) {
            return false;
        }
        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (objectType != null ? !objectType.equals(that.objectType) : that.objectType != null) {
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
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (accountKey != null ? accountKey.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        return result;
    }
}
