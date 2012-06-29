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

import java.util.List;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.model.BusinessSubscription;
import com.ning.billing.analytics.model.BusinessSubscriptionEvent;
import com.ning.billing.analytics.model.BusinessSubscriptionTransition;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.RequestedSubscriptionEvent;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;

public class BusinessSubscriptionTransitionRecorder {
    private static final Logger log = LoggerFactory.getLogger(BusinessSubscriptionTransitionRecorder.class);

    private final BusinessSubscriptionTransitionSqlDao sqlDao;
    private final EntitlementUserApi entitlementApi;
    private final AccountUserApi accountApi;
    private final CatalogService catalogService;

    @Inject
    public BusinessSubscriptionTransitionRecorder(final BusinessSubscriptionTransitionSqlDao sqlDao, final CatalogService catalogService, final EntitlementUserApi entitlementApi, final AccountUserApi accountApi) {
        this.sqlDao = sqlDao;
        this.catalogService = catalogService;
        this.entitlementApi = entitlementApi;
        this.accountApi = accountApi;
    }

    public void subscriptionCreated(final EffectiveSubscriptionEvent created) throws AccountApiException, EntitlementUserApiException {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCreated(created.getNextPlan(), catalogService.getFullCatalog(), created.getEffectiveTransitionTime(), created.getSubscriptionStartDate());
        recordTransition(event, created);
    }

    public void subscriptionRecreated(final EffectiveSubscriptionEvent recreated) throws AccountApiException, EntitlementUserApiException {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionRecreated(recreated.getNextPlan(), catalogService.getFullCatalog(), recreated.getEffectiveTransitionTime(), recreated.getSubscriptionStartDate());
        recordTransition(event, recreated);
    }

    public void subscriptionCancelled(final EffectiveSubscriptionEvent cancelled) throws AccountApiException, EntitlementUserApiException {
        // cancelled.getNextPlan() is null here - need to look at the previous one to create the correct event name
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCancelled(cancelled.getPreviousPlan(), catalogService.getFullCatalog(), cancelled.getEffectiveTransitionTime(), cancelled.getSubscriptionStartDate());
        recordTransition(event, cancelled);
    }

    public void subscriptionChanged(final RequestedSubscriptionEvent changed) throws EntitlementUserApiException, AccountApiException {
        // Future change
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionChanged(changed.getNextPlan(), catalogService.getFullCatalog(), changed.getEffectiveTransitionTime(), changed.getSubscriptionStartDate());
        recordTransition(event, changed);
    }

    public void subscriptionChanged(final EffectiveSubscriptionEvent changed) throws AccountApiException, EntitlementUserApiException {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionChanged(changed.getNextPlan(), catalogService.getFullCatalog(), changed.getEffectiveTransitionTime(), changed.getSubscriptionStartDate());
        recordTransition(event, changed);
    }

    public void subscriptionPhaseChanged(final EffectiveSubscriptionEvent phaseChanged) throws AccountApiException, EntitlementUserApiException {
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionPhaseChanged(phaseChanged.getNextPlan(), phaseChanged.getNextState(), catalogService.getFullCatalog(), phaseChanged.getEffectiveTransitionTime(), phaseChanged.getSubscriptionStartDate());
        recordTransition(event, phaseChanged);
    }

