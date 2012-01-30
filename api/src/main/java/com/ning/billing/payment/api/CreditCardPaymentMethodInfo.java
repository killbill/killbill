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

package com.ning.billing.payment.api;


public final class CreditCardPaymentMethodInfo extends PaymentMethodInfo {
    public static final class Builder extends BuilderBase<CreditCardPaymentMethodInfo, Builder> {
        private String cardHolderName;
        private String cardType;
        private String expirationDate;
        private String maskNumber;

        public Builder() {
            super(Builder.class);
        }

        public Builder(CreditCardPaymentMethodInfo src) {
            super(Builder.class, src);
        }

        public Builder setCardHolderName(String cardHolderName) {
            this.cardHolderName = cardHolderName;
            return this;
        }

        public Builder setCardType(String cardType) {
            this.cardType = cardType;
            return this;
        }

        public Builder setExpirationDateStr(String expirationDateStr) {
            this.expirationDate = expirationDateStr;
            return this;
        }

        public Builder setMaskNumber(String maskNumber) {
            this.maskNumber = maskNumber;
            return this;
        }

        public CreditCardPaymentMethodInfo build() {
            return new CreditCardPaymentMethodInfo(id, accountId, defaultMethod, cardHolderName, cardType, expirationDate, maskNumber);
        }
    }

    private final String cardHolderName;
    private final String cardType;
    private final String expirationDate;
    private final String maskNumber;

    public CreditCardPaymentMethodInfo(String id,
                                   String accountId,
                                   Boolean defaultMethod,
                                   String cardHolderName,
                                   String cardType,
                                   String expirationDate,
                                   String maskNumber) {
      super(id, accountId, defaultMethod, "CreditCard");
      this.cardHolderName = cardHolderName;
      this.cardType = cardType;
      this.expirationDate = expirationDate;
      this.maskNumber = maskNumber;
    }

    public String getCardHolderName() {
      return cardHolderName;
    }

    public String getCardType() {
      return cardType;
    }

    public String getExpirationDate() {
      return expirationDate;
    }

    public String getMaskNumber() {
      return maskNumber;
    }
}
