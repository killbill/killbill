package com.ning.billing.payment.api;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.util.entity.Entity;

public interface PaymentAttempt extends Entity {

    /**
     *
     * @return the payment attempt id
     */
    UUID getId();

    /**
     *
     * @return the date when that attempt was made
     */
    DateTime getEffectiveDate();

    /**
     *
     * @return the error code from the gateway
     */
    String getGatewayErrorCode();

    /**
     *
     * @return the error message from the gateway
     */
    String getGatewayErrorMsg();

    /**
     *
     * @return the status for that attempt
     */
    PaymentStatus getPaymentStatus();
}
