package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;

public class MockInvoiceDao implements InvoiceDao {
    private static final class InvoicePaymentInfo {
        public final String invoiceId;
        public final String paymentId;
        public final DateTime paymentDate;
        public final BigDecimal amount;
        public final Currency currency;

        public InvoicePaymentInfo(String invoiceId, String paymentId, DateTime paymentDate, BigDecimal amount, Currency currency) {
            this.invoiceId = invoiceId;
            this.paymentId = paymentId;
            this.paymentDate = paymentDate;
            this.amount = amount;
            this.currency = currency;
        }
    }

    private final Object monitor = new Object();
    private final Map<String, Invoice> invoices = new LinkedHashMap<String, Invoice>();
    private final List<InvoicePaymentInfo> paymentInfos = new ArrayList<MockInvoiceDao.InvoicePaymentInfo>();

    @Override
    public void save(Invoice invoice) {
        synchronized (monitor) {
            invoices.put(invoice.getId().toString(), invoice);
        }
    }

    private Invoice munge(Invoice invoice) {
        if (invoice == null) {
            return null;
        }

        DateTime lastPaymentDate = null;
        BigDecimal amountPaid = new BigDecimal("0");

        for (InvoicePaymentInfo info : paymentInfos) {
            if (info.invoiceId.equals(invoice.getId().toString())) {
                if (lastPaymentDate == null || lastPaymentDate.isBefore(info.paymentDate)) {
                    lastPaymentDate = info.paymentDate;
                }
                if (info.amount != null) {
                    amountPaid.add(info.amount);
                }
            }
        }
        return new DefaultInvoice(invoice.getId(),
                                  invoice.getAccountId(),
                                  invoice.getInvoiceDate(),
                                  invoice.getTargetDate(),
                                  invoice.getCurrency(),
                                  lastPaymentDate,
                                  amountPaid,
                                  invoice.getItems());
    }

    private List<Invoice> munge(Collection<Invoice> invoices) {
        List<Invoice> result = new ArrayList<Invoice>();
        for (Invoice invoice : invoices) {
            result.add(munge(invoice));
        }
        return result;
    }

    @Override
    public Invoice getById(String id) {
        synchronized (monitor) {
            return munge(invoices.get(id));
        }
    }

    @Override
    public List<Invoice> get() {
        synchronized (monitor) {
            return munge(invoices.values());
        }
    }

    @Override
    public List<Invoice> getInvoicesByAccount(String accountId) {
        List<Invoice> result = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (Invoice invoice : invoices.values()) {
                if (accountId.equals(invoice.getAccountId().toString())) {
                    result.add(invoice);
                }
            }
        }
        return munge(result);
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(String subscriptionId) {
        List<Invoice> result = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (Invoice invoice : invoices.values()) {
                for (InvoiceItem item : invoice.getItems()) {
                    if (subscriptionId.equals(item.getSubscriptionId().toString())) {
                        result.add(invoice);
                        break;
                    }
                }
            }
        }
        return munge(result);
    }

    @Override
    public List<UUID> getInvoicesForPayment(Date targetDate, int numberOfDays) {
        Set<UUID> result = new LinkedHashSet<UUID>();

        synchronized (monitor) {
            for (InvoicePaymentInfo info : paymentInfos) {
                Invoice invoice = invoices.get(info.invoiceId);
                if ((invoice != null) &&
                    (((info.paymentDate == null) || !info.paymentDate.plusDays(numberOfDays).isAfter(targetDate.getTime())) &&
                    (invoice.getTotalAmount() != null) && invoice.getTotalAmount().doubleValue() >= 0) &&
                    ((info.amount == null) || info.amount.doubleValue() >= invoice.getTotalAmount().doubleValue())) {

                    result.add(invoice.getId());
                }
            }
        }

        return new ArrayList<UUID>(result);
    }

    @Override
    public void notifySuccessfulPayment(String invoiceId,
                                        BigDecimal paymentAmount,
                                        String currency, String paymentId,
                                        Date paymentDate) {
        synchronized (monitor) {
            paymentInfos.add(new InvoicePaymentInfo(invoiceId, paymentId, new DateTime(paymentDate), paymentAmount, Currency.valueOf(currency)));
        }
    }

    @Override
    public void notifyFailedPayment(String invoiceId, String paymentId,
                                    Date paymentAttemptDate) {
        synchronized (monitor) {
            paymentInfos.add(new InvoicePaymentInfo(invoiceId, paymentId, new DateTime(paymentAttemptDate), null, null));
        }
    }

    @Override
    public void test() {
    }
}
