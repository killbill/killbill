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

package com.ning.billing.analytics;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.account.api.IAccountUserApi;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionDao;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.IEntitlementUserApi;
import com.ning.billing.entitlement.api.user.ISubscriptionBundle;
import com.ning.billing.entitlement.api.user.ISubscriptionTransition;
import org.joda.time.DateTime;

import java.util.List;

public class AnalyticsListener
{
    private final BusinessSubscriptionTransitionDao dao;
    private final IEntitlementUserApi entitlementApi;
    private final IAccountUserApi accountApi;
    private final BusinessSubscriptionTransitionRecorder bstRecorder;

    @Inject
    public AnalyticsListener(final BusinessSubscriptionTransitionDao dao, final IEntitlementUserApi entitlementApi, final IAccountUserApi accountApi)
    {
        this.dao = dao;
        this.entitlementApi = entitlementApi;
        this.accountApi = accountApi;
        this.bstRecorder = new BusinessSubscriptionTransitionRecorder(dao);
    }

    @Subscribe
    public void handleNotificationChange(final ISubscriptionTransition event)
    {
        switch (event.getTransitionType()) {
            case CREATE:
                subscriptionCreated(event);
                break;
            case CANCEL:
                subscriptionCancelled(event);
                break;
            case CHANGE:
                subscriptionChanged(event);
                break;
            case PAUSE:
                subscriptionPaused(event);
                break;
            case RESUME:
                subscriptionResumed(event);
                break;
            case UNCANCEL:
                break;
            case PHASE:
                subscriptionPhaseChanged(event);
                break;
            default:
                throw new RuntimeException("Unexpected event type " + event.getRequestedTransitionTime());
        }
    }

    private void subscriptionCreated(final ISubscriptionTransition created)
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCreated(created.getNextPlan());
        recordTransition(event, created);
    }

    private void subscriptionCancelled(final ISubscriptionTransition cancelled)
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCancelled(cancelled.getNextPlan());
        recordTransition(event, cancelled);
    }

    private void subscriptionChanged(final ISubscriptionTransition changed)
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionChanged(changed.getNextPlan());
        recordTransition(event, changed);
    }

    private void subscriptionPaused(final ISubscriptionTransition paused)
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionPaused(paused.getNextPlan());
        recordTransition(event, paused);
    }

    private void subscriptionResumed(final ISubscriptionTransition resumed)
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionResumed(resumed.getNextPlan());
        recordTransition(event, resumed);
    }

    private void subscriptionPhaseChanged(final ISubscriptionTransition phaseChanged)
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionPhaseChanged(phaseChanged.getNextPlan(), phaseChanged.getNextState());
        recordTransition(event, phaseChanged);
    }

    private void recordTransition(final BusinessSubscriptionEvent event, final ISubscriptionTransition transition)
    {
        Currency currency = null;
        String transitionKey = null;

        // Retrieve key and currency via the bundle
        final ISubscriptionBundle bundle = entitlementApi.getBundleFromId(transition.getBundleId());
        if (bundle != null) {
            transitionKey = bundle.getKey();

            final IAccount account = accountApi.getAccountFromId(bundle.getAccountId());
            if (account != null) {
                currency = account.getCurrency();
            }
        }

        // The ISubscriptionTransition interface gives us all the prev/next information we need but the start date
        // of the previous plan. We need to retrieve it from our own transitions table
        DateTime previousEffectiveTransitionTime = null;
        final List<BusinessSubscriptionTransition> transitions = dao.getTransitions(transitionKey);
        if (transitions != null && transitions.size() > 0) {
            final BusinessSubscriptionTransition lastTransition = transitions.get(transitions.size() - 1);
            if (lastTransition != null && lastTransition.getNextSubscription() != null) {
                previousEffectiveTransitionTime = lastTransition.getNextSubscription().getStartDate();
            }
        }

        // TODO Support currency changes
        final BusinessSubscription prevSubscription = new BusinessSubscription(transition.getPreviousPlan(), transition.getPreviousPhase(), currency, previousEffectiveTransitionTime, transition.getPreviousState(), transition.getSubscriptionId(), transition.getBundleId());
        final BusinessSubscription nextSubscription = new BusinessSubscription(transition.getNextPlan(), transition.getNextPhase(), currency, transition.getEffectiveTransitionTime(), transition.getNextState(), transition.getSubscriptionId(), transition.getBundleId());

        bstRecorder.record(transitionKey, transition.getRequestedTransitionTime(), event, prevSubscription, nextSubscription);
    }
}
