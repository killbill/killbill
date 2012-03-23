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

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionDao;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class BusinessSubscriptionTransitionRecorder
{
    private static final Logger log = LoggerFactory.getLogger(BusinessSubscriptionTransitionRecorder.class);

    private final BusinessSubscriptionTransitionDao dao;
    private final EntitlementUserApi entitlementApi;
    private final AccountUserApi accountApi;

    @Inject
    public BusinessSubscriptionTransitionRecorder(final BusinessSubscriptionTransitionDao dao, final EntitlementUserApi entitlementApi, final AccountUserApi accountApi)
    {
        this.dao = dao;
        this.entitlementApi = entitlementApi;
        this.accountApi = accountApi;
    }

    public void subscriptionCreated(final SubscriptionTransition created) throws AccountApiException
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCreated(created.getNextPlan());
        recordTransition(event, created);
    }

    public void subscriptionRecreated(final SubscriptionTransition recreated) throws AccountApiException
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionRecreated(recreated.getNextPlan());
        recordTransition(event, recreated);
    }


    public void subscriptionCancelled(final SubscriptionTransition cancelled) throws AccountApiException
    {
        // cancelled.getNextPlan() is null here - need to look at the previous one to create the correct event name
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCancelled(cancelled.getPreviousPlan());
        recordTransition(event, cancelled);
    }

    public void subscriptionChanged(final SubscriptionTransition changed) throws AccountApiException
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionChanged(changed.getNextPlan());
        recordTransition(event, changed);
    }

    public void subscriptionPhaseChanged(final SubscriptionTransition phaseChanged) throws AccountApiException
    {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionPhaseChanged(phaseChanged.getNextPlan(), phaseChanged.getNextState());
        recordTransition(event, phaseChanged);
    }

    public void recordTransition(final BusinessSubscriptionEvent event, final SubscriptionTransition transition) throws AccountApiException
    {
        Currency currency = null;
        String transitionKey = null;
        String accountKey = null;

        // Retrieve key and currency via the bundle
        final SubscriptionBundle bundle = entitlementApi.getBundleFromId(transition.getBundleId());
        if (bundle != null) {
            transitionKey = bundle.getKey();

            final Account account = accountApi.getAccountById(bundle.getAccountId());
            if (account != null) {
                accountKey = account.getExternalKey();
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
        final BusinessSubscription prevSubscription;
        if (previousEffectiveTransitionTime == null) {
            prevSubscription = null;
        }
        else {
            prevSubscription = new BusinessSubscription(transition.getPreviousPriceList(), transition.getPreviousPlan(), transition.getPreviousPhase(), currency, previousEffectiveTransitionTime, transition.getPreviousState(), transition.getSubscriptionId(), transition.getBundleId());
        }
        final BusinessSubscription nextSubscription;

        // next plan is null for CANCEL events
        if (transition.getNextPlan() == null) {
            nextSubscription = null;
        }
        else {
            nextSubscription = new BusinessSubscription(transition.getNextPriceList(), transition.getNextPlan(), transition.getNextPhase(), currency, transition.getEffectiveTransitionTime(), transition.getNextState(), transition.getSubscriptionId(), transition.getBundleId());
        }

        record(transition.getId(), transitionKey, accountKey, transition.getRequestedTransitionTime(), event, prevSubscription, nextSubscription);
    }

    // Public for internal reasons
    public void record(final UUID id, final String key, final String accountKey, final DateTime requestedDateTime, final BusinessSubscriptionEvent event, final BusinessSubscription prevSubscription, final BusinessSubscription nextSubscription)
    {
        final BusinessSubscriptionTransition transition = new BusinessSubscriptionTransition(
            id,
            key,
            accountKey,
            requestedDateTime,
            event,
            prevSubscription,
            nextSubscription
        );

        log.info(transition.getEvent() + " " + transition);
        dao.createTransition(transition);
    }
}
