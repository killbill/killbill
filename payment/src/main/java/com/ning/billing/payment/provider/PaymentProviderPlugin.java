package com.ning.billing.payment.provider;

import com.ning.billing.invoice.model.Invoice;

public interface PaymentProviderPlugin {
    public void processInvoice(Invoice invoice);
}
