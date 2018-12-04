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
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.DefaultInvoice;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class InvoiceWithMetadata {

    private final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates;

    private DefaultInvoice invoice;

    private final Set<TrackingRecordId> trackingIds;

    public InvoiceWithMetadata(final DefaultInvoice originalInvoice, final Set<TrackingRecordId> trackingIds, final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates) {
        this.invoice = originalInvoice;
        this.perSubscriptionFutureNotificationDates = perSubscriptionFutureNotificationDates;
        this.trackingIds = trackingIds;
        build();
    }

    public DefaultInvoice getInvoice() {
        return invoice;
    }

    public Map<UUID, SubscriptionFutureNotificationDates> getPerSubscriptionFutureNotificationDates() {
        return perSubscriptionFutureNotificationDates;
    }

    // Remove all the IN_ADVANCE items for which we have no invoice items
    private void build() {
        // nextRecurringDate are computed based on *proposed* items, and not missing items (= proposed - existing). So
        // we need to filter out the dates for which there is no item left otherwsie we may end up in creating too many notification dates
        // and in particular that could lead to an infinite loop.
        for (final UUID subscriptionId : perSubscriptionFutureNotificationDates.keySet()) {
            final SubscriptionFutureNotificationDates tmp = perSubscriptionFutureNotificationDates.get(subscriptionId);
            if (tmp.getRecurringBillingMode() == BillingMode.IN_ADVANCE && !hasItemsForSubscription(subscriptionId, InvoiceItemType.RECURRING)) {
                tmp.resetNextRecurringDate();
            }
        }
        if (invoice != null && invoice.getInvoiceItems().isEmpty()) {
            invoice = null;
        }
    }

    private boolean hasItemsForSubscription(final UUID subscriptionId, final InvoiceItemType invoiceItemType) {
        return invoice != null && Iterables.any(invoice.getInvoiceItems(), new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getInvoiceItemType() == invoiceItemType &&
                       input.getSubscriptionId().equals(subscriptionId);
            }
        });
    }

    public Set<TrackingRecordId> getTrackingIds() {
        return trackingIds;
    }

    public static class TrackingRecordId {

        private final String trackingId;
        private final UUID invoiceId;
        private final UUID subscriptionId;
        private final String unitType;
        private final LocalDate recordDate;

        public TrackingRecordId(final String trackingId, final UUID invoiceId, final UUID subscriptionId, final String unitType, final LocalDate recordDate) {
            this.trackingId = trackingId;
            this.invoiceId = invoiceId;
            this.subscriptionId = subscriptionId;
            this.unitType = unitType;
            this.recordDate = recordDate;
        }

        public String getTrackingId() {
            return trackingId;
        }

        public UUID getInvoiceId() {
            return invoiceId;
        }

        public UUID getSubscriptionId() {
            return subscriptionId;
        }

        public LocalDate getRecordDate() {
            return recordDate;
        }

        public String getUnitType() {
            return unitType;
        }


        //
        // Two records are similar if they were issued from the same usage record {subscriptionId, trackingId, unitType, recordDate}
        // regardless on which 'invoice' they got attached to.
        //
        public boolean isSimilarRecord(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TrackingRecordId)) {
                return false;
            }
            final TrackingRecordId that = (TrackingRecordId) o;
            return Objects.equal(trackingId, that.trackingId) &&
                   Objects.equal(subscriptionId, that.subscriptionId) &&
                   Objects.equal(unitType, that.unitType) &&
                   Objects.equal(recordDate, that.recordDate);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TrackingRecordId)) {
                return false;
            }
            final TrackingRecordId that = (TrackingRecordId) o;
            return Objects.equal(trackingId, that.trackingId) &&
                   Objects.equal(invoiceId, that.invoiceId) &&
                   Objects.equal(subscriptionId, that.subscriptionId) &&
                   Objects.equal(unitType, that.unitType) &&
                   Objects.equal(recordDate, that.recordDate);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(trackingId, invoiceId, subscriptionId, unitType, recordDate);
        }
    }

    public static class SubscriptionFutureNotificationDates {

        private final BillingMode recurringBillingMode;

        private LocalDate nextRecurringDate;
        private Map<UsageDef, LocalDate> nextUsageDates;

        public SubscriptionFutureNotificationDates(final BillingMode recurringBillingMode) {
            this.recurringBillingMode = recurringBillingMode;
            this.nextRecurringDate = null;
            this.nextUsageDates = null;
        }

        public void updateNextRecurringDateIfRequired(final LocalDate nextRecurringDateCandidate) {
            if (nextRecurringDateCandidate != null) {
                nextRecurringDate = getMaxDate(nextRecurringDate, nextRecurringDateCandidate);
            }
        }

        public void updateNextUsageDateIfRequired(final String usageName, final BillingMode billingMode, final LocalDate nextUsageDateCandidate) {
            if (nextUsageDateCandidate != null) {
                if (nextUsageDates == null) {
                    nextUsageDates = new HashMap<UsageDef, LocalDate>();
                }
                final UsageDef usageDef = new UsageDef(usageName, billingMode);
                final LocalDate nextUsageDate = getMaxDate(nextUsageDates.get(usageDef), nextUsageDateCandidate);
                nextUsageDates.put(usageDef, nextUsageDate);
            }
        }

        public LocalDate getNextRecurringDate() {
            return nextRecurringDate;
        }

        public Map<UsageDef, LocalDate> getNextUsageDates() {
            return nextUsageDates;
        }

        public BillingMode getRecurringBillingMode() {
            return recurringBillingMode;
        }

        public void resetNextRecurringDate() {
            nextRecurringDate = null;
        }

        private static LocalDate getMaxDate(@Nullable final LocalDate existingDate, final LocalDate nextDateCandidate) {
            if (existingDate == null) {
                return nextDateCandidate;
            } else {
                return nextDateCandidate.compareTo(existingDate) > 0 ? nextDateCandidate : existingDate;
            }
        }

        public static class UsageDef {

            private final String usageName;
            private final BillingMode billingMode;

            public UsageDef(final String usageName, final BillingMode billingMode) {
                this.usageName = usageName;
                this.billingMode = billingMode;
            }

            public String getUsageName() {
                return usageName;
            }

            public BillingMode getBillingMode() {
                return billingMode;
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof UsageDef)) {
                    return false;
                }

                final UsageDef usageDef = (UsageDef) o;

                if (usageName != null ? !usageName.equals(usageDef.usageName) : usageDef.usageName != null) {
                    return false;
                }
                return billingMode == usageDef.billingMode;

            }

            @Override
            public int hashCode() {
                int result = usageName != null ? usageName.hashCode() : 0;
                result = 31 * result + (billingMode != null ? billingMode.hashCode() : 0);
                return result;
            }
        }
    }
}
