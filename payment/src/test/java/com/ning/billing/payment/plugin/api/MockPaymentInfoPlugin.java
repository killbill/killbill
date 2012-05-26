/* 
 * Copyright 2010-2011 Ning, Inc.
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
package com.ning.billing.payment.plugin.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.payment.api.PaymentInfoEvent;

public class MockPaymentInfoPlugin implements PaymentInfoPlugin {
    private final String externalPaymentId;
    private final BigDecimal amount;
    private final String bankIdentificationNumber;
    private final DateTime createdDate;
    private final DateTime effectiveDate;
    private final String paymentNumber;
    private final String paymentMethod;
    private final String cardType;
    private final String cardCountry;
    private final String referenceId;    
    private final String paymentMethodId;        
    private final BigDecimal refundAmount;
    private final String status;    
    private final String type;
    private final DateTime updatedDate;
    
    
    public MockPaymentInfoPlugin(PaymentInfoEvent info) {
        super();
        this.externalPaymentId = info.getExternalPaymentId();
        this.amount = info.getAmount();
        this.bankIdentificationNumber = info.getBankIdentificationNumber();
        this.createdDate = info.getCreatedDate();
        this.effectiveDate = info.getEffectiveDate();
        this.paymentNumber = info.getPaymentNumber();
        this.paymentMethod = info.getPaymentMethod();
        this.cardType = info.getCardType();
        this.cardCountry = info.getCardCountry();
        this.referenceId = info.getReferenceId();
        this.paymentMethodId = info.getPaymentMethodId();
        this.refundAmount = info.getRefundAmount();
        this.status = info.getStatus();
        this.type = info.getType();
        this.updatedDate = info.getUpdatedDate();
    }


    @Override
    public String getExternalPaymentId() {
        return externalPaymentId;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public String getBankIdentificationNumber() {
        return bankIdentificationNumber;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public String getPaymentNumber() {
        return paymentNumber;
    }

    @Override
    public String getPaymentMethod() {
        return paymentMethod;
    }

    @Override
    public String getCardType() {
        return cardType;
    }

    @Override
    public String getCardCountry() {
        return cardCountry;
    }

    @Override
    public String getReferenceId() {
        return referenceId;
    }

    @Override
    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    @Override
    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }
}
