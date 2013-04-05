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
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.util.audit.AuditLog;

/**
 * Describe a state change between two BusinessSubscription
 */
public class BusinessSubscriptionTransitionModelDao extends BusinessModelDaoBase {

    private static final String SUBSCRIPTION_TABLE_NAME = "bst";

    private Long subscriptionEventRecordId;
    private UUID bundleId;
    private String bundleExternalKey;
    private UUID subscriptionId;
    private DateTime requestedTimestamp;
    private BusinessSubscriptionEvent event;
    private BusinessSubscription previousSubscription;
    private BusinessSubscription nextSubscription;

    public BusinessSubscriptionTransitionModelDao() { /* When reading from the database */ }

    public BusinessSubscriptionTransitionModelDao(final Long subscriptionEventRecordId,
                                                  final UUID bundleId,
                                                  final String bundleExternalKey,
                                                  final UUID subscriptionId,
                                                  final DateTime requestedTimestamp,
                                                  final BusinessSubscriptionEvent event,
                                                  final BusinessSubscription previousSubscription,
                                                  final BusinessSubscription nextSubscription,
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
        this.subscriptionEventRecordId = subscriptionEventRecordId;
        this.bundleId = bundleId;
        this.bundleExternalKey = bundleExternalKey;
        this.subscriptionId = subscriptionId;
        this.requestedTimestamp = requestedTimestamp;
        this.event = event;
        this.previousSubscription = previousSubscription;
        this.nextSubscription = nextSubscription;
    }

    public BusinessSubscriptionTransitionModelDao(final Account account,
                                                  final Long accountRecordId,
                                                  final SubscriptionBundle bundle,
                                                  final SubscriptionTransition transition,
                                                  final Long subscriptionEventRecordId,
                                                  final DateTime requestedTimestamp,
                                                  final BusinessSubscriptionEvent event,
                                                  final BusinessSubscription previousSubscription,
                                                  final BusinessSubscription nextSubscription,
                                                  final AuditLog creationAuditLog,
                                                  final Long tenantRecordId) {
        this(subscriptionEventRecordId,
             bundle.getId(),
             bundle.getExternalKey(),
             transition.getSubscriptionId(),
             requestedTimestamp,
             event,
             previousSubscription,
             nextSubscription,
             transition.getNextEventCreatedDate(),
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
        return SUBSCRIPTION_TABLE_NAME;
    }

    public Long getSubscriptionEventRecordId() {
        return subscriptionEventRecordId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public String getBundleExternalKey() {
        return bundleExternalKey;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public DateTime getRequestedTimestamp() {
        return requestedTimestamp;
    }

    public BusinessSubscriptionEvent getEvent() {
        return event;
    }

    public BusinessSubscription getPreviousSubscription() {
        return previousSubscription;
    }

    public BusinessSubscription getNextSubscription() {
        return nextSubscription;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessSubscriptionTransitionModelDao");
        sb.append("{subscriptionEventRecordId=").append(subscriptionEventRecordId);
        sb.append(", bundleId=").append(bundleId);
        sb.append(", bundleExternalKey='").append(bundleExternalKey).append('\'');
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", requestedTimestamp=").append(requestedTimestamp);
        sb.append(", event=").append(event);
        sb.append(", previousSubscription=").append(previousSubscription);
        sb.append(", nextSubscription=").append(nextSubscription);
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

        final BusinessSubscriptionTransitionModelDao that = (BusinessSubscriptionTransitionModelDao) o;

        if (bundleExternalKey != null ? !bundleExternalKey.equals(that.bundleExternalKey) : that.bundleExternalKey != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (event != null ? !event.equals(that.event) : that.event != null) {
            return false;
        }
        if (nextSubscription != null ? !nextSubscription.equals(that.nextSubscription) : that.nextSubscription != null) {
            return false;
        }
        if (previousSubscription != null ? !previousSubscription.equals(that.previousSubscription) : that.previousSubscription != null) {
            return false;
        }
        if (requestedTimestamp != null ? !requestedTimestamp.equals(that.requestedTimestamp) : that.requestedTimestamp != null) {
            return false;
        }
        if (subscriptionEventRecordId != null ? !subscriptionEventRecordId.equals(that.subscriptionEventRecordId) : that.subscriptionEventRecordId != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (subscriptionEventRecordId != null ? subscriptionEventRecordId.hashCode() : 0);
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (bundleExternalKey != null ? bundleExternalKey.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (requestedTimestamp != null ? requestedTimestamp.hashCode() : 0);
        result = 31 * result + (event != null ? event.hashCode() : 0);
        result = 31 * result + (previousSubscription != null ? previousSubscription.hashCode() : 0);
        result = 31 * result + (nextSubscription != null ? nextSubscription.hashCode() : 0);
        return result;
    }
}
