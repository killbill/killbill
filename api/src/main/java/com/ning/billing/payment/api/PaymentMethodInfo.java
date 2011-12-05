package com.ning.billing.payment.api;

public class PaymentMethodInfo {
    private final String id;
    private final String accountId;
    private final Boolean defaultMethod;
    private final String email;
    private final String type;

    public PaymentMethodInfo(String id,
                             String accountId,
                             Boolean defaultMethod,
                             String email,
                             String type) {
        this.id = id;
        this.accountId = accountId;
        this.defaultMethod = defaultMethod;
        this.email = email;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public Boolean getDefaultMethod() {
        return defaultMethod;
    }

    public String getEmail() {
        return email;
    }

    public String getType() {
        return type;
    }

}
