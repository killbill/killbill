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

package com.ning.billing.analytics.api;

import java.math.BigDecimal;

import org.joda.time.LocalDate;

import com.ning.billing.util.entity.Entity;

public interface BusinessAccount extends Entity {

    /**
     * @return account external key, if available
     */
    public String getExternalKey();

    /**
     * @return account holder name
     */
    public String getName();

    /**
     * @return account currency
     */
    public String getCurrency();

    /**
     * @return current account balance
     */
    public BigDecimal getBalance();

    /**
     * @return date of the last invoice
     */
    public LocalDate getLastInvoiceDate();

    /**
     * @return sum of all invoices balance
     */
    public BigDecimal getTotalInvoiceBalance();

    /**
     * @return status of the last payment
     */
    public String getLastPaymentStatus();

    /**
     * @return default payment method type
     */
    public String getDefaultPaymentMethodType();

    /**
     * @return type of credit card for the default payment method,
     *         if the default payment method is a credit card
     */
    public String getDefaultCreditCardType();

    /**
     * @return billing country for the default payment method
     */
    public String getDefaultBillingAddressCountry();
}
