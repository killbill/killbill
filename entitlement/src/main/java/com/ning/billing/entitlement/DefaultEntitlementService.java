package com.ning.billing.entitlement;

public class DefaultEntitlementService implements EntitlementService {

    @Override
    public String getName() {
        return EntitlementService.ENTITLEMENT_SERVICE_NAME;
    }
}
