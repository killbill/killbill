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

public class CatalogOverridePlanDefinitionModelDao {

    private Long recordId;
    private String parentPlanName;
    private Boolean isActive;
    private DateTime effectiveDate;
    private DateTime createdDate;
    private String createdBy;
    private Long tenantRecordId;

    public CatalogOverridePlanDefinitionModelDao() {
    }

    public CatalogOverridePlanDefinitionModelDao(final String parentPlanName, final Boolean isActive, final DateTime effectiveDate) {
        this.recordId = 0L;
        this.parentPlanName = parentPlanName;
        this.isActive = isActive;
        this.effectiveDate = effectiveDate;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(final Long recordId) {
        this.recordId = recordId;
    }

    public String getParentPlanName() {
        return parentPlanName;
    }

    public void setParentPlanName(final String parentPlanName) {
        this.parentPlanName = parentPlanName;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(final Boolean isActive) {
        this.isActive = isActive;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(final DateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
    }

    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    public void setTenantRecordId(final Long tenantRecordId) {
        this.tenantRecordId = tenantRecordId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return "CatalogOverridePlanDefinitionModelDao{" +
               "recordId=" + recordId +
               ", parentPlanName='" + parentPlanName + '\'' +
               ", isActive=" + isActive +
               ", effectiveDate=" + effectiveDate +
               ", createdDate=" + createdDate +
               ", createdBy='" + createdBy + '\'' +
               ", tenantRecordId=" + tenantRecordId +
               '}';
    }
}

