package org.killbill.billing.catalog.dao;

import org.joda.time.DateTime;

/**
 * Created by sruthipendyala on 10/7/16.
 */
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

    public Long getRecordId() {

        return recordId;
    }

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
