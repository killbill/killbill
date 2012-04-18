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

package com.ning.billing.overdue.wrapper;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.overdue.applicator.OverdueStateApplicator;
import com.ning.billing.overdue.calculator.BillingStateCalculatorBundle;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.config.api.OverdueError;
import com.ning.billing.overdue.service.ExtendedOverdueService;
import com.ning.billing.util.clock.Clock;

public class OverdueWrapperFactory {

    private final OverdueConfig overdueConfig;
    private final BillingStateCalculatorBundle billingStateCalcuatorBundle;
    private final OverdueStateApplicator<SubscriptionBundle> overdueStateApplicatorBundle;
    private final BlockingApi api;
    private final Clock clock;

    @Inject
    public OverdueWrapperFactory(BlockingApi api, ExtendedOverdueService service, Clock clock, 
            BillingStateCalculatorBundle billingStateCalcuatorBundle, OverdueStateApplicator<SubscriptionBundle> overdueStateApplicatorBundle) {
        this.billingStateCalcuatorBundle = billingStateCalcuatorBundle;
        this.overdueStateApplicatorBundle = overdueStateApplicatorBundle;
        this.overdueConfig = service.getOverdueConfig();
        this.api = api;
        this.clock = clock;
    }

    @SuppressWarnings("unchecked")
    public <T extends Blockable> OverdueWrapper<T> createOverdueWrapperFor(T overdueable) throws OverdueError {
        if(overdueable instanceof SubscriptionBundle) {
            return (OverdueWrapper<T>)new OverdueWrapper<SubscriptionBundle>((SubscriptionBundle)overdueable, api, overdueConfig.getBundleStateSet(), 
                    clock, billingStateCalcuatorBundle, overdueStateApplicatorBundle );
        } else {
            throw new OverdueError(ErrorCode.OVERDUE_OVERDUEABLE_NOT_SUPPORTED, overdueable.getClass());
        }
    }


}
