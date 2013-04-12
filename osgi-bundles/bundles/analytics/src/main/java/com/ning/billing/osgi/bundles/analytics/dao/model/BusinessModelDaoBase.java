/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

public abstract class BusinessModelDaoBase {

    // See ddl.sql
    private final String DEFAULT_REPORT_GROUP = "default";

    // See ddl.sql
    public enum ReportGroup {
        test,
        partner
    }

    protected Long recordId;

    protected DateTime createdDate;
    protected String createdBy;
    protected String createdReasonCode;
    protected String createdComments;
    protected UUID accountId;
    protected String accountName;
    protected String accountExternalKey;
    protected Long accountRecordId;
    protected Long tenantRecordId;
    protected String reportGroup;

    public BusinessModelDaoBase() { /* When reading from the database */ }

    public BusinessModelDaoBase(final DateTime createdDate,
                                final String createdBy,
                                final String createdReasonCode,
                                final String createdComments,
                                final UUID accountId,
                                final String accountName,
                                final String accountExternalKey,
                                final Long accountRecordId,
                                final Long tenantRecordId,
                                @Nullable final ReportGroup reportGroup) {
        recordId = null;
        this.createdDate = createdDate;
        this.createdBy = createdBy;
        this.createdReasonCode = createdReasonCode;
        this.createdComments = createdComments;
        this.accountId = accountId;
        this.accountName = accountName;
        this.accountExternalKey = accountExternalKey;
        this.accountRecordId = accountRecordId;
        this.tenantRecordId = tenantRecordId;
        this.reportGroup = reportGroup == null ? DEFAULT_REPORT_GROUP : reportGroup.toString();
    }

    public abstract String getTableName();

    public Long getRecordId() {
        return recordId;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getCreatedReasonCode() {
        return createdReasonCode;
    }

    public String getCreatedComments() {
        return createdComments;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountExternalKey() {
        return accountExternalKey;
    }

    public Long getAccountRecordId() {
        return accountRecordId;
    }

    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    public String getReportGroup() {
        return reportGroup;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessModelDaoBase");
        sb.append("{DEFAULT_REPORT_GROUP='").append(DEFAULT_REPORT_GROUP).append('\'');
        sb.append(", recordId=").append(recordId);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", createdBy='").append(createdBy).append('\'');
        sb.append(", createdReasonCode='").append(createdReasonCode).append('\'');
        sb.append(", createdComments='").append(createdComments).append('\'');
        sb.append(", accountId=").append(accountId);
        sb.append(", accountName='").append(accountName).append('\'');
        sb.append(", accountExternalKey='").append(accountExternalKey).append('\'');
        sb.append(", accountRecordId=").append(accountRecordId);
        sb.append(", tenantRecordId=").append(tenantRecordId);
        sb.append(", reportGroup='").append(reportGroup).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BusinessModelDaoBase that = (BusinessModelDaoBase) o;

        if (accountExternalKey != null ? !accountExternalKey.equals(that.accountExternalKey) : that.accountExternalKey != null) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (accountName != null ? !accountName.equals(that.accountName) : that.accountName != null) {
            return false;
        }
        if (accountRecordId != null ? !accountRecordId.equals(that.accountRecordId) : that.accountRecordId != null) {
            return false;
        }
        if (createdBy != null ? !createdBy.equals(that.createdBy) : that.createdBy != null) {
            return false;
        }
        if (createdComments != null ? !createdComments.equals(that.createdComments) : that.createdComments != null) {
            return false;
        }
        if (createdDate != null ? (createdDate.compareTo(that.createdDate) != 0) : that.createdDate != null) {
            return false;
        }
        if (createdReasonCode != null ? !createdReasonCode.equals(that.createdReasonCode) : that.createdReasonCode != null) {
            return false;
        }
        if (reportGroup != null ? !reportGroup.equals(that.reportGroup) : that.reportGroup != null) {
            return false;
        }
        if (tenantRecordId != null ? !tenantRecordId.equals(that.tenantRecordId) : that.tenantRecordId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = DEFAULT_REPORT_GROUP.hashCode();
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (createdBy != null ? createdBy.hashCode() : 0);
        result = 31 * result + (createdReasonCode != null ? createdReasonCode.hashCode() : 0);
        result = 31 * result + (createdComments != null ? createdComments.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (accountName != null ? accountName.hashCode() : 0);
        result = 31 * result + (accountExternalKey != null ? accountExternalKey.hashCode() : 0);
        result = 31 * result + (accountRecordId != null ? accountRecordId.hashCode() : 0);
        result = 31 * result + (tenantRecordId != null ? tenantRecordId.hashCode() : 0);
        result = 31 * result + (reportGroup != null ? reportGroup.hashCode() : 0);
        return result;
    }
}
