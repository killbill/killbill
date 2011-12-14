package com.ning.billing.invoice.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ning.billing.catalog.api.Currency;

public class MockInvoicePaymentApi implements InvoicePaymentApi
{
    private final CopyOnWriteArrayList<Invoice> invoices = new CopyOnWriteArrayList<Invoice>();

    public void add(Invoice invoice) {
        invoices.add(invoice);
    }

    @Override
    public void paymentSuccessful(UUID invoiceId, BigDecimal amount, Currency currency, UUID paymentId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Invoice> getInvoicesByAccount(UUID accountId) {
        ArrayList<Invoice> result = new ArrayList<Invoice>();

        for (Invoice invoice : invoices) {
            if (accountId.equals(invoice.getAccountId())) {
                result.add(invoice);
            }
        }
        return result;
    }

    @Override
    public Invoice getInvoice(UUID invoiceId) {
        for (Invoice invoice : invoices) {
            if (invoiceId.equals(invoice.getId())) {
                return invoice;
            }
        }
        return null;
    }
}
