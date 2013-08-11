package com.ning.billing.entitlement;

import com.ning.billing.lifecycle.KillbillService;

public interface EntitlementService extends KillbillService {

    public static final String ENTITLEMENT_SERVICE_NAME = "entitlement-service";

    public String getName();
}
