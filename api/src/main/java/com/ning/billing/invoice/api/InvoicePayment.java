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

package com.ning.billing.invoice.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.entity.Entity;

public interface InvoicePayment extends Entity {

    /**
     * @return payment id
     */
    UUID getPaymentId();

    /**
     * @return invoice payment type
     */
    InvoicePaymentType getType();

    /**
     * @return invoice id
     */
    UUID getInvoiceId();

    /**
     * @return payment date
     */
    DateTime getPaymentDate();

    /**
     * @return amount (from the payment)
     */
    BigDecimal getAmount();

    /**
     * @return currency (from the payment)
     */
    Currency getCurrency();

    /**
     * Linked invoice payment id: null for payments, associated
     * invoice payment id for refunds and chargebacks
     *
     * @return linked invoice payment id
     */
    UUID getLinkedInvoicePaymentId();

    /**
     * Payment cookie id: null for payments and chargebacks, refund id for refunds
     *
     * @return payment cookie id
     */
    UUID getPaymentCookieId();

}
