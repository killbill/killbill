package com.ning.billing.payment.api;

import com.google.common.base.Objects;

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

    @Override
    public int hashCode() {
        return Objects.hashCode(id,
                                accountId,
                                defaultMethod,
                                email,
                                type);
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() == obj.getClass()) {
            PaymentMethodInfo other = (PaymentMethodInfo)obj;
            if (obj == other) {
                return true;
            }
            else {
                return Objects.equal(id, other.id) &&
                       Objects.equal(accountId, other.accountId) &&
                       Objects.equal(defaultMethod, other.defaultMethod) &&
                       Objects.equal(email, other.email) &&
                       Objects.equal(type, other.type);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "PaymentMethodInfo [id=" + id + ", accountId=" + accountId + ", defaultMethod=" + defaultMethod + ", email=" + email + ", type=" + type + "]";
    }

}
