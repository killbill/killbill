package com.ning.billing.invoice.api.migration;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.model.DefaultInvoice;
import org.joda.time.DateTime;

import java.util.UUID;

public class MigrationInvoice extends DefaultInvoice {
    public MigrationInvoice(UUID accountId, DateTime invoiceDate, DateTime targetDate, Currency currency) {
        super(UUID.randomUUID(), accountId, null, invoiceDate, targetDate, currency, true, null, null);
    }
}
