/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.analytics.model;

import java.util.UUID;

import org.joda.time.DateTime;

/**
 * Describe a state change between two BusinessSubscription
 * <p/>
 * The key is unique identifier that ties sets of subscriptions together.
 */
public class BusinessSubscriptionTransition {
    private final UUID subscriptionId;
    private final long totalOrdering;
    private final String externalKey;
    private final String accountKey;
    private final DateTime requestedTimestamp;
    private final BusinessSubscriptionEvent event;
    private final BusinessSubscription previousSubscription;
    private final BusinessSubscription nextSubscription;

    public BusinessSubscriptionTransition(final UUID subscriptionId, final Long totalOrdering, final String externalKey, final String accountKey, final DateTime requestedTimestamp,
                                          final BusinessSubscriptionEvent event, final BusinessSubscription previousSubscription, final BusinessSubscription nextSubscription) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("An event must have a subscription id");
        }
        if (totalOrdering == null) {
            throw new IllegalArgumentException("An event must have a total ordering");
        }
        if (externalKey == null) {
            throw new IllegalArgumentException("An event must have an external key");
        }
        if (accountKey == null) {
            throw new IllegalArgumentException("An event must have an account key");
        }
        if (requestedTimestamp == null) {
            throw new IllegalArgumentException("An event must have a requestedTimestamp");
        }
        if (event == null) {
            throw new IllegalArgumentException("No event specified");
        }

        this.subscriptionId = subscriptionId;
        this.totalOrdering = totalOrdering;
        this.externalKey = externalKey;
        this.accountKey = accountKey;
        this.requestedTimestamp = requestedTimestamp;
        this.event = event;
        this.previousSubscription = previousSubscription;
        this.nextSubscription = nextSubscription;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public long getTotalOrdering() {
        return totalOrdering;
    }

    public BusinessSubscriptionEvent getEvent() {
        return event;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public BusinessSubscription getNextSubscription() {
        return nextSubscription;
    }

    public BusinessSubscription getPreviousSubscription() {
        return previousSubscription;
    }

    public DateTime getRequestedTimestamp() {
        return requestedTimestamp;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessSubscriptionTransition");
        sb.append("{accountKey='").append(accountKey).append('\'');
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", totalOrdering=").append(totalOrdering);
        sb.append(", key='").append(externalKey).append('\'');
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

        final BusinessSubscriptionTransition that = (BusinessSubscriptionTransition) o;

        if (accountKey != null ? !accountKey.equals(that.accountKey) : that.accountKey != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (event != null ? !event.equals(that.event) : that.event != null) {
            return false;
        }
        if (totalOrdering != that.totalOrdering) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
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

        return true;
    }

    @Override
    public int hashCode() {
        int result = (int) (totalOrdering ^ (totalOrdering >>> 32));
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (accountKey != null ? accountKey.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (requestedTimestamp != null ? requestedTimestamp.hashCode() : 0);
        result = 31 * result + (event != null ? event.hashCode() : 0);
        result = 31 * result + (previousSubscription != null ? previousSubscription.hashCode() : 0);
        result = 31 * result + (nextSubscription != null ? nextSubscription.hashCode() : 0);
        return result;
    }
}
