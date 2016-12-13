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

public class CatalogOverrideTierBlockModelDao {
    private Short blockNumber;
    private Long recordId;
    private Long blockDefRecordId;
    private Long targetTierDefRecordId;
    private DateTime createdDate;
    private String createdBy;
    private Long tenantRecordId;

    public CatalogOverrideTierBlockModelDao() {
    }

    public CatalogOverrideTierBlockModelDao(Short blockNumber, Long blockDefRecordId, Long targetTierDefRecordId) {
        this.blockNumber = blockNumber;
        this.blockDefRecordId = blockDefRecordId;
        this.targetTierDefRecordId = targetTierDefRecordId;
    }

    public Short getBlockNumber() {
        return blockNumber;
    }

    public Long getRecordId() {
        return recordId;
    }

    public Long getBlockDefRecordId() {
        return blockDefRecordId;
    }

    public Long getTargetTierDefRecordId() {
        return targetTierDefRecordId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    public void setBlockNumber(Short blockNumber) {
        this.blockNumber = blockNumber;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public void setBlockDefRecordId(Long blockDefRecordId) {
        this.blockDefRecordId = blockDefRecordId;
    }

    public void setTargetTierDefRecordId(Long targetTierDefRecordId) {
        this.targetTierDefRecordId = targetTierDefRecordId;
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
