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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.model.BusinessSubscription;
import com.ning.billing.analytics.model.BusinessSubscriptionEvent;
import com.ning.billing.analytics.model.BusinessSubscriptionTransition;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

import com.google.inject.Inject;

public class BusinessSubscriptionTransitionDao {

    private static final Logger log = LoggerFactory.getLogger(BusinessSubscriptionTransitionDao.class);

    private final BusinessSubscriptionTransitionSqlDao sqlDao;
    private final EntitlementInternalApi entitlementApi;
    private final AccountInternalApi accountApi;
    private final CatalogService catalogService;
    private final Clock clock;

    @Inject
    public BusinessSubscriptionTransitionDao(final BusinessSubscriptionTransitionSqlDao sqlDao,
                                             final CatalogService catalogService,
                                             final EntitlementInternalApi entitlementApi,
                                             final AccountInternalApi accountApi,
                                             final Clock clock) {
        this.sqlDao = sqlDao;
        this.catalogService = catalogService;
        this.entitlementApi = entitlementApi;
        this.accountApi = accountApi;
        this.clock = clock;
    }

    public void rebuildTransitionsForBundle(final UUID bundleId, final InternalCallContext context) {
        final SubscriptionBundle bundle;
        try {
            bundle = entitlementApi.getBundleFromId(bundleId, context);
        } catch (EntitlementUserApiException e) {
            log.warn("Ignoring update for bundle {}: bundle does not exist", bundleId);
            return;
        }

        final Account account;
        try {
            account = accountApi.getAccountById(bundle.getAccountId(), context);
        } catch (AccountApiException e) {
            log.warn("Ignoring update for bundle {}: account {} does not exist", bundleId, bundle.getAccountId());
            return;
        }

        final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundleId, context);

        final Currency currency = account.getCurrency();

