/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.entitlement.api;

import org.killbill.billing.entitlement.DefaultEntitlementService;
import org.killbill.billing.platform.api.KillbillService.KILLBILL_SERVICES;

public abstract class EntitlementOrderingBase {

    public static final String BILLING_SERVICE_NAME = "billing-service";
    public static final String ENT_BILLING_SERVICE_NAME = "entitlement+billing-service";

    protected static String getRealServiceNameForEntitlementOrExternalServiceName(final String originalServiceName, final SubscriptionEventType eventType) {
        final String serviceName;
        if (KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName().equals(originalServiceName)) {
            serviceName = getServiceName(eventType);
        } else {
            serviceName = originalServiceName;
        }
        return serviceName;
    }

    protected static String getServiceName(final SubscriptionEventType type) {
        switch (type) {
            case START_BILLING:
            case PAUSE_BILLING:
            case RESUME_BILLING:
            case STOP_BILLING:
                return BILLING_SERVICE_NAME;

            case PHASE:
            case CHANGE:
                return ENT_BILLING_SERVICE_NAME;

            default:
                return KILLBILL_SERVICES.ENTITLEMENT_SERVICE.getServiceName();
        }
    }
}