    void recordTransition(final BusinessSubscriptionEvent event, final SubscriptionEvent transition) throws AccountApiException, EntitlementUserApiException {
        Currency currency = null;
        String externalKey = null;
        String accountKey = null;

        // Retrieve key and currency via the bundle
        final SubscriptionBundle bundle = entitlementApi.getBundleFromId(transition.getBundleId());
        if (bundle != null) {
            externalKey = bundle.getKey();

            final Account account = accountApi.getAccountById(bundle.getAccountId());
            if (account != null) {
                accountKey = account.getExternalKey();
                currency = account.getCurrency();
            }
        }

        // The SubscriptionEvent interface gives us all the prev/next information we need but the start date
        // of the previous phase. We need to retrieve it from our own transitions table
        DateTime previousEffectiveTransitionTime = null;
        // For creation events, the prev subscription will always be null
        if (event.getEventType() != BusinessSubscriptionEvent.EventType.ADD) {
            final List<BusinessSubscriptionTransition> transitions = sqlDao.getTransitions(externalKey);
            if (transitions != null && transitions.size() > 0) {
                final BusinessSubscriptionTransition lastTransition = transitions.get(transitions.size() - 1);
                if (lastTransition != null && lastTransition.getNextSubscription() != null) {
                    previousEffectiveTransitionTime = lastTransition.getNextSubscription().getStartDate();
                }
            }
        }

        // TODO Support currency changes
        final BusinessSubscription prevSubscription;
        if (previousEffectiveTransitionTime == null) {
            prevSubscription = null;
        } else {
            prevSubscription = new BusinessSubscription(transition.getPreviousPriceList(), transition.getPreviousPlan(), transition.getPreviousPhase(), currency, previousEffectiveTransitionTime, transition.getPreviousState(), transition.getSubscriptionId(), transition.getBundleId(), catalogService.getFullCatalog());
        }
        final BusinessSubscription nextSubscription;

        // next plan is null for CANCEL events
        if (transition.getNextPlan() == null) {
            nextSubscription = null;
        } else {
            nextSubscription = new BusinessSubscription(transition.getNextPriceList(), transition.getNextPlan(), transition.getNextPhase(), currency, transition.getEffectiveTransitionTime(), transition.getNextState(), transition.getSubscriptionId(), transition.getBundleId(), catalogService.getFullCatalog());
        }

        catchUpIfNeededAndRecord(transition.getTotalOrdering(), externalKey, accountKey, transition.getRequestedTransitionTime(), event, prevSubscription, nextSubscription);
    }

    public void catchUpIfNeededAndRecord(final Long totalOrdering, final String externalKey, final String accountKey, final DateTime requestedDateTime,
                                         final BusinessSubscriptionEvent event, final BusinessSubscription prevSubscription, final BusinessSubscription nextSubscription) {
        // There is no ordering guaranteed with events on the bus. This can be problematic on e.g. subscription creation:
        // the requested future change from trial to evergreen could be received before the actual creation event.
        // In this case, we would have two subscriptions in BST, with both null for the previous transition.
        // To work around this, we need to update bst as we go
        if (BusinessSubscriptionEvent.EventType.ADD.equals(event.getEventType())) {
            final List<BusinessSubscriptionTransition> transitions = sqlDao.getTransitionForSubscription(nextSubscription.getSubscriptionId().toString());
            if (transitions != null && transitions.size() > 0) {
                final BusinessSubscriptionTransition firstTransition = transitions.get(0);
                if (firstTransition.getPreviousSubscription() == null) {
                    final BusinessSubscriptionTransition updatedFirstTransition = new BusinessSubscriptionTransition(
                            firstTransition.getTotalOrdering(),
                            firstTransition.getExternalKey(),
                            firstTransition.getAccountKey(),
                            firstTransition.getRequestedTimestamp(),
                            firstTransition.getEvent(),
                            nextSubscription,
                            firstTransition.getNextSubscription()
                    );
                    sqlDao.updateTransition(updatedFirstTransition.getTotalOrdering(), updatedFirstTransition);
                }
            }
        }

        record(totalOrdering, externalKey, accountKey, requestedDateTime, event, prevSubscription, nextSubscription);
    }

    // Public for internal reasons
    public void record(final Long totalOrdering, final String externalKey, final String accountKey, final DateTime requestedDateTime, final BusinessSubscriptionEvent event, final BusinessSubscription prevSubscription, final BusinessSubscription nextSubscription) {
        final BusinessSubscriptionTransition transition = new BusinessSubscriptionTransition(
                totalOrdering,
                externalKey,
                accountKey,
                requestedDateTime,
                event,
                prevSubscription,
                nextSubscription
        );

        log.info(transition.getEvent() + " " + transition);
        sqlDao.createTransition(transition);
    }
}
