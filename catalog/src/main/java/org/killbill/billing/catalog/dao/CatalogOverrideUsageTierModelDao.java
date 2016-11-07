/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.catalog.dao;

import org.joda.time.DateTime;

public class CatalogOverrideUsageTierModelDao {

    private Long recordId;
    private Short tierNumber;
    private Long tierDefRecordId;
    private Long targetUsageDefRecordId;
    private DateTime createdDate;
    private String createdBy;
    private Long tenantRecordId;

    public CatalogOverrideUsageTierModelDao() {
    }

    public CatalogOverrideUsageTierModelDao(Short tierNumber, Long tierDefRecordId, Long targetUsageDefRecordId) {
        this.tierNumber = tierNumber;
        this.tierDefRecordId = tierDefRecordId;
        this.targetUsageDefRecordId = targetUsageDefRecordId;
    }

    public Long getRecordId() { return recordId; }

    public Short getTierNumber() {
        return tierNumber;
    }

    public void setTierNumber(Short tierNumber) {
        this.tierNumber = tierNumber;
    }

    public Long getTierDefRecordId() {
        return tierDefRecordId;
    }

    public Long getTargetUsageDefRecordId() {
        return targetUsageDefRecordId;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public void setTierDefRecordId(Long tierDefRecordId) {
        this.tierDefRecordId = tierDefRecordId;
    }

    public void setTargetUsageDefRecordId(Long targetUsageDefRecordId) {
        this.targetUsageDefRecordId = targetUsageDefRecordId;
    }

    public void setCreatedDate(DateTime createdDate) {
        this.createdDate = createdDate;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setTenantRecordId(Long tenantRecordId) {
        this.tenantRecordId = tenantRecordId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Long getTenantRecordId() {
        return tenantRecordId;
    }
}
