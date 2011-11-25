package com.ning.billing.payment.provider;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.model.Invoice;

public interface PaymentProviderPlugin {
    public void processInvoice(Account account, Invoice invoice);
}
