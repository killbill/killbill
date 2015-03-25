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

public class CatalogOverridePhaseDefinitionModelDao {

    private Long recordId;
    private String parentPhaseName;
    private String currency;
    private BigDecimal fixedPrice;
    private BigDecimal recurringPrice;
    private DateTime effectiveDate;
    private DateTime createdDate;
    private String createdBy;
    private Long tenantRecordId;

    public CatalogOverridePhaseDefinitionModelDao() {
    }

    public CatalogOverridePhaseDefinitionModelDao(final String parentPhaseName, final String currency, final BigDecimal fixedPrice, final BigDecimal recurringPrice, final DateTime effectiveDate) {
        this.parentPhaseName = parentPhaseName;
        this.currency = currency;
        this.fixedPrice = fixedPrice;
        this.recurringPrice = recurringPrice;
        this.effectiveDate = effectiveDate;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(final Long recordId) {
        this.recordId = recordId;
    }

    public String getParentPhaseName() {
        return parentPhaseName;
    }

    public void setParentPhaseName(final String parentPhaseName) {
        this.parentPhaseName = parentPhaseName;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(final String currency) {
        this.currency = currency;
    }

    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    public void setFixedPrice(final BigDecimal fixedPrice) {
        this.fixedPrice = fixedPrice;
    }

    public BigDecimal getRecurringPrice() {
        return recurringPrice;
    }

    public void setRecurringPrice(final BigDecimal recurringPrice) {
        this.recurringPrice = recurringPrice;
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
