package org.killbill.billing.catalog.dao;

import org.joda.time.DateTime;

/**
 * Created by sruthipendyala on 10/7/16.
 */
public class CatalogOverridePhaseUsageModelDao {

    private Short usageNumber;
    private Long recordId;
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

    public Long gettargetPhaseDefRecordId() {
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

    public void settargetPhaseDefRecordId(Long targetPhaseDefRecordId) {
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
