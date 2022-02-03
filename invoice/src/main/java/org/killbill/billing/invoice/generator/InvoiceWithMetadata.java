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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.model.DefaultInvoice;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class InvoiceWithMetadata {

    private final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates;

    private DefaultInvoice invoice;
    private final Set<TrackingRecordId> trackingIds;
    private final Map<DateTime, List<UUID>> chargedThroughDates;
    private final boolean filterZeroUsageItems;

    public InvoiceWithMetadata(final DefaultInvoice originalInvoice,
                               final Set<TrackingRecordId> trackingIds,
                               final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates,
                               final boolean filterZeroUsageItems,
                               final InternalCallContext context) {
        this.filterZeroUsageItems = filterZeroUsageItems;
        this.invoice = originalInvoice;
        this.chargedThroughDates = computeChargedThroughDates(originalInvoice, context);
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
        // we need to filter out the dates for which there is no item left otherwise we may end up in creating too many notification dates
        // and in particular that could lead to an infinite loop.
        for (final Entry<UUID, SubscriptionFutureNotificationDates> entry : perSubscriptionFutureNotificationDates.entrySet()) {
            final SubscriptionFutureNotificationDates tmp = entry.getValue();
            if (tmp.getNextRecurringDate() != null &&
                tmp.getRecurringBillingMode() == BillingMode.IN_ADVANCE &&
                !hasItemsForSubscription(entry.getKey(), InvoiceItemType.RECURRING) &&
                !hasItemsForDate(tmp.getNextRecurringDate()) /* e.g. REPAIR_ADJ */) {
                tmp.resetNextRecurringDate();
            }
        }


        if (invoice != null ) {
            // Filter $0 USAGE items if specified by config
            if (filterZeroUsageItems) {
                final Iterable<InvoiceItem> resultingItems = Iterables.filter(invoice.getInvoiceItems(), new Predicate<InvoiceItem>() {
                    @Override
                    public boolean apply(final InvoiceItem invoiceItem) {
                        return invoiceItem.getInvoiceItemType() != InvoiceItemType.USAGE ||
                               invoiceItem.getAmount().compareTo(BigDecimal.ZERO) != 0 ||
                               (invoiceItem.getQuantity() != null &&  invoiceItem.getQuantity() > 0);
                    }
                });
                final ImmutableList<InvoiceItem> filteredItems = ImmutableList.copyOf(resultingItems);
                // Reset invoice items with filtered list
                invoice.getInvoiceItems().clear();
                invoice.addInvoiceItems(filteredItems);
            }

            // If no resulting items -> result a null invoice
            // (but we already computed all the stuff we care about: chargedThroughDates, trackingIds,..
            if (Iterables.isEmpty(invoice.getInvoiceItems())) {
                invoice = null;
            }
        }
    }


    public static Map<DateTime, List<UUID>> computeChargedThroughDates(final Invoice invoice, final InternalCallContext context)  {
        final Map<UUID, DateTime> chargedThroughDates;
        if (invoice != null &&
            invoice.getStatus() == InvoiceStatus.COMMITTED) /* See https://github.com/killbill/killbill/issues/1296 */ {
            chargedThroughDates = new HashMap<>();
                // Don't use invoice.getInvoiceItems(final Class<T> clazz) as some items can come from plugins
                for (final InvoiceItem item : invoice.getInvoiceItems()) {
                if (item.getInvoiceItemType() != InvoiceItemType.FIXED &&
                    item.getInvoiceItemType() != InvoiceItemType.RECURRING &&
                    item.getInvoiceItemType() != InvoiceItemType.USAGE) {
                    continue;
                }
                final UUID subscriptionId = item.getSubscriptionId();
                final LocalDate endDate = (item.getEndDate() != null) ? item.getEndDate() : item.getStartDate();

                final DateTime proposedChargedThroughDate = context.toUTCDateTime(endDate);
                if (chargedThroughDates.containsKey(subscriptionId)) {
                    if (chargedThroughDates.get(subscriptionId).isBefore(proposedChargedThroughDate)) {
                        chargedThroughDates.put(subscriptionId, proposedChargedThroughDate);
                    }
                } else {
                    chargedThroughDates.put(subscriptionId, proposedChargedThroughDate);
                }
            }
        } else {
            chargedThroughDates = ImmutableMap.of();
        }

        final Map<DateTime, List<UUID>> perDateCTDs = new HashMap<>();
        for (final Map.Entry<UUID,DateTime>  kv : chargedThroughDates.entrySet()) {
            if (kv.getValue() != null) {
                if (!perDateCTDs.containsKey(kv.getValue())) {
                    perDateCTDs.put(kv.getValue(), new ArrayList<>());
                }
                perDateCTDs.get(kv.getValue()).add(kv.getKey());
            }
        }
        return perDateCTDs;
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

    private boolean hasItemsForDate(final LocalDate date) {
        return invoice != null && Iterables.any(invoice.getInvoiceItems(), new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return (input.getStartDate() != null && input.getStartDate().compareTo(date) == 0) ||
                       (input.getEndDate() != null && input.getEndDate().compareTo(date) == 0);
            }
        });
    }

    public Set<TrackingRecordId> getTrackingIds() {
        return trackingIds;
    }

    public Map<DateTime, List<UUID>>  getChargeThroughDates() {
        return chargedThroughDates;
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

        public void clearNextRecurringDate() {
            nextRecurringDate = null;
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
