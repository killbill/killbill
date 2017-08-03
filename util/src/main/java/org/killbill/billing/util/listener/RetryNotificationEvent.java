/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.listener;

import org.joda.time.DateTime;
import org.killbill.notificationq.api.NotificationEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RetryNotificationEvent implements NotificationEvent {

    private final String originalEvent;
    private final Class originalEventClass;
    private final DateTime originalEffectiveDate;
    private final int retryNb;

    @JsonCreator
    public RetryNotificationEvent(@JsonProperty("originalEvent") final String originalEvent,
                                  @JsonProperty("originalEventClass") final Class originalEventClass,
                                  @JsonProperty("originalEffectiveDate")  final DateTime originalEffectiveDate,
                                  @JsonProperty("retryNb") final int retryNb) {
        this.originalEvent = originalEvent;
        this.originalEventClass = originalEventClass;
        this.originalEffectiveDate = originalEffectiveDate;
        this.retryNb = retryNb;
    }

    public String getOriginalEvent() {
        return originalEvent;
    }

    public Class getOriginalEventClass() {
        return originalEventClass;
    }

    public DateTime getOriginalEffectiveDate() {
        return originalEffectiveDate;
    }

    public int getRetryNb() {
        return retryNb;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RetryNotificationEvent{");
        sb.append("originalEvent=").append(originalEvent);
        sb.append(", originalEventClass=").append(originalEventClass);
        sb.append(", originalEffectiveDate=").append(originalEffectiveDate);
        sb.append(", retryNb=").append(retryNb);
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

        final RetryNotificationEvent that = (RetryNotificationEvent) o;

        if (retryNb != that.retryNb) {
            return false;
        }
        if (originalEvent != null ? !originalEvent.equals(that.originalEvent) : that.originalEvent != null) {
            return false;
        }
        if (originalEventClass != null ? !originalEventClass.equals(that.originalEventClass) : that.originalEventClass != null) {
            return false;
        }
        return originalEffectiveDate != null ? originalEffectiveDate.compareTo(that.originalEffectiveDate) == 0 : that.originalEffectiveDate == null;
    }

    @Override
    public int hashCode() {
        int result = originalEvent != null ? originalEvent.hashCode() : 0;
        result = 31 * result + (originalEventClass != null ? originalEventClass.hashCode() : 0);
        result = 31 * result + (originalEffectiveDate != null ? originalEffectiveDate.hashCode() : 0);
        result = 31 * result + retryNb;
        return result;
    }
}
