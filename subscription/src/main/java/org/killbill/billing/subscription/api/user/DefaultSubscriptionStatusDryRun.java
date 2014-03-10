/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.subscription.api.user;

import java.util.UUID;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.entitlement.api.EntitlementAOStatusDryRun;

public class DefaultSubscriptionStatusDryRun implements EntitlementAOStatusDryRun {

    private final UUID id;
    private final String productName;
    private final PhaseType phaseType;
    private final BillingPeriod billingPeriod;
    private final String priceList;
    private final DryRunChangeReason reason;


    public DefaultSubscriptionStatusDryRun(final UUID id, final String productName,
                                           final PhaseType phaseType, final BillingPeriod billingPeriod, final String priceList,
                                           final DryRunChangeReason reason) {
        super();
        this.id = id;
        this.productName = productName;
        this.phaseType = phaseType;
        this.billingPeriod = billingPeriod;
        this.priceList = priceList;
        this.reason = reason;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getProductName() {
        return productName;
    }

    @Override
    public PhaseType getPhaseType() {
        return phaseType;
    }


    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    @Override
    public String getPriceList() {
        return priceList;
    }

    @Override
    public DryRunChangeReason getReason() {
        return reason;
    }
}
