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

public class CatalogOverridePhaseUsageModelDao {

    private Long recordId;
    private Short usageNumber;
    private Long usageDefRecordId;
    private Long targetPhaseDefRecordId;
    private DateTime createdDate;
    private String createdBy;
    private Long tenantRecordId;

    public CatalogOverridePhaseUsageModelDao() {
    }

    public CatalogOverridePhaseUsageModelDao(Short usageNumber, Long usageDefRecordId, Long targetPhaseDefRecordId) {
        this.usageNumber = usageNumber;
        this.usageDefRecordId = usageDefRecordId;
        this.targetPhaseDefRecordId = targetPhaseDefRecordId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public Short getUsageNumber() {
        return usageNumber;
    }

    public Long getUsageDefRecordId() {
        return usageDefRecordId;
    }

    public Long getTargetPhaseDefRecordId() {
        return targetPhaseDefRecordId;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public void setUsageNumber(Short usageNumber) {
        this.usageNumber = usageNumber;
    }

    public void setUsageDefRecordId(Long usageDefRecordId) {
        this.usageDefRecordId = usageDefRecordId;
    }

    public void setTargetPhaseDefRecordId(Long targetPhaseDefRecordId) {
        this.targetPhaseDefRecordId = targetPhaseDefRecordId;
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
}
