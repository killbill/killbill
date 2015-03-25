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

public class CatalogOverridePlanPhaseModelDao {

    private Long recordId;
    private Short phaseNumber;
    private Long phaseDefRecordId;
    private Long targetPlanDefRecordId;
    private DateTime createdDate;
    private String createdBy;
    private Long tenantRecordId;

    public CatalogOverridePlanPhaseModelDao() {
    }

    public CatalogOverridePlanPhaseModelDao(final Short phaseNumber, final Long phaseDefRecordId, final Long targetPlanDefRecordId) {
        this.phaseNumber = phaseNumber;
        this.phaseDefRecordId = phaseDefRecordId;
        this.targetPlanDefRecordId = targetPlanDefRecordId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(final Long recordId) {
        this.recordId = recordId;
    }

    public Short getPhaseNumber() {
        return phaseNumber;
    }

    public void setPhaseNumber(final Short phaseNumber) {
        this.phaseNumber = phaseNumber;
    }

    public Long getPhaseDefRecordId() {
        return phaseDefRecordId;
    }

    public void setPhaseDefRecordId(final Long phaseDefRecordId) {
        this.phaseDefRecordId = phaseDefRecordId;
    }

    public Long getTargetPlanDefRecordId() {
        return targetPlanDefRecordId;
    }

    public void setTargetPlanDefRecordId(final Long targetPlanDefRecordId) {
        this.targetPlanDefRecordId = targetPlanDefRecordId;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    public void setTenantRecordId(final Long tenantRecordId) {
        this.tenantRecordId = tenantRecordId;
    }
}
