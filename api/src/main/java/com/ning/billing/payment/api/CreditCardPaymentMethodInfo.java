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
        private String cardAddress1;
        private String cardAddress2;
        private String cardCity;
        private String cardState;
        private String cardPostalCode;
        private String cardCountry;

        public Builder() {
            super(Builder.class);
        }

        public Builder(CreditCardPaymentMethodInfo src) {
            super(Builder.class, src);
            this.cardHolderName = src.cardHolderName;
            this.cardType = src.cardType;
            this.expirationDate = src.expirationDate;
            this.cardAddress1 = src.cardAddress1;
            this.cardAddress2 = src.cardAddress2;
            this.cardCity = src.cardCity;
            this.cardState = src.cardState;
            this.cardPostalCode = src.cardPostalCode;
            this.cardCountry = src.cardCountry;
            this.maskNumber = src.maskNumber;
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

        public Builder setCardAddress1(String creditCardAddress1) {
            this.cardAddress1 = creditCardAddress1;
            return this;
        }

        public Builder setCardAddress2(String creditCardAddress2) {
            this.cardAddress2 = creditCardAddress2;
            return this;
        }

        public Builder setCardCity(String creditCardCity) {
            this.cardCity = creditCardCity;
            return this;
        }

        public Builder setCardState(String creditCardState) {
            this.cardState = creditCardState;
            return this;
        }

        public Builder setCardPostalCode(String creditCardPostalCode) {
            this.cardPostalCode = creditCardPostalCode;
            return this;
        }

        public Builder setCardCountry(String creditCardCountry) {
            this.cardCountry = creditCardCountry;
            return this;
        }

        public Builder setMaskNumber(String maskNumber) {
            this.maskNumber = maskNumber;
            return this;
        }

        public CreditCardPaymentMethodInfo build() {
            return new CreditCardPaymentMethodInfo(id,
                                                   accountId,
                                                   defaultMethod,
                                                   cardHolderName,
                                                   cardType,
                                                   expirationDate,
                                                   maskNumber,
                                                   cardAddress1,
                                                   cardAddress2,
                                                   cardCity,
                                                   cardState,
                                                   cardPostalCode,
                                                   cardCountry);
        }
    }

    private final String cardHolderName;
    private final String cardType;
    private final String expirationDate;
    private final String maskNumber;
    private final String cardAddress1;
    private final String cardAddress2;
    private final String cardCity;
    private final String cardState;
    private final String cardPostalCode;
    private final String cardCountry;

    public CreditCardPaymentMethodInfo(String id,
                                   String accountId,
                                   Boolean defaultMethod,
                                   String cardHolderName,
                                   String cardType,
                                   String expirationDate,
                                   String maskNumber,
                                   String cardAddress1,
                                   String cardAddress2,
                                   String cardCity,
                                   String cardState,
                                   String cardPostalCode,
                                   String cardCountry) {

      super(id, accountId, defaultMethod, "CreditCard");
      this.cardHolderName = cardHolderName;
      this.cardType = cardType;
      this.expirationDate = expirationDate;
      this.maskNumber = maskNumber;
      this.cardAddress1 = cardAddress1;
      this.cardAddress2 = cardAddress2;
      this.cardCity = cardCity;
      this.cardState = cardState;
      this.cardPostalCode = cardPostalCode;
      this.cardCountry = cardCountry;
    }

    public String getCardHolderName() {
      return cardHolderName;
    }

    public String getCardType() {
      return cardType;
    }

    public String getCardAddress1() {
        return cardAddress1;
    }

    public String getCardAddress2() {
        return cardAddress2;
    }

    public String getCardCity() {
        return cardCity;
    }

    public String getCardState() {
        return cardState;
    }

    public String getCardPostalCode() {
        return cardPostalCode;
    }

    public String getCardCountry() {
        return cardCountry;
    }

    public String getExpirationDate() {
      return expirationDate;
    }

    public String getMaskNumber() {
      return maskNumber;
    }

    @Override
    public String toString() {
        return "CreditCardPaymentMethodInfo [cardHolderName=" + cardHolderName + ", cardType=" + cardType + ", expirationDate=" + expirationDate + ", maskNumber=" + maskNumber + ", cardAddress1=" + cardAddress1 + ", cardAddress2=" + cardAddress2 + ", cardCity=" + cardCity + ", cardState=" + cardState + ", cardPostalCode=" + cardPostalCode + ", cardCountry=" + cardCountry + "]";
    }

}
