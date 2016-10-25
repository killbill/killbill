package org.killbill.billing.catalog.dao;

import org.joda.time.DateTime;

import java.math.BigDecimal;

/**
 * Created by sruthipendyala on 10/7/16.
 */
public class CatalogOverrideBlockDefinitionModelDao {

    private Long recordId;
    private String parentUnitName;
    private String currency;
    private BigDecimal price;
    private String blockType;
    private double size;
    private double max;
    private DateTime effectiveDate;
    private DateTime createdDate;
    private String createdBy;
    private Long tenantRecordId;

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getParentUnitName() {
        return parentUnitName;
    }

    public void setParentUnitName(String parentUnitName) {
        this.parentUnitName = parentUnitName;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getBlockType() {
        return blockType;
    }

    public void setBlockType(String blockType) {
        this.blockType = blockType;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }


    public double getMax() {
        return max;
    }

    public void setMax(double max) {
        this.max = max;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(DateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(DateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }


    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    public void setTenantRecordId(Long tenantRecordId) {
        this.tenantRecordId = tenantRecordId;
    }

    public CatalogOverrideBlockDefinitionModelDao() {
    }

    public CatalogOverrideBlockDefinitionModelDao(String parentUnitName, String currency, BigDecimal price, double size, double max, DateTime effectiveDate) {

        this.parentUnitName = parentUnitName;
        this.currency = currency;
        this.price = price;
        this.size = size;
        this.max = max;
        this.effectiveDate = effectiveDate;
    }
}
