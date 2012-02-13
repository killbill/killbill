package com.ning.billing.invoice.model;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.UUID;

public abstract class InvoiceItemBase implements InvoiceItem {
    protected final UUID id;
    protected final UUID invoiceId;
    protected final UUID subscriptionId;
    protected final String planName;
    protected final String phaseName;
    protected final DateTime startDate;
    protected final DateTime endDate;
    protected final BigDecimal amount;
    protected final Currency currency;

    public InvoiceItemBase(UUID invoiceId, UUID subscriptionId, String planName, String phaseName,
                           DateTime startDate, DateTime endDate, BigDecimal amount, Currency currency) {
        this(UUID.randomUUID(), invoiceId, subscriptionId, planName, phaseName,
             startDate, endDate, amount, currency);
    }

    public InvoiceItemBase(UUID id, UUID invoiceId, UUID subscriptionId, String planName, String phaseName,
                           DateTime startDate, DateTime endDate, BigDecimal amount, Currency currency) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.subscriptionId = subscriptionId;
        this.planName = planName;
        this.phaseName = phaseName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.amount = amount;
        this.currency = currency;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public String getPlanName() {
        return planName;
    }

    @Override
    public String getPhaseName() {
        return phaseName;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public DateTime getStartDate() {
        return startDate;
    }

    @Override
    public DateTime getEndDate() {
        return endDate;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public abstract InvoiceItem asCredit();

    @Override
    public abstract String getDescription();

    @Override
    public abstract int compareTo(InvoiceItem invoiceItem);
}
