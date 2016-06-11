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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.clock.ClockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class BillCycleDayCalculator {

    private static final Logger log = LoggerFactory.getLogger(BillCycleDayCalculator.class);

    public BillCycleDayCalculator() {
    }

    public static int calculateBcdForAlignment(final SubscriptionBase subscription, final SubscriptionBase baseSubscription, final BillingAlignment alignment, final DateTimeZone accountTimeZone, final int accountBillCycleDayLocal) {
        int result = 0;
        switch (alignment) {
            case ACCOUNT:
                result = accountBillCycleDayLocal != 0 ? accountBillCycleDayLocal : calculateBcdFromSubscription(subscription, accountTimeZone);
                break;
            case BUNDLE:
                result = calculateBcdFromSubscription(baseSubscription, accountTimeZone);
                break;
            case SUBSCRIPTION:
                result = calculateBcdFromSubscription(subscription, accountTimeZone);
                break;
        }
        return result;
    }

    @VisibleForTesting
    static int calculateBcdFromSubscription(final SubscriptionBase subscription, final DateTimeZone accountTimeZone) {
        final DateTime date = subscription.getDateOfFirstRecurringNonZeroCharge();
        final int bcdLocal = ClockUtil.toDateTime(date, accountTimeZone).getDayOfMonth();
        log.info("Calculated BCD: subscriptionId='{}', subscriptionStartDate='{}', accountTimeZone='{}', bcd='{}'",
                 subscription.getId(), date.toDateTimeISO(), accountTimeZone, bcdLocal);
        return bcdLocal;
    }
}
