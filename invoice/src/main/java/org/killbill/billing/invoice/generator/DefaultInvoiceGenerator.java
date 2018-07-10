/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.SubscriptionFutureNotificationDates;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.clock.Clock;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultInvoiceGenerator implements InvoiceGenerator {

    private final Clock clock;
    private final InvoiceConfig config;

    private final FixedAndRecurringInvoiceItemGenerator recurringInvoiceItemGenerator;
    private final UsageInvoiceItemGenerator usageInvoiceItemGenerator;

    @Inject
    public DefaultInvoiceGenerator(final Clock clock, final InvoiceConfig config, final FixedAndRecurringInvoiceItemGenerator recurringInvoiceItemGenerator, final UsageInvoiceItemGenerator usageInvoiceItemGenerator) {
        this.clock = clock;
        this.config = config;
        this.recurringInvoiceItemGenerator = recurringInvoiceItemGenerator;
        this.usageInvoiceItemGenerator = usageInvoiceItemGenerator;
    }

    /*
     * adjusts target date to the maximum invoice target date, if future invoices exist
     */
    @Override
    public InvoiceWithMetadata generateInvoice(final ImmutableAccountData account,
                                               @Nullable final BillingEventSet events,
                                               @Nullable final Iterable<Invoice> existingInvoices,
                                               @Nullable final UUID targetInvoiceId,
                                               final LocalDate targetDate,
                                               final Currency targetCurrency,
                                               final InternalCallContext context) throws InvoiceApiException {
        if ((events == null) || (events.size() == 0) || events.isAccountAutoInvoiceOff()) {
            return new InvoiceWithMetadata(null, ImmutableMap.<UUID, SubscriptionFutureNotificationDates>of());
        }

        validateTargetDate(targetDate, context);
        final LocalDate adjustedTargetDate = adjustTargetDate(existingInvoices, targetDate);

        final LocalDate invoiceDate = context.toLocalDate(context.getCreatedDate());
        final InvoiceStatus invoiceStatus = events.isAccountAutoInvoiceDraft() ? InvoiceStatus.DRAFT : InvoiceStatus.COMMITTED;
        final DefaultInvoice invoice = targetInvoiceId != null ?
                                       new DefaultInvoice(targetInvoiceId, account.getId(), null, invoiceDate, adjustedTargetDate, targetCurrency, false, invoiceStatus) :
                                       new DefaultInvoice(account.getId(), invoiceDate, adjustedTargetDate, targetCurrency, invoiceStatus);

        final Map<UUID, SubscriptionFutureNotificationDates> perSubscriptionFutureNotificationDates = new HashMap<UUID, SubscriptionFutureNotificationDates>();

        final List<InvoiceItem> fixedAndRecurringItems = recurringInvoiceItemGenerator.generateItems(account, invoice.getId(), events, existingInvoices, adjustedTargetDate, targetCurrency, perSubscriptionFutureNotificationDates, context);
        invoice.addInvoiceItems(fixedAndRecurringItems);

        final List<InvoiceItem> usageItems = usageInvoiceItemGenerator.generateItems(account, invoice.getId(), events, existingInvoices, adjustedTargetDate, targetCurrency, perSubscriptionFutureNotificationDates, context);
        invoice.addInvoiceItems(usageItems);

        if (targetInvoiceId != null) {
            final Invoice originalInvoice = Iterables.tryFind(existingInvoices, new Predicate<Invoice>() {
                @Override
                public boolean apply(final Invoice input) {
                    return input.getId().equals(targetInvoiceId);
                }
            }).orNull();
            Preconditions.checkNotNull(originalInvoice, "Expecting to find an existing invoice matching the targetInvoiceId");
            invoice.addInvoiceItems(originalInvoice.getInvoiceItems());
        }

        return new InvoiceWithMetadata(invoice.getInvoiceItems().isEmpty() ? null : invoice, perSubscriptionFutureNotificationDates);
    }

    private void validateTargetDate(final LocalDate targetDate, final InternalTenantContext context) throws InvoiceApiException {
        final int maximumNumberOfMonths = config.getNumberOfMonthsInFuture(context);

        if (Months.monthsBetween(clock.getUTCToday(), targetDate).getMonths() > maximumNumberOfMonths) {
            throw new InvoiceApiException(ErrorCode.INVOICE_TARGET_DATE_TOO_FAR_IN_THE_FUTURE, targetDate.toString());
        }
    }

    private LocalDate adjustTargetDate(final Iterable<Invoice> existingInvoices, final LocalDate targetDate) {
        if (existingInvoices == null) {
            return targetDate;
        }

        LocalDate maxDate = targetDate;

        for (final Invoice invoice : existingInvoices) {
            if ((invoice.getTargetDate() != null) && invoice.getTargetDate().isAfter(maxDate)) {
                maxDate = invoice.getTargetDate();
            }
        }
        return maxDate;
    }

}
