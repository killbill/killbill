package com.ning.billing.payment;

import java.util.UUID;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.invoice.model.Invoice;
import com.ning.billing.util.eventbus.IEventBus;
import com.ning.billing.util.eventbus.IEventBus.EventBusException;

public class MockInvoiceProcessor implements InvoiceProcessor {
    private final IEventBus eventBus;

    @Inject
    public MockInvoiceProcessor(IEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    @Subscribe
    public void receiveInvoice(Invoice invoice) throws EventBusException {
        eventBus.post(new PaymentInfo(UUID.randomUUID()));
    }
}
