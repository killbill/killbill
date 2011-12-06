package com.ning.billing.payment;

import com.ning.billing.payment.api.PaymentMethodInfo;

public class PaypalPaymentMethod  extends PaymentMethodInfo {
    private final String baid;

    public PaypalPaymentMethod(String id,
                               String accountId,
                               String baid,
                               Boolean defaultMethod,
                               String email,
                               String type) {
        super(id, accountId, defaultMethod, email, "paypal");
        this.baid = baid;
    }

    public String getBaid() {
        return baid;
    }

}