        sqlDao.inTransaction(new Transaction<Void, BusinessSubscriptionTransitionSqlDao>() {
            @Override
            public Void inTransaction(final BusinessSubscriptionTransitionSqlDao transactional, final TransactionStatus status) throws Exception {
                log.info("Started rebuilding transitions for bundle id {}", bundleId);
                transactional.deleteTransitionsForBundle(bundleId.toString(), context);

                final ArrayList<BusinessSubscriptionTransition> transitions = new ArrayList<BusinessSubscriptionTransition>();
                for (final Subscription subscription : subscriptions) {
                    for (final EffectiveSubscriptionEvent event : subscription.getAllTransitions()) {
                        final BusinessSubscriptionEvent businessEvent = getBusinessSubscriptionFromEvent(event);
                        if (businessEvent == null) {
                            continue;
                        }

                        final BusinessSubscription prevSubscription = createPreviousBusinessSubscription(event, businessEvent, transitions, currency);
                        final BusinessSubscription nextSubscription = createNextBusinessSubscription(event, businessEvent, currency);
                        final BusinessSubscriptionTransition transition = new BusinessSubscriptionTransition(
                                event.getTotalOrdering(),
                                bundleId,
                                bundle.getKey(),
                                bundle.getAccountId(),
                                account.getExternalKey(),
                                subscription.getId(),
                                event.getRequestedTransitionTime(),
                                businessEvent,
                                prevSubscription,
                                nextSubscription
                        );

                        transactional.createTransition(transition, context);
                        transitions.add(transition);
                        log.info("Adding transition {}", transition);

                        // We need to manually add the system cancel event
                        if (SubscriptionTransitionType.CANCEL.equals(event.getTransitionType()) &&
                            clock.getUTCNow().isAfter(event.getEffectiveTransitionTime())) {
                            final BusinessSubscriptionTransition systemCancelTransition = new BusinessSubscriptionTransition(
                                    event.getTotalOrdering(),
                                    bundleId,
                                    bundle.getKey(),
                                    bundle.getAccountId(),
                                    account.getExternalKey(),
                                    subscription.getId(),
                                    // Note! The system cancel event requested time is the effective time when the subscription
                                    // is cancelled, which is the effective time of the cancel event
                                    event.getEffectiveTransitionTime(),
                                    new BusinessSubscriptionEvent(BusinessSubscriptionEvent.EventType.SYSTEM_CANCEL, businessEvent.getCategory()),
                                    prevSubscription,
                                    nextSubscription
                            );
                            transactional.createTransition(systemCancelTransition, context);
                            transitions.add(systemCancelTransition);
                            log.info("Adding transition {}", systemCancelTransition);
                        }
                    }
                }

                log.info("Finished rebuilding transitions for bundle id {}", bundleId);
                return null;
            }
        });
    }

    private BusinessSubscriptionEvent getBusinessSubscriptionFromEvent(final SubscriptionEvent event) throws AccountApiException, EntitlementUserApiException {
        switch (event.getTransitionType()) {
            // A subscription enters either through migration or as newly created subscription
            case MIGRATE_ENTITLEMENT:
                return subscriptionMigrated(event);
            case CREATE:
                return subscriptionCreated(event);
            case RE_CREATE:
                return subscriptionRecreated(event);
            case TRANSFER:
                return subscriptionTransfered(event);
            case CANCEL:
                return subscriptionCancelled(event);
            case CHANGE:
                return subscriptionChanged(event);
            case PHASE:
                return subscriptionPhaseChanged(event);
            // TODO - should we really ignore these?
            case MIGRATE_BILLING:
            case UNCANCEL:
                return null;
            default:
                log.warn("Unexpected event type " + event.getTransitionType());
                return null;
        }
    }

    private BusinessSubscriptionEvent subscriptionMigrated(final SubscriptionEvent created) throws AccountApiException, EntitlementUserApiException {
        return BusinessSubscriptionEvent.subscriptionMigrated(created.getNextPlan(), catalogService.getFullCatalog(), created.getEffectiveTransitionTime(), created.getSubscriptionStartDate());
    }

    private BusinessSubscriptionEvent subscriptionCreated(final SubscriptionEvent created) throws AccountApiException, EntitlementUserApiException {
        return BusinessSubscriptionEvent.subscriptionCreated(created.getNextPlan(), catalogService.getFullCatalog(), created.getEffectiveTransitionTime(), created.getSubscriptionStartDate());
    }

    private BusinessSubscriptionEvent subscriptionRecreated(final SubscriptionEvent recreated) throws AccountApiException, EntitlementUserApiException {
        return BusinessSubscriptionEvent.subscriptionRecreated(recreated.getNextPlan(), catalogService.getFullCatalog(), recreated.getEffectiveTransitionTime(), recreated.getSubscriptionStartDate());
    }

    private BusinessSubscriptionEvent subscriptionTransfered(final SubscriptionEvent transfered) throws AccountApiException, EntitlementUserApiException {
        return BusinessSubscriptionEvent.subscriptionTransfered(transfered.getNextPlan(), catalogService.getFullCatalog(), transfered.getEffectiveTransitionTime(), transfered.getSubscriptionStartDate());
    }

    private BusinessSubscriptionEvent subscriptionCancelled(final SubscriptionEvent cancelled) throws AccountApiException, EntitlementUserApiException {
        // cancelled.getNextPlan() is null here - need to look at the previous one to create the correct event name
        return BusinessSubscriptionEvent.subscriptionCancelled(cancelled.getPreviousPlan(), catalogService.getFullCatalog(), cancelled.getEffectiveTransitionTime(), cancelled.getSubscriptionStartDate());
    }

    private BusinessSubscriptionEvent subscriptionChanged(final SubscriptionEvent changed) throws AccountApiException, EntitlementUserApiException {
        return BusinessSubscriptionEvent.subscriptionChanged(changed.getNextPlan(), catalogService.getFullCatalog(), changed.getEffectiveTransitionTime(), changed.getSubscriptionStartDate());
    }

    private BusinessSubscriptionEvent subscriptionPhaseChanged(final SubscriptionEvent phaseChanged) throws AccountApiException, EntitlementUserApiException {
        return BusinessSubscriptionEvent.subscriptionPhaseChanged(phaseChanged.getNextPlan(), phaseChanged.getNextState(), catalogService.getFullCatalog(), phaseChanged.getEffectiveTransitionTime(), phaseChanged.getSubscriptionStartDate());
    }

    private BusinessSubscription createNextBusinessSubscription(final EffectiveSubscriptionEvent event, final BusinessSubscriptionEvent businessEvent, final Currency currency) {
        final BusinessSubscription nextSubscription;
        if (BusinessSubscriptionEvent.EventType.CANCEL.equals(businessEvent.getEventType()) ||
            BusinessSubscriptionEvent.EventType.SYSTEM_CANCEL.equals(businessEvent.getEventType())) {
            nextSubscription = null;
        } else {
            nextSubscription = new BusinessSubscription(event.getNextPriceList(), event.getNextPlan(), event.getNextPhase(),
                                                        currency, event.getEffectiveTransitionTime(), event.getNextState(),
                                                        catalogService.getFullCatalog());
        }

        return nextSubscription;
    }

    private BusinessSubscription createPreviousBusinessSubscription(final EffectiveSubscriptionEvent event,
                                                                    final BusinessSubscriptionEvent businessEvent,
                                                                    final ArrayList<BusinessSubscriptionTransition> transitions,
                                                                    final Currency currency) {
        if (BusinessSubscriptionEvent.EventType.MIGRATE.equals(businessEvent.getEventType()) ||
            BusinessSubscriptionEvent.EventType.ADD.equals(businessEvent.getEventType()) ||
            BusinessSubscriptionEvent.EventType.RE_ADD.equals(businessEvent.getEventType()) ||
            BusinessSubscriptionEvent.EventType.TRANSFER.equals(businessEvent.getEventType())) {
            return null;
        }

        final BusinessSubscriptionTransition prevTransition = getPreviousBusinessSubscriptionTransitionForEvent(event, transitions);
        return new BusinessSubscription(event.getPreviousPriceList(), event.getPreviousPlan(), event.getPreviousPhase(),
                                        currency, prevTransition.getNextSubscription().getStartDate(), event.getPreviousState(),
                                        catalogService.getFullCatalog());
    }

    private BusinessSubscriptionTransition getPreviousBusinessSubscriptionTransitionForEvent(final EffectiveSubscriptionEvent event,
                                                                                             final ArrayList<BusinessSubscriptionTransition> transitions) {
        BusinessSubscriptionTransition transition = null;
        for (final BusinessSubscriptionTransition candidate : transitions) {
            final BusinessSubscription nextSubscription = candidate.getNextSubscription();
            if (nextSubscription == null || !nextSubscription.getStartDate().isBefore(event.getEffectiveTransitionTime())) {
                continue;
            }

            if (candidate.getSubscriptionId().equals(event.getSubscriptionId())) {
                transition = candidate;
            }
        }

        if (transition == null) {
            log.error("Unable to retrieve the previous transition - THIS SHOULD NEVER HAPPEN");
            // Fall back to the latest one?
            transition = transitions.get(transitions.size() - 1);
        }

        return transition;
    }
}
