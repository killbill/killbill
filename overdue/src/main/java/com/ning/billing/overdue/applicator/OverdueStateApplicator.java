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

package com.ning.billing.overdue.applicator;

import java.util.LinkedList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.junction.api.DefaultBlockingState;
import com.ning.billing.ovedue.notification.OverdueCheckPoster;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.OverdueCancellationPolicicy;
import com.ning.billing.overdue.OverdueChangeEvent;
import com.ning.billing.overdue.OverdueService;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;

public class OverdueStateApplicator<T extends Blockable> {

    private static final String API_USER_NAME = "OverdueStateApplicator";

    private static final Logger log = LoggerFactory.getLogger(OverdueStateApplicator.class);

    private final BlockingApi blockingApi;
    private final Clock clock;
    private final OverdueCheckPoster poster;
    private final Bus bus;
    private final EntitlementUserApi entitlementUserApi;
    private final CallContextFactory factory;


    @Inject
    public OverdueStateApplicator(final BlockingApi accessApi, final EntitlementUserApi entitlementUserApi, final Clock clock,
            final OverdueCheckPoster poster, final Bus bus, final CallContextFactory factory) {
        this.blockingApi = accessApi;
        this.entitlementUserApi = entitlementUserApi;
        this.clock = clock;
        this.poster = poster;
        this.bus = bus;
        this.factory = factory;
    }


    public void apply(final OverdueState<T> firstOverdueState, final BillingState<T> billingState,
            final T overdueable, final String previousOverdueStateName, final OverdueState<T> nextOverdueState) throws OverdueException {

        try {

            // We did not reach first state, we we need to check if there is any pending condition for which we will not receive
            // any notifications.. (last two conditions are there for test purpose)
            if (nextOverdueState.isClearState() && firstOverdueState != null && billingState !=  null) {
                 final LocalDate firstUnpaidInvoice = billingState.getDateOfEarliestUnpaidInvoice();
                 if (firstUnpaidInvoice != null) {
                     final Period reevaluationInterval = firstOverdueState.getReevaluationInterval();
                     createFutureNotification(overdueable, firstUnpaidInvoice.toDateTimeAtCurrentTime().plus(reevaluationInterval));
                 }
            }

            if (nextOverdueState == null || previousOverdueStateName.equals(nextOverdueState.getName())) {
                return; //That's it we are done...
            }

            storeNewState(overdueable, nextOverdueState);

            cancelSubscriptionsIfRequired(overdueable, nextOverdueState);

            final Period reevaluationInterval = nextOverdueState.getReevaluationInterval();
            if (!nextOverdueState.isClearState()) {
                createFutureNotification(overdueable, clock.getUTCNow().plus(reevaluationInterval));
            }
        } catch (OverdueApiException e) {
            if (e.getCode() != ErrorCode.OVERDUE_NO_REEVALUATION_INTERVAL.getCode()) {
                throw new OverdueException(e);
            }
        }

        if (nextOverdueState.isClearState()) {
            clear(overdueable);
        }

        try {
            bus.post(createOverdueEvent(overdueable, previousOverdueStateName, nextOverdueState.getName()));
        } catch (Exception e) {
            log.error("Error posting overdue change event to bus",e);
        }
    }


    private OverdueChangeEvent createOverdueEvent(T overdueable, String previousOverdueStateName, String nextOverdueStateName) throws BlockingApiException {
        return new DefaultOverdueChangeEvent(overdueable.getId(), Blockable.Type.get(overdueable), previousOverdueStateName, nextOverdueStateName, null);
    }

    protected void storeNewState(final T blockable, final OverdueState<T> nextOverdueState) throws OverdueException {
        try {
            blockingApi.setBlockingState(new DefaultBlockingState(blockable.getId(), nextOverdueState.getName(), Blockable.Type.get(blockable),
                                                                  OverdueService.OVERDUE_SERVICE_NAME, blockChanges(nextOverdueState), blockEntitlement(nextOverdueState), blockBilling(nextOverdueState)));
        } catch (Exception e) {
            throw new OverdueException(e, ErrorCode.OVERDUE_CAT_ERROR_ENCOUNTERED, blockable.getId(), blockable.getClass().getName());
        }
    }

    private boolean blockChanges(final OverdueState<T> nextOverdueState) {
        return nextOverdueState.blockChanges();
    }

    private boolean blockBilling(final OverdueState<T> nextOverdueState) {
        return nextOverdueState.disableEntitlementAndChangesBlocked();
    }

    private boolean blockEntitlement(final OverdueState<T> nextOverdueState) {
        return nextOverdueState.disableEntitlementAndChangesBlocked();
    }

    protected void createFutureNotification(final T overdueable,
                                            final DateTime timeOfNextCheck) {
        poster.insertOverdueCheckNotification(overdueable, timeOfNextCheck);

    }

    protected void clear(final T blockable) {
        //Need to clear the overrride table here too (when we add it)
        poster.clearNotificationsFor(blockable);
    }

    private void cancelSubscriptionsIfRequired(final T blockable, final OverdueState<T> nextOverdueState) throws OverdueException {
        if (nextOverdueState.getSubscriptionCancellationPolicy() == OverdueCancellationPolicicy.NONE) {
            return;
        }
        try {
            ActionPolicy actionPolicy = null;
            switch(nextOverdueState.getSubscriptionCancellationPolicy()) {
            case END_OF_TERM:
                actionPolicy = ActionPolicy.END_OF_TERM;
                break;
            case IMMEDIATE:
                actionPolicy = ActionPolicy.IMMEDIATE;
                break;
            default :
                throw new IllegalStateException("Unexpected OverdueCancellationPolicy " + nextOverdueState.getSubscriptionCancellationPolicy());
            }
            final List<Subscription> toBeCancelled = new LinkedList<Subscription>();
            computeSubscriptionsToCancel(blockable, toBeCancelled);
            final CallContext context = factory.createCallContext(API_USER_NAME, CallOrigin.INTERNAL, UserType.SYSTEM);
            for (Subscription cur : toBeCancelled) {
                cur.cancelWithPolicy(clock.getUTCNow(), actionPolicy, context);
            }
        } catch (EntitlementUserApiException e) {
            throw new OverdueException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void computeSubscriptionsToCancel(final T blockable, final List<Subscription> result) throws EntitlementUserApiException{
        if (blockable instanceof Subscription) {
            result.add((Subscription) blockable);
            return;
        } else if (blockable instanceof SubscriptionBundle) {
            for (Subscription cur : entitlementUserApi.getSubscriptionsForBundle(blockable.getId())) {
                computeSubscriptionsToCancel((T) cur, result);
            }
        } else if (blockable instanceof Account) {
            for (SubscriptionBundle cur : entitlementUserApi.getBundlesForAccount(blockable.getId())) {
                computeSubscriptionsToCancel((T) cur, result);
            }
        }
    }
}
