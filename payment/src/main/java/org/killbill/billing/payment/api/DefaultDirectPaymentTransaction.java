/*
 * Copyright 2014 Groupon, Inc
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.payment.plugin.api.PaymentInfoPlugin;

public class DefaultDirectPaymentTransaction extends EntityBase implements DirectPaymentTransaction {

    private final UUID directTransactionId;
    private final TransactionType transactionType;
    private final DateTime effectiveDate;
    private final PaymentStatus status;
    private final BigDecimal amount;
    private final Currency currency;
    private final String gatewayErrorCode;
    private final String gatewayErrorMsg;
    private final PaymentInfoPlugin infoPlugin;
    private final Integer retryCount;

    public DefaultDirectPaymentTransaction(final UUID id, final DateTime createdDate, final DateTime updatedDate, final UUID directTransactionId, final TransactionType transactionType,
                                           final DateTime effectiveDate, final Integer retryCount, final PaymentStatus status, final BigDecimal amount, final Currency currency,
                                           final String gatewayErrorCode, final String gatewayErrorMsg, final PaymentInfoPlugin infoPlugin) {
        super(id, createdDate, updatedDate);
        this.directTransactionId = directTransactionId;
        this.transactionType = transactionType;
        this.effectiveDate = effectiveDate;
        this.retryCount = retryCount;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.gatewayErrorCode = gatewayErrorCode;
        this.gatewayErrorMsg = gatewayErrorMsg;
        this.infoPlugin = infoPlugin;
    }

    @Override
    public UUID getDirectPaymentId() {
        return directTransactionId;
    }

    @Override
    public TransactionType getTransactionType() {
        return transactionType;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public String getGatewayErrorCode() {
        return gatewayErrorCode;
    }

    @Override
    public String getGatewayErrorMsg() {
        return gatewayErrorMsg;
    }

    @Override
    public PaymentStatus getPaymentStatus() {
        return status;
    }

    @Override
    public PaymentInfoPlugin getPaymentInfoPlugin() {
        return infoPlugin;
    }
}
