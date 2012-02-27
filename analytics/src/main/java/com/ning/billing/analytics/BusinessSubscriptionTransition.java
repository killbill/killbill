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

package com.ning.billing.analytics;

import org.joda.time.DateTime;

import java.util.UUID;

/**
 * Describe a state change between two BusinessSubscription
 * <p/>
 * The key is unique identifier that ties sets of subscriptions together.
 */
public class BusinessSubscriptionTransition
{
    private final UUID id;
    private final String key;
    private final String accountKey;
    private final DateTime requestedTimestamp;
    private final BusinessSubscriptionEvent event;
    private final BusinessSubscription previousSubscription;
    private final BusinessSubscription nextSubscription;

    public BusinessSubscriptionTransition(final UUID id, final String key, final String accountKey, final DateTime requestedTimestamp, final BusinessSubscriptionEvent event, final BusinessSubscription previousSubscription, final BusinessSubscription nextsubscription)
    {
        if (id == null) {
            throw new IllegalArgumentException("An event must have an id");
        }
        if (key == null) {
            throw new IllegalArgumentException("An event must have an key");
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

        this.id = id;
        this.key = key;
        this.accountKey = accountKey;
        this.requestedTimestamp = requestedTimestamp;
        this.event = event;
        this.previousSubscription = previousSubscription;
        this.nextSubscription = nextsubscription;
    }

    public UUID getId()
    {
        return id;
    }

    public BusinessSubscriptionEvent getEvent()
    {
        return event;
    }

    public String getKey()
    {
        return key;
    }

    public String getAccountKey()
    {
        return accountKey;
    }

    public BusinessSubscription getNextSubscription()
    {
        return nextSubscription;
    }

    public BusinessSubscription getPreviousSubscription()
    {
        return previousSubscription;
    }

    public DateTime getRequestedTimestamp()
    {
        return requestedTimestamp;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessSubscriptionTransition");
        sb.append("{accountKey='").append(accountKey).append('\'');
        sb.append(", id=").append(id);
        sb.append(", key='").append(key).append('\'');
        sb.append(", requestedTimestamp=").append(requestedTimestamp);
        sb.append(", event=").append(event);
        sb.append(", previousSubscription=").append(previousSubscription);
        sb.append(", nextSubscription=").append(nextSubscription);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o)
    {
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
        if (event != null ? !event.equals(that.event) : that.event != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (key != null ? !key.equals(that.key) : that.key != null) {
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
    public int hashCode()
    {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + (accountKey != null ? accountKey.hashCode() : 0);
        result = 31 * result + (requestedTimestamp != null ? requestedTimestamp.hashCode() : 0);
        result = 31 * result + (event != null ? event.hashCode() : 0);
        result = 31 * result + (previousSubscription != null ? previousSubscription.hashCode() : 0);
        result = 31 * result + (nextSubscription != null ? nextSubscription.hashCode() : 0);
        return result;
    }
}
