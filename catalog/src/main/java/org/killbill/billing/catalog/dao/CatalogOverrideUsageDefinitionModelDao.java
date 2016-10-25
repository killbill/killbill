package org.killbill.billing.catalog.dao;

import org.joda.time.DateTime;

import java.math.BigDecimal;

/**
 * Created by sruthipendyala on 10/7/16.
 */
public class CatalogOverrideUsageDefinitionModelDao {


    private Long recordId;
    private String parentUsageName;
    private String parentUsageType;
    private String currency;
    private BigDecimal fixedPrice;
    private BigDecimal recurringPrice;
    private DateTime effectiveDate;
    private DateTime createdDate;
    private String createdBy;
    private Long tenantRecordId;

    public CatalogOverrideUsageDefinitionModelDao() {
    }

    public CatalogOverrideUsageDefinitionModelDao(String parentUsageName, String parentUsageType, String currency, BigDecimal fixedPrice, BigDecimal recurringPrice, DateTime effectiveDate) {

        this.parentUsageName = parentUsageName;
        this.parentUsageType = parentUsageType;
        this.currency = currency;
        this.fixedPrice = fixedPrice;
        this.recurringPrice = recurringPrice;
        this.effectiveDate = effectiveDate;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public void setParentUsageName(String parentUsageName) {
        this.parentUsageName = parentUsageName;
    }

    public void setParentUsageType(String parentUsageType) {
        this.parentUsageType = parentUsageType;
    }



    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setFixedPrice(BigDecimal fixedPrice) {
        this.fixedPrice = fixedPrice;
    }

    public void setRecurringPrice(BigDecimal recurringPrice) {
        this.recurringPrice = recurringPrice;
    }

    public void setEffectiveDate(DateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
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

    public String getParentUsageName() {

        return parentUsageName;
    }

    public String getParentUsageType() {
        return parentUsageType;
    }



    public String getCurrency() {
        return currency;
    }

    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    public BigDecimal getRecurringPrice() {
        return recurringPrice;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
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




}
