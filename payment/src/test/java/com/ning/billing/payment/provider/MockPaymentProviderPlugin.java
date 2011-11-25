package com.ning.billing.payment.provider;

import java.util.UUID;

import com.google.inject.Inject;
import com.ning.billing.invoice.model.Invoice;
import com.ning.billing.payment.PaymentInfo;
import com.ning.billing.util.eventbus.IEventBus;
import com.ning.billing.util.eventbus.IEventBus.EventBusException;

public class MockPaymentProviderPlugin implements PaymentProviderPlugin {
    private final IEventBus eventBus;

    @Inject
    public MockPaymentProviderPlugin(IEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void processInvoice(Invoice invoice) {
        try {
            eventBus.post(new PaymentInfo(UUID.randomUUID()));
        }
        catch (EventBusException ex) {
            throw new RuntimeException(ex);
        }
    }

}
