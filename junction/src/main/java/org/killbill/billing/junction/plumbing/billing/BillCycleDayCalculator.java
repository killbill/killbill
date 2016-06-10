/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.junction.plumbing.billing;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.clock.ClockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected int calculateBcd(final ImmutableAccountData account, final int accountBillCycleDayLocal, final UUID bundleId, final SubscriptionBase subscription, final EffectiveSubscriptionInternalEvent transition, final InternalCallContext context)
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

        return calculateBcdForAlignment(account, accountBillCycleDayLocal, subscription, alignment, bundleId, context);
    }

    @VisibleForTesting
    int calculateBcdForAlignment(final ImmutableAccountData account, final int accountBillCycleDayLocal, final SubscriptionBase subscription, final BillingAlignment alignment, final UUID bundleId,
                                 final InternalCallContext context) throws AccountApiException, SubscriptionBaseApiException, CatalogApiException {
        int result = 0;
        switch (alignment) {
            case ACCOUNT:
                result = accountBillCycleDayLocal != 0 ? accountBillCycleDayLocal : calculateBcdFromSubscription(subscription, account);
                break;
            case BUNDLE:
                final SubscriptionBase baseSub = subscriptionApi.getBaseSubscription(bundleId, context);
                result = calculateBcdFromSubscription(baseSub, account);
                break;
            case SUBSCRIPTION:
                result = calculateBcdFromSubscription(subscription, account);
                break;
        }

        if (result == 0) {
            throw new CatalogApiException(ErrorCode.CAT_INVALID_BILLING_ALIGNMENT, alignment.toString());
        }

        return result;
    }

    @VisibleForTesting
    int calculateBcdFromSubscription(final SubscriptionBase subscription, final ImmutableAccountData account)
            throws AccountApiException, CatalogApiException {

        final DateTime date = subscription.getDateOfFirstRecurringNonZeroCharge();
        final int bcdLocal = ClockUtil.toDateTime(date, account.getTimeZone()).getDayOfMonth();
        log.info("Calculated BCD: subscriptionId='{}', subscriptionStartDate='{}', accountTimeZone='{}', bcd='{}'",
                 subscription.getId(), date.toDateTimeISO(), account.getTimeZone(), bcdLocal);
        return bcdLocal;
    }
}
