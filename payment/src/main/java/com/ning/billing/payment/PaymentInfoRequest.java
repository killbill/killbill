package com.ning.billing.payment;

import java.util.UUID;

import com.ning.billing.util.eventbus.IEventBusType;

public class PaymentInfoRequest implements IEventBusType {
    private final UUID accountId;
    private final String paymentId;

    public PaymentInfoRequest(UUID accountId, String paymentId) {
        this.accountId = accountId;
        this.paymentId = paymentId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getPaymentId() {
        return paymentId;
    }
}
