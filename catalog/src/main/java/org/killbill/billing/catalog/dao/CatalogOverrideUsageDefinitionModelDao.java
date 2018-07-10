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

import java.math.BigDecimal;

import org.joda.time.DateTime;

public class CatalogOverrideUsageDefinitionModelDao {

    private Long recordId;
    private String parentUsageName;
    private String type;
    private String currency;
    private BigDecimal fixedPrice;
    private BigDecimal recurringPrice;
    private DateTime effectiveDate;
    private DateTime createdDate;
    private String createdBy;
    private Long tenantRecordId;

    public CatalogOverrideUsageDefinitionModelDao() {
    }

    public CatalogOverrideUsageDefinitionModelDao(String parentUsageName, String type, String currency, BigDecimal fixedPrice, BigDecimal recurringPrice, DateTime effectiveDate) {

        this.parentUsageName = parentUsageName;
        this.type = type;
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

    public void setType(String type) {
        this.type = type;
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

    public String getType() {
        return type;
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
