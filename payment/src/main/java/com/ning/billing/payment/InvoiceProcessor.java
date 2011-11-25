package com.ning.billing.payment;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.model.Invoice;
import com.ning.billing.payment.provider.PaymentProviderPlugin;
import com.ning.billing.util.eventbus.IEventBus.EventBusException;

public class InvoiceProcessor {
    private final PaymentProviderPlugin provider;

    @Inject
    public InvoiceProcessor(PaymentProviderPlugin provider) {
        this.provider = provider;
    }

    @Subscribe
    public void receiveInvoice(Invoice invoice) throws EventBusException {
        // TODO: retrieve account
        final Account account = null;

        provider.processInvoice(account, invoice);
    }
}
