/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.junction.BillingEventSet;
import org.slf4j.Logger;

public abstract class InvoiceItemGenerator {

    public abstract List<InvoiceItem> generateItems(final ImmutableAccountData account, final UUID invoiceId, final BillingEventSet eventSet,
                                                    @Nullable final Iterable<Invoice> existingInvoices, final LocalDate targetDate,
                                                    final Currency targetCurrency, Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDate,
                                                    final InternalCallContext context) throws InvoiceApiException;

    public static class InvoiceItemGeneratorLogger {

        private final UUID invoiceId;
        private final UUID accountId;
        private final String type;
        private final Logger delegate;
        private final boolean enabled;

        private StringBuilder logStringBuilder = null;

        public InvoiceItemGeneratorLogger(final UUID invoiceId, final UUID accountId, final String type, final Logger delegate) {
            this.invoiceId = invoiceId;
            this.accountId = accountId;
            this.type = type;
            this.delegate = delegate;
            this.enabled = delegate.isDebugEnabled();
        }

        public void append(final Object event, final Collection<InvoiceItem> items) {
            if (!enabled || items.isEmpty()) {
                return;
            }
            append(event, items.toArray(new InvoiceItem[items.size()]));
        }

        public void append(final Object event, final InvoiceItem... items) {
            if (!enabled || items.length == 0) {
                return;
            }

            getLogStringBuilder().append("\n")
                                 .append(event);

            for (final InvoiceItem item : items) {
                getLogStringBuilder().append("\n\t")
                                     .append(item);
            }
        }

        public void logItems() {
            if (enabled && logStringBuilder != null) {
                delegate.debug(getLogStringBuilder().toString());
            }
        }

        private StringBuilder getLogStringBuilder() {
            if (logStringBuilder == null) {
                logStringBuilder = new StringBuilder("Proposed ").append(type)
                                                                 .append(" items for invoiceId='")
                                                                 .append(invoiceId)
                                                                 .append("', accountId='")
                                                                 .append(accountId)
                                                                 .append("'");
            }

            return logStringBuilder;
        }
    }
}
