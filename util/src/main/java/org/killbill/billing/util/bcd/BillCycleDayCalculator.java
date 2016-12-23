/*
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

package org.killbill.billing.util.bcd;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.clock.ClockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BillCycleDayCalculator {

    private static final Logger log = LoggerFactory.getLogger(BillCycleDayCalculator.class);

    public static int calculateBcdForAlignment(@Nullable final Map<UUID, Integer> bcdCache, final SubscriptionBase subscription, final SubscriptionBase baseSubscription, final BillingAlignment alignment, final DateTimeZone accountTimeZone, final int accountBillCycleDayLocal) {
        int result = 0;
        switch (alignment) {
            case ACCOUNT:
                result = accountBillCycleDayLocal != 0 ? accountBillCycleDayLocal : calculateOrRetrieveBcdFromSubscription(bcdCache, subscription, accountTimeZone);
                break;
            case BUNDLE:
                result = calculateOrRetrieveBcdFromSubscription(bcdCache, baseSubscription, accountTimeZone);
                break;
            case SUBSCRIPTION:
                result = calculateOrRetrieveBcdFromSubscription(bcdCache, subscription, accountTimeZone);
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
        if (proposedBillCycleDate < billingCycleDay && billingCycleDay <= lastDayOfMonth) {
            proposedBillCycleDate = billingCycleDay;
        }
        return new LocalDate(proposedDate.getYear(), proposedDate.getMonthOfYear(), proposedBillCycleDate, proposedDate.getChronology());
    }

    public static LocalDate alignProposedBillCycleDate(final DateTime proposedDate, final int billingCycleDay, final BillingPeriod billingPeriod, final DateTimeZone accountTimeZone) {
        final LocalDate proposedLocalDate = ClockUtil.toLocalDate(proposedDate, accountTimeZone);
        final LocalDate resultingLocalDate = alignProposedBillCycleDate(proposedLocalDate, billingCycleDay, billingPeriod);
        return resultingLocalDate;
    }

    private static int calculateOrRetrieveBcdFromSubscription(@Nullable final Map<UUID, Integer> bcdCache, final SubscriptionBase subscription, final DateTimeZone accountTimeZone) {
        Integer result = bcdCache != null ? bcdCache.get(subscription.getId()) : null;
        if (result == null) {
            result = calculateBcdFromSubscription(subscription, accountTimeZone);
            if (bcdCache != null) {
                bcdCache.put(subscription.getId(), result);
            }
        }
        return result;
    }

    private static int calculateBcdFromSubscription(final SubscriptionBase subscription, final DateTimeZone accountTimeZone) {
        final DateTime date = subscription.getDateOfFirstRecurringNonZeroCharge();
        final int bcdLocal = ClockUtil.toDateTime(date, accountTimeZone).getDayOfMonth();
        log.debug("Calculated BCD: subscriptionId='{}', subscriptionStartDate='{}', accountTimeZone='{}', bcd='{}'",
                  subscription.getId(), date.toDateTimeISO(), accountTimeZone, bcdLocal);
        return bcdLocal;
    }
}
