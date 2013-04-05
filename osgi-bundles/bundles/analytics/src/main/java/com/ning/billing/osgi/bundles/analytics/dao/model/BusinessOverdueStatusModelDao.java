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

import org.joda.time.DateTime;

import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.audit.AuditLog;

public class BusinessOverdueStatusModelDao extends BusinessModelDaoBase {

    private static final String OVERDUE_STATUS_TABLE_NAME = "bos";

    private final Long blockingStateRecordId;
    private final UUID bundleId;
    private final String bundleExternalKey;
    private final String status;
    private final DateTime startDate;
    private final DateTime endDate;

    public BusinessOverdueStatusModelDao(final Long blockingStateRecordId,
                                         final UUID bundleId,
                                         final String bundleExternalKey,
                                         final String status,
                                         final DateTime startDate,
                                         final DateTime endDate,
                                         final DateTime createdDate,
                                         final String createdBy,
                                         final String createdReasonCode,
                                         final String createdComments,
                                         final UUID accountId,
                                         final String accountName,
                                         final String accountExternalKey,
                                         final Long accountRecordId,
                                         final Long tenantRecordId) {
        super(createdDate,
              createdBy,
              createdReasonCode,
              createdComments,
              accountId,
              accountName,
              accountExternalKey,
              accountRecordId,
              tenantRecordId);
        this.blockingStateRecordId = blockingStateRecordId;
        this.bundleId = bundleId;
        this.bundleExternalKey = bundleExternalKey;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public BusinessOverdueStatusModelDao(final Account account,
                                         final Long accountRecordId,
                                         final SubscriptionBundle subscriptionBundle,
                                         final BlockingState blockingState,
                                         final Long blockingStateRecordId,
                                         final DateTime endDate,
                                         final AuditLog creationAuditLog,
                                         final Long tenantRecordId) {
        this(blockingStateRecordId,
             subscriptionBundle.getId(),
             subscriptionBundle.getExternalKey(),
             blockingState.getStateName(),
             blockingState.getTimestamp(),
             endDate,
             blockingState.getCreatedDate(),
             creationAuditLog.getUserName(),
             creationAuditLog.getReasonCode(),
             creationAuditLog.getComment(),
             account.getId(),
             account.getName(),
             account.getExternalKey(),
             accountRecordId,
             tenantRecordId);
    }

    @Override
    public String getTableName() {
        return OVERDUE_STATUS_TABLE_NAME;
    }

    public Long getBlockingStateRecordId() {
        return blockingStateRecordId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public String getBundleExternalKey() {
        return bundleExternalKey;
    }

    public String getStatus() {
        return status;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessOverdueStatusModelDao");
        sb.append("{blockingStateRecordId=").append(blockingStateRecordId);
        sb.append(", bundleId=").append(bundleId);
        sb.append(", bundleExternalKey='").append(bundleExternalKey).append('\'');
        sb.append(", status='").append(status).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
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
        if (!super.equals(o)) {
            return false;
        }

        final BusinessOverdueStatusModelDao that = (BusinessOverdueStatusModelDao) o;

        if (blockingStateRecordId != null ? !blockingStateRecordId.equals(that.blockingStateRecordId) : that.blockingStateRecordId != null) {
            return false;
        }
        if (bundleExternalKey != null ? !bundleExternalKey.equals(that.bundleExternalKey) : that.bundleExternalKey != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
            return false;
        }
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) {
            return false;
        }
        if (status != null ? !status.equals(that.status) : that.status != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (blockingStateRecordId != null ? blockingStateRecordId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (bundleExternalKey != null ? bundleExternalKey.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        return result;
    }
}
