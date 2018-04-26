/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.overdue.wrapper;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.api.BlockingState;
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
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.globallocker.LockerType;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.LockFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OverdueWrapper {


    public static final String CLEAR_STATE_NAME = "__KILLBILL__CLEAR__OVERDUE_STATE__";

    private static final Logger log = LoggerFactory.getLogger(OverdueWrapper.class);

    // Should we introduce a config?
    private static final int MAX_LOCK_RETRIES = 50;

    private final Account overdueable;
    private final BlockingInternalApi api;
    private final GlobalLocker locker;
    private final Clock clock;
    private final OverdueStateSet overdueStateSet;
    private final BillingStateCalculator billingStateCalcuator;
    private final OverdueStateApplicator overdueStateApplicator;
    private final InternalCallContextFactory internalCallContextFactory;

    public OverdueWrapper(final Account overdueable,
                          final BlockingInternalApi api,
                          final OverdueStateSet overdueStateSet,
                          final GlobalLocker locker,
                          final Clock clock,
                          final BillingStateCalculator billingStateCalcuator,
                          final OverdueStateApplicator overdueStateApplicator,
                          final InternalCallContextFactory internalCallContextFactory) {
        this.overdueable = overdueable;
        this.overdueStateSet = overdueStateSet;
        this.api = api;
        this.locker = locker;
        this.clock = clock;
        this.billingStateCalcuator = billingStateCalcuator;
        this.overdueStateApplicator = overdueStateApplicator;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public OverdueState refresh(final DateTime effectiveDate, final InternalCallContext context) throws OverdueException, OverdueApiException {
        if (overdueStateSet.size() < 1) { // No configuration available
            return overdueStateSet.getClearState();
        }

        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), overdueable.getId().toString(), MAX_LOCK_RETRIES);

            return refreshWithLock(effectiveDate, context);
        } catch (final LockFailedException e) {
            log.warn("Failed to process overdue for accountId='{}'", overdueable.getId(), e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return null;
    }

    private OverdueState refreshWithLock(final DateTime effectiveDate, final InternalCallContext context) throws OverdueException, OverdueApiException {
        final BillingState billingState = billingState(context);
        final BlockingState blockingStateForService = api.getBlockingStateForService(overdueable.getId(), BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, context);
        final String previousOverdueStateName = blockingStateForService != null ? blockingStateForService.getStateName() : OverdueWrapper.CLEAR_STATE_NAME;
        final OverdueState currentOverdueState = overdueStateSet.findState(previousOverdueStateName);
        final OverdueState nextOverdueState = overdueStateSet.calculateOverdueState(billingState, context.toLocalDate(context.getCreatedDate()));

        overdueStateApplicator.apply(effectiveDate, overdueStateSet, billingState, overdueable, currentOverdueState, nextOverdueState, context);

        return nextOverdueState;
    }

    public void clear(final DateTime effectiveDate, final InternalCallContext context) throws OverdueException, OverdueApiException {
        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCNT_INV_PAY.toString(), overdueable.getId().toString(), MAX_LOCK_RETRIES);

            clearWithLock(effectiveDate, context);
        } catch (final LockFailedException e) {
            log.warn("Failed to clear overdue for accountId='{}'", overdueable.getId(), e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
    }

    private void clearWithLock(final DateTime effectiveDate, final InternalCallContext context) throws OverdueException, OverdueApiException {
        final BlockingState blockingStateForService = api.getBlockingStateForService(overdueable.getId(), BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, context);
        final String previousOverdueStateName = blockingStateForService != null ? blockingStateForService.getStateName() : OverdueWrapper.CLEAR_STATE_NAME;
        final OverdueState previousOverdueState = overdueStateSet.findState(previousOverdueStateName);
        overdueStateApplicator.clear(effectiveDate, overdueable, previousOverdueState, overdueStateSet.getClearState(), context);
    }

    public BillingState billingState(final InternalCallContext context) throws OverdueException {
        if ((overdueable.getParentAccountId() != null) && (overdueable.isPaymentDelegatedToParent())) {
            // calculate billing state from parent account
            final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(overdueable.getParentAccountId(), context);
            final InternalCallContext parentAccountContext = internalCallContextFactory.createInternalCallContext(internalTenantContext.getAccountRecordId(), context);
            return billingStateCalcuator.calculateBillingState(overdueable, parentAccountContext);
        }
        return billingStateCalcuator.calculateBillingState(overdueable, context);
    }
}
