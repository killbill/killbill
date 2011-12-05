package com.ning.billing.payment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.eventbus.Subscribe;
import com.ning.billing.payment.api.PaymentError;

public class MockPaymentInfoReceiver {
    private final List<PaymentInfo> processedPayments = Collections.synchronizedList(new ArrayList<PaymentInfo>());
    private final List<PaymentError> errors = Collections.synchronizedList(new ArrayList<PaymentError>());

    @Subscribe
    public void processedPayment(PaymentInfo paymentInfo) {
        processedPayments.add(paymentInfo);
    }

    @Subscribe
    public void processedPaymentError(PaymentError paymentError) {
        errors.add(paymentError);
    }

    public List<PaymentInfo> getProcessedPayments() {
        return new ArrayList<PaymentInfo>(processedPayments);
    }

    public List<PaymentError> getErrors() {
        return new ArrayList<PaymentError>(errors);
    }

    public void clear() {
        processedPayments.clear();
        errors.clear();
    }
}