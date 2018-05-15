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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.DefaultInvoice;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class InvoiceWithMetadata {

    private final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates;

    private DefaultInvoice invoice;

    public InvoiceWithMetadata(final DefaultInvoice originalInvoice, final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates) {
        this.invoice = originalInvoice;
        this.perSubscriptionFutureNotificationDates = perSubscriptionFutureNotificationDates;
        build();
        removeMarkerUsageItems();
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

    protected void removeMarkerUsageItems() {
        if (invoice != null) {
            final Iterator<InvoiceItem> it = invoice.getInvoiceItems().iterator();
            while (it.hasNext()) {
                final InvoiceItem item = it.next();
                if (isMarkerUsageItem(item)) {
                    it.remove();
                }
            }
            if (invoice.getInvoiceItems().isEmpty()) {
                invoice = null;
            }
        }
    }

    //
    // $0 Usage item with no detail section
    // ($0 Usage item *with* detail section are valid items in case of tiers defined with $0 amount)
    //
    private boolean isMarkerUsageItem(final InvoiceItem item) {
        return item.getInvoiceItemType() == InvoiceItemType.USAGE &&
               item.getItemDetails() == null &&
               item.getAmount().compareTo(BigDecimal.ZERO) == 0;
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
