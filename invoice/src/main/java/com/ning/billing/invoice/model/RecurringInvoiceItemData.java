package com.ning.billing.invoice.model;

import org.joda.time.DateTime;

import java.math.BigDecimal;

public class RecurringInvoiceItemData {
    private final DateTime startDate;
    private final DateTime endDate;
    private final BigDecimal numberOfCycles;

    public RecurringInvoiceItemData(DateTime startDate, DateTime endDate, BigDecimal numberOfCycles) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.numberOfCycles = numberOfCycles;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    public BigDecimal getNumberOfCycles() {
        return numberOfCycles;
    }
}
