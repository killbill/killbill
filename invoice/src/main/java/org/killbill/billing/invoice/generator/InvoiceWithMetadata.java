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

package org.killbill.billing.invoice.generator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.api.Invoice;

public class InvoiceWithMetadata {

    private final Invoice invoice;
    private final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates;

    public static class SubscriptionFutureNotificationDates {

        private LocalDate nextRecurringDate;
        private Map<String, LocalDate> nextUsageDates;

        public SubscriptionFutureNotificationDates() {
            this.nextRecurringDate = null;
            this.nextUsageDates = null;
        }

        public void updateNextRecurringDateIfRequired(final LocalDate nextRecurringDateCandidate) {
            nextRecurringDate = getMaxDate(nextRecurringDate, nextRecurringDateCandidate);
        }

        public void updateNextUsageDateIfRequired(final String usageName, final LocalDate nextUsageDateCandidate) {
            if (nextUsageDates == null) {
                nextUsageDates = new HashMap<String, LocalDate>();
            }
            final LocalDate nextUsageDate = getMaxDate(nextUsageDates.get(usageName), nextUsageDateCandidate);
            nextUsageDates.put(usageName, nextUsageDate);
        }

        public LocalDate getNextRecurringDate() {
            return nextRecurringDate;
        }

        public Map<String, LocalDate> getNextUsageDates() {
            return nextUsageDates;
        }

        private static LocalDate getMaxDate(@Nullable final LocalDate existingDate, final LocalDate nextDateCandidate) {
            if (existingDate == null) {
                return nextDateCandidate;
            } else {
                return nextDateCandidate.compareTo(existingDate) > 0 ? nextDateCandidate : existingDate;
            }
        }

    }

    public InvoiceWithMetadata(final Invoice invoice, final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates) {
        this.invoice = invoice;
        this.perSubscriptionFutureNotificationDates = perSubscriptionFutureNotificationDates;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public Map<UUID, SubscriptionFutureNotificationDates> getPerSubscriptionFutureNotificationDates() {
        return perSubscriptionFutureNotificationDates;
    }

}
