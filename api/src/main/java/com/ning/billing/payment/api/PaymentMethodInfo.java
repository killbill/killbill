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

//    {
//        "accountId": "4028e487336cf73001337259cb2c7dc3",
//        "baid": "B-0TV91452RK157135S",
//        "defaultMethod": false,
//        "email": "JAdamRush@gmail.com",
//        "id": "4028e487336cf7300133725bc5900391",
//        "type": "PayPal"
//    },
//    {
//        "accountId": "4028e487336cf73001337259cb2c7dc3",
//        "cardHolderName": "Jaime Jaramillo Maldonado",
//        "cardType": "MasterCard",
//        "defaultMethod": true,
//        "expirationDate": "2012-05",
//        "id": "4028e48733fd62b6013400e0a3b818d1",
//        "maskNumber": "************8631",
//        "type": "CreditCard"
//    }

}
