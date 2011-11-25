package com.ning.billing.payment;

import com.ning.billing.invoice.model.Invoice;
import com.ning.billing.util.eventbus.IEventBus.EventBusException;

public interface InvoiceProcessor {
    public void receiveInvoice(Invoice invoice) throws EventBusException;
}
