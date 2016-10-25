package org.killbill.billing.catalog.dao;

import org.joda.time.DateTime;

/**
 * Created by sruthipendyala on 10/7/16.
 */
public class CatalogOverrideTierBlockModelDao {
    private Short blockNumber;
    private Long recordId;
    private Long blockDefRecordId;
    private Long targetTierRecordId;
    private DateTime createdDate;
    private String createdBy;
    private Long tenantRecordId;

    public CatalogOverrideTierBlockModelDao() {
    }

    public CatalogOverrideTierBlockModelDao(Short blockNumber, Long blockDefRecordId, Long targetTierRecordId) {
        this.blockNumber = blockNumber;
        this.blockDefRecordId = blockDefRecordId;
        this.targetTierRecordId = targetTierRecordId;
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

    public Long getTargetTierRecordId() {
        return targetTierRecordId;
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

    public void setTargetTierRecordId(Long targetTierRecordId) {
        this.targetTierRecordId = targetTierRecordId;
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
