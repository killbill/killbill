/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.broadcast.dao;

import org.joda.time.DateTime;

public class BroadcastModelDao {

    private Long recordId;
    private String serviceName;
    private String type;
    private String event;
    private DateTime createdDate;
    private String createdBy;

    public BroadcastModelDao() {
    }

    public BroadcastModelDao(final String serviceName, final String type, final String event, final DateTime createdDate, final String createdBy) {
        this.recordId = -1L;
        this.serviceName = serviceName;
        this.type = type;
        this.event = event;
        this.createdDate = createdDate;
        this.createdBy = createdBy;
    }

    public Long getRecordId() {
        return recordId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getType() {
        return type;
    }

    public String getEvent() {
        return event;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BroadcastModelDao{");
        sb.append("recordId=").append(recordId);
        sb.append(", serviceName='").append(serviceName).append('\'');
        sb.append(", type='").append(type).append('\'');
        sb.append(", event='").append(event).append('\'');
        sb.append(", createdDate=").append(createdDate);
        sb.append(", createdBy='").append(createdBy).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
