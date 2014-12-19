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

package org.killbill.billing.overdue.wrapper;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.billing.overdue.applicator.OverdueStateApplicator;
import org.killbill.billing.overdue.calculator.BillingStateCalculator;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.overdue.config.api.OverdueException;
import org.killbill.billing.overdue.config.api.OverdueStateSet;
import org.killbill.clock.Clock;

public class OverdueWrapper {

    private final Account overdueable;
    private final BlockingInternalApi api;
    private final Clock clock;
    private final OverdueStateSet overdueStateSet;
    private final BillingStateCalculator billingStateCalcuator;
    private final OverdueStateApplicator overdueStateApplicator;

    public OverdueWrapper(final Account overdueable, final BlockingInternalApi api,
                          final OverdueStateSet overdueStateSet,
                          final Clock clock,
                          final BillingStateCalculator billingStateCalcuator,
                          final OverdueStateApplicator overdueStateApplicator) {
        this.overdueable = overdueable;
        this.overdueStateSet = overdueStateSet;
        this.api = api;
        this.clock = clock;
        this.billingStateCalcuator = billingStateCalcuator;
        this.overdueStateApplicator = overdueStateApplicator;
    }

    public OverdueState refresh(final InternalCallContext context) throws OverdueException, OverdueApiException {
        if (overdueStateSet.size() < 1) { // No configuration available
            return overdueStateSet.getClearState();
        }

        final BillingState billingState = billingState(context);
        final String previousOverdueStateName = api.getBlockingStateForService(overdueable.getId(), BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, context).getStateName();

        final OverdueState currentOverdueState = overdueStateSet.findState(previousOverdueStateName);
        final OverdueState nextOverdueState = overdueStateSet.calculateOverdueState(billingState, clock.getToday(billingState.getAccountTimeZone()));

        overdueStateApplicator.apply(overdueStateSet, billingState, overdueable, currentOverdueState, nextOverdueState, context);

        return nextOverdueState;
    }

    public void clear(final InternalCallContext context) throws OverdueException, OverdueApiException {
        final String previousOverdueStateName = api.getBlockingStateForService(overdueable.getId(), BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, context).getStateName();
        final OverdueState previousOverdueState = overdueStateSet.findState(previousOverdueStateName);
        overdueStateApplicator.clear(overdueable, previousOverdueState, overdueStateSet.getClearState(), context);
    }

    public BillingState billingState(final InternalTenantContext context) throws OverdueException {
        return billingStateCalcuator.calculateBillingState(overdueable, context);
    }
}
