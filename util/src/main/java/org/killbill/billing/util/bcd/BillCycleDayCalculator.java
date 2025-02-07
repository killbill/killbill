/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.util.bcd;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.commons.utils.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BillCycleDayCalculator {

    private static final Logger log = LoggerFactory.getLogger(BillCycleDayCalculator.class);

    public static int calculateBcdForAlignment(@Nullable final Map<UUID, Integer> bcdCache, final SubscriptionBase subscription, final SubscriptionBase baseSubscription, final BillingAlignment alignment, final InternalTenantContext internalTenantContext, final int accountBillCycleDayLocal) {
        int result = 0;
        switch (alignment) {
            case ACCOUNT:
                Preconditions.checkState(accountBillCycleDayLocal != 0, "Account BCD should be set at this point");
                result = accountBillCycleDayLocal;
                break;
            case BUNDLE:
                result = calculateOrRetrieveBcdFromSubscription(bcdCache, baseSubscription, internalTenantContext);
                break;
            case SUBSCRIPTION:
                result = calculateOrRetrieveBcdFromSubscription(bcdCache, subscription, internalTenantContext);
                break;
        }
        return result;
    }

    public static LocalDate alignProposedBillCycleDate(final LocalDate proposedDate, final int billingCycleDay, final BillingPeriod billingPeriod) {
        // billingCycleDay alignment only makes sense for month based BillingPeriod (MONTHLY, QUARTERLY, BIANNUAL, ANNUAL)
        final boolean isMonthBased = (billingPeriod.getPeriod().getMonths() | billingPeriod.getPeriod().getYears()) > 0;
        if (!isMonthBased) {
            return proposedDate;
        }
        final int lastDayOfMonth = proposedDate.dayOfMonth().getMaximumValue();
        int proposedBillCycleDate = proposedDate.getDayOfMonth();
        if (billingCycleDay <= lastDayOfMonth) {
            proposedBillCycleDate = billingCycleDay;
        } else {
            proposedBillCycleDate = lastDayOfMonth;
        }
        return new LocalDate(proposedDate.getYear(), proposedDate.getMonthOfYear(), proposedBillCycleDate, proposedDate.getChronology());
    }

    public static LocalDate alignProposedBillCycleDate(final DateTime proposedDate, final int billingCycleDay, final BillingPeriod billingPeriod, final InternalTenantContext internalTenantContext) {
        final LocalDate proposedLocalDate = internalTenantContext.toLocalDate(proposedDate);
        final LocalDate resultingLocalDate = alignProposedBillCycleDate(proposedLocalDate, billingCycleDay, billingPeriod);
        return resultingLocalDate;
    }


    private static LocalDate alignToNextBillCycleDate(final LocalDate prevTransitionDate, final LocalDate curTransitionDate, /* date to be aligned */ final int billingCycleDay, final BillingPeriod billingPeriod) {
        // Trivial case, nothing to align with
        if (billingPeriod == BillingPeriod.NO_BILLING_PERIOD) {
            return curTransitionDate;
        }
        // billingCycleDay alignment only makes sense for month based BillingPeriod (MONTHLY, QUARTERLY, BIANNUAL, ANNUAL)
        final boolean isMonthBased = (billingPeriod.getPeriod().getMonths() | billingPeriod.getPeriod().getYears()) > 0;
        if (!isMonthBased) {
            LocalDate newDate = prevTransitionDate.plus(billingPeriod.getPeriod());
            while (newDate.isBefore(curTransitionDate)) {
                newDate = newDate.plus(billingPeriod.getPeriod());
            }
            return newDate;
        }
        if (curTransitionDate.getDayOfMonth() > billingCycleDay) {
            return alignProposedBillCycleDate(curTransitionDate.plusMonths(1), billingCycleDay, billingPeriod);
        } else {
            return alignProposedBillCycleDate(curTransitionDate, billingCycleDay, billingPeriod);
        }
    }

    public static LocalDate alignToNextBillCycleDate(final DateTime prevTransitionDate, final DateTime curTransitionDate,/* date to be aligned */ final int billingCycleDay, final BillingPeriod billingPeriod, final InternalTenantContext internalTenantContext) {
        return alignToNextBillCycleDate(internalTenantContext.toLocalDate(prevTransitionDate), internalTenantContext.toLocalDate(curTransitionDate), billingCycleDay, billingPeriod);
    }

    private static int calculateOrRetrieveBcdFromSubscription(@Nullable final Map<UUID, Integer> bcdCache, final SubscriptionBase subscription, final InternalTenantContext internalTenantContext) {
        Integer result = bcdCache != null ? bcdCache.get(subscription.getId()) : null;
        if (result == null) {
            result = calculateBcdFromSubscription(subscription, internalTenantContext);
            if (bcdCache != null) {
                bcdCache.put(subscription.getId(), result);
            }
        }
        return result;
    }

    private static int calculateBcdFromSubscription(final SubscriptionBase subscription, final InternalTenantContext internalTenantContext) {
        final DateTime date = subscription.getDateOfFirstRecurringNonZeroCharge();
        final int bcdLocal = internalTenantContext.toLocalDate(date).getDayOfMonth();
        log.debug("Calculated BCD: subscriptionId='{}', subscriptionStartDate='{}', bcd='{}'",
                  subscription.getId(), date.toDateTimeISO(), bcdLocal);
        return bcdLocal;
    }
}
