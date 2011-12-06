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

package com.ning.billing.payment;

import com.ning.billing.payment.api.PaymentMethodInfo;

public class CreditCardPaymentMethod  extends PaymentMethodInfo {
    private final String cardHolderName;
    private final String cardType; // e.g. MasterCard
    private final String expirationDateStr; // e.g. 2012-01
    private final String maskNumber; // e.g. "************1234"

    public CreditCardPaymentMethod(String id,
                                   String accountId,
                                   Boolean defaultMethod,
                                   String email,
                                   String type,
                                   String cardHolderName,
                                   String cardType,
                                   String expirationDateStr,
                                   String maskNumber) {
        super(id, accountId, defaultMethod, email, "creditCard");
        this.cardHolderName = cardHolderName;
        this.cardType = cardType;
        this.expirationDateStr = expirationDateStr;
        this.maskNumber = maskNumber;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public String getCardType() {
        return cardType;
    }

    public String getExpirationDateStr() {
        return expirationDateStr;
    }

    public String getMaskNumber() {
        return maskNumber;
    }

}
