/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.invoice.notification;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.notificationq.DefaultUUIDNotificationKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class NextBillingDateNotificationKey extends DefaultUUIDNotificationKey {

    private final Boolean isDryRunForInvoiceNotification;
    private final Boolean isRescheduled;
    private final DateTime targetDate;
    private final Iterable<UUID> uuidKeys;

    @JsonCreator
    public NextBillingDateNotificationKey(@Deprecated @JsonProperty("uuidKey") final UUID uuidKey,
                                          @JsonProperty("uuidKeys") final Iterable<UUID> uuidKeys,
                                          @JsonProperty("targetDate") final DateTime targetDate,
                                          @JsonProperty("isDryRunForInvoiceNotification") final Boolean isDryRunForInvoiceNotification,
                                          @JsonProperty("isRescheduled") final Boolean isRescheduled) {
        super(uuidKey);
        this.uuidKeys = uuidKeys;
        this.targetDate = targetDate;
        this.isDryRunForInvoiceNotification = isDryRunForInvoiceNotification;
        this.isRescheduled = isRescheduled;
    }

    public NextBillingDateNotificationKey(final NextBillingDateNotificationKey existing,
                                          final Iterable<UUID> newUUIDKeys) {
        super(null);
        this.uuidKeys = ImmutableSet.copyOf(Iterables.concat(existing.getUuidKeys(), newUUIDKeys));
        this.targetDate = existing.getTargetDate();
        this.isDryRunForInvoiceNotification = existing.isDryRunForInvoiceNotification();
        this.isRescheduled = existing.isRescheduled();
    }

    @JsonProperty("isDryRunForInvoiceNotification")
    public Boolean isDryRunForInvoiceNotification() {
        return isDryRunForInvoiceNotification;
    }

    @JsonProperty("isRescheduled")
    public Boolean isRescheduled() {
        return isRescheduled;
    }

    public DateTime getTargetDate() {
        return targetDate;
    }

    public final Iterable<UUID> getUuidKeys() {
        // Deprecated mode
        if (uuidKeys == null || !uuidKeys.iterator().hasNext()) {
            return ImmutableList.of(getUuidKey());
        } else {
            return uuidKeys;
        }
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("NextBillingDateNotificationKey{");
        sb.append("isDryRunForInvoiceNotification=").append(isDryRunForInvoiceNotification);
        sb.append(", isRescheduled=").append(isRescheduled);
        sb.append(", targetDate=").append(targetDate);
        sb.append(", uuidKeys=").append(uuidKeys);
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

        final NextBillingDateNotificationKey that = (NextBillingDateNotificationKey) o;

        if (isDryRunForInvoiceNotification != null ? !isDryRunForInvoiceNotification.equals(that.isDryRunForInvoiceNotification) : that.isDryRunForInvoiceNotification != null) {
            return false;
        }
        if (isRescheduled != null ? !isRescheduled.equals(that.isRescheduled) : that.isRescheduled != null) {
            return false;
        }
        if (targetDate != null ? targetDate.compareTo(that.targetDate) != 0 : that.targetDate != null) {
            return false;
        }
        return uuidKeys != null ? uuidKeys.equals(that.uuidKeys) : that.uuidKeys == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (isDryRunForInvoiceNotification != null ? isDryRunForInvoiceNotification.hashCode() : 0);
        result = 31 * result + (isRescheduled != null ? isRescheduled.hashCode() : 0);
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (uuidKeys != null ? uuidKeys.hashCode() : 0);
        return result;
    }
}
