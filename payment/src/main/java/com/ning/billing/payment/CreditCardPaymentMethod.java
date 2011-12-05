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
