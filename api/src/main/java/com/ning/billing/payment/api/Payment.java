/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;
import com.ning.billing.util.entity.Entity;

public interface Payment extends Entity {

    /**
     *
     * @return the account id
     */
    UUID getAccountId();

    /**
     *
     * @return the invoice id
     */
    UUID getInvoiceId();

    /**
     *
     * @return the payment method id
     */
    UUID getPaymentMethodId();

    /**
     *
     * @return the payment number
     */
    Integer getPaymentNumber();

    /**
     *
     * @return the amount that needs to be paid
     */
    BigDecimal getAmount();

    /**
     *
     * @return the paid amount
     */
    BigDecimal getPaidAmount();

    /**
     * If the payment is successful, this date is the date of payment, otherwise this is the date of the last attempt
     *
     * @return the effective date of the payment
     */
    DateTime getEffectiveDate();

    /**
     *
     * @return the currency associated with that payment
     */
    Currency getCurrency();

    /**
     *
     * @return the payment status
     */
    PaymentStatus getPaymentStatus();

    /**
     *
     * @return the list of attempts on that payment
     */
    List<PaymentAttempt> getAttempts();

    /**
     *
     * @return the first payment ref id from the plugin
     */
    @Deprecated
    String getExtFirstPaymentIdRef();

    /**
     *
     * @return the second payment ref id from the plugin
     */
    @Deprecated
    String getExtSecondPaymentIdRef();

    /**
     * This will only be filled when the call requires the detail from the plugin
     *
     * @return the addtional info from the plugin
     */
    PaymentInfoPlugin getPaymentInfoPlugin();


}
