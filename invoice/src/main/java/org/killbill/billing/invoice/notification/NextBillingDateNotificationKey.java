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

    private Boolean isDryRunForInvoiceNotification;
    private DateTime targetDate;
    private final Iterable<UUID> uuidKeys;

    @JsonCreator
    public NextBillingDateNotificationKey(@Deprecated @JsonProperty("uuidKey") final UUID uuidKey,
                                          @JsonProperty("uuidKeys") final Iterable<UUID> uuidKeys,
                                          @JsonProperty("targetDate") final DateTime targetDate,
                                          @JsonProperty("isDryRunForInvoiceNotification") final Boolean isDryRunForInvoiceNotification) {
        super(uuidKey);
        this.uuidKeys = uuidKeys;
        this.targetDate = targetDate;
        this.isDryRunForInvoiceNotification = isDryRunForInvoiceNotification;
    }

    public NextBillingDateNotificationKey(NextBillingDateNotificationKey existing,
                                          final Iterable<UUID> newUUIDKeys) {
        super(null);
        this.uuidKeys = ImmutableSet.copyOf(Iterables.concat(existing.getUuidKeys(), newUUIDKeys));
        this.targetDate = existing.getTargetDate();
        this.isDryRunForInvoiceNotification = existing.isDryRunForInvoiceNotification();
    }

    @JsonProperty("isDryRunForInvoiceNotification")
    public Boolean isDryRunForInvoiceNotification() {
        return isDryRunForInvoiceNotification;
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
}
