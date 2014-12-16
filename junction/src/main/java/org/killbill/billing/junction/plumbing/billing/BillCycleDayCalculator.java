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

package org.killbill.billing.junction.plumbing.billing;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

public class BillCycleDayCalculator {

    private static final Logger log = LoggerFactory.getLogger(BillCycleDayCalculator.class);

    private final CatalogService catalogService;
    private final SubscriptionBaseInternalApi subscriptionApi;

    @Inject
    public BillCycleDayCalculator(final CatalogService catalogService, final SubscriptionBaseInternalApi subscriptionApi) {
        this.catalogService = catalogService;
        this.subscriptionApi = subscriptionApi;
    }

    protected int calculateBcd(final UUID bundleId, final SubscriptionBase subscription, final EffectiveSubscriptionInternalEvent transition, final Account account, final InternalCallContext context)
            throws CatalogApiException, AccountApiException, SubscriptionBaseApiException {

        final Catalog catalog = catalogService.getFullCatalog(context);

        final Plan prevPlan = (transition.getPreviousPlan() != null) ? catalog.findPlan(transition.getPreviousPlan(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;
        final Plan nextPlan = (transition.getNextPlan() != null) ? catalog.findPlan(transition.getNextPlan(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final Plan plan = (transition.getTransitionType() != SubscriptionBaseTransitionType.CANCEL) ? nextPlan : prevPlan;
        final Product product = plan.getProduct();

        final PlanPhase prevPhase = (transition.getPreviousPhase() != null) ? catalog.findPhase(transition.getPreviousPhase(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;
        final PlanPhase nextPhase = (transition.getNextPhase() != null) ? catalog.findPhase(transition.getNextPhase(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final PlanPhase phase = (transition.getTransitionType() != SubscriptionBaseTransitionType.CANCEL) ? nextPhase : prevPhase;

        final BillingPeriod billingPeriod = phase.getRecurring() != null ? phase.getRecurring().getBillingPeriod() : BillingPeriod.NO_BILLING_PERIOD;
        final BillingAlignment alignment = catalog.billingAlignment(
                new PlanPhaseSpecifier(product.getName(),
                                       product.getCategory(),
                                       billingPeriod,
                                       transition.getNextPriceList(),
                                       phase.getPhaseType()),
                transition.getRequestedTransitionTime());

        return calculateBcdForAlignment(alignment, bundleId, subscription, account, catalog, plan, context);
    }

    @VisibleForTesting
    int calculateBcdForAlignment(final BillingAlignment alignment, final UUID bundleId, final SubscriptionBase subscription,
                                 final Account account, final Catalog catalog, final Plan plan, final InternalCallContext context) throws AccountApiException, SubscriptionBaseApiException, CatalogApiException {
        int result = 0;
        switch (alignment) {
            case ACCOUNT:
                result = account.getBillCycleDayLocal();
                if (result == 0) {
                    result = calculateBcdFromSubscription(subscription, plan, account, catalog, context);
                }
                break;
            case BUNDLE:
                final SubscriptionBase baseSub = subscriptionApi.getBaseSubscription(bundleId, context);
                Plan basePlan = baseSub.getCurrentPlan();
                if (basePlan == null) {
                    // The BP has been cancelled
                    basePlan = baseSub.getLastActivePlan();
                }
                result = calculateBcdFromSubscription(baseSub, basePlan, account, catalog, context);
                break;
            case SUBSCRIPTION:
                result = calculateBcdFromSubscription(subscription, plan, account, catalog, context);
                break;
        }

        if (result == 0) {
            throw new CatalogApiException(ErrorCode.CAT_INVALID_BILLING_ALIGNMENT, alignment.toString());
        }

        return result;
    }

    @VisibleForTesting
    int calculateBcdFromSubscription(final SubscriptionBase subscription, final Plan plan, final Account account, final Catalog catalog, final InternalCallContext context)
            throws AccountApiException, CatalogApiException {
        // Retrieve the initial phase type for that subscription
        // TODO - this should be extracted somewhere, along with this code above
        final PhaseType initialPhaseType;
        final List<EffectiveSubscriptionInternalEvent> transitions = subscriptionApi.getAllTransitions(subscription, context);
        if (transitions.size() == 0) {
            initialPhaseType = null;
        } else {
            final DateTime requestedDate = subscription.getStartDate();
            final String initialPhaseString = transitions.get(0).getNextPhase();
            if (initialPhaseString == null) {
                initialPhaseType = null;
            } else {
                final PlanPhase initialPhase = catalog.findPhase(initialPhaseString, requestedDate, subscription.getStartDate());
                if (initialPhase == null) {
                    initialPhaseType = null;
                } else {
                    initialPhaseType = initialPhase.getPhaseType();
                }
            }
        }

        final DateTime date = plan.dateOfFirstRecurringNonZeroCharge(subscription.getStartDate(), initialPhaseType);
        final int bcdUTC = date.toDateTime(DateTimeZone.UTC).getDayOfMonth();
        final int bcdLocal = date.toDateTime(account.getTimeZone()).getDayOfMonth();
        log.info("Calculated BCD: subscription id {}, subscription start {}, timezone {}, bcd UTC {}, bcd local {}",
                 subscription.getId(), date.toDateTimeISO(), account.getTimeZone(), bcdUTC, bcdLocal);

        return bcdLocal;
    }
}
