/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2014-2023 The Billing Project, LLC
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

package org.killbill.billing.invoice.usage;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.InvoiceConfig.AccountTzOffset;
import org.killbill.clock.ClockUtil;

// * AccountTzOffset=FIXED:
// We use the same logic to compute a LocalDate from a DateTime as we use in the rest of the invoice code.
// This is the default.
//
// * AccountTzOffset=VARIABLE:
// We use a different logic for usage code to convert dates to guarantee some stability
// across accounts created with the same TZ but at different dates during the year (daylight saving or standard).
// See https://github.com/killbill/killbill/issues/1934 for an example of a scenario
//
// Instead of using the 'fixedOffsetTimeZone' from our context like we do in the rest of our code,
// we use recompute the offset based on the effective date of the event (transition, targetDate, ...)
// which means 2 accounts with same TZ would see the same results (given similar subscription, and usage points)
//
public class UsageClockUtil {

    private final InvoiceConfig config;

    public UsageClockUtil(final InvoiceConfig config) {
        this.config = config;
    }

    private DateTimeZone getEffectiveDateTimeZone(final InternalTenantContext context) {
        final AccountTzOffset mode = config.getAccountTzOffsetMode(context);
        if (mode == AccountTzOffset.VARIABLE) {
            return context.getAccountTimeZone();
        } else {
            return context.getFixedOffsetTimeZone();
        }
    }
    public DateTime toDateTimeAtStartOfDay(final LocalDate input, final InternalTenantContext context) {
        return toDateTimeAtStartOfDay(input, getEffectiveDateTimeZone(context));
    }

    private DateTime toDateTimeAtStartOfDay(final LocalDate input, final DateTimeZone refTz) {

        // We compute the date at the beginning of the day based on the account TZ
        //
        // We do not use the 'fixedOffsetTimeZone' from the context as it would produce different dates for different accounts
        // (started at different time) during the year (daylight saving)
        //
        // So the 'tz' value is computed based on the input date (which is the same across all accounts, typically a transition or targetDate)
        final DateTimeZone tz = DateTimeZone.forOffsetMillis(refTz.getOffset(input.toDateTimeAtStartOfDay(refTz).getMillis()));
        return input.toDateTimeAtStartOfDay(tz).toDateTime(DateTimeZone.UTC);
    }

    public DateTime toDateTimeAtEndOfDay(final LocalDate input, final InternalTenantContext context) {
        return toDateTimeAtEndOfDay(input, getEffectiveDateTimeZone(context));
    }

    private DateTime toDateTimeAtEndOfDay(final LocalDate input, final DateTimeZone refTz) {
        // final DateTime targetDateEndOfDay = toEndOfDay(targetDate, internalTenantContext.getFixedOffsetTimeZone());
        DateTime dateTimeAtStartOfDay = toDateTimeAtStartOfDay(input, refTz);
        return dateTimeAtStartOfDay.plusDays(1).minus(Period.millis(1)).toDateTime(DateTimeZone.UTC);
    }

    public LocalDate toLocalDate(final DateTime input, final InternalTenantContext context) {
        return toLocalDate(input, getEffectiveDateTimeZone(context));
    }

    private LocalDate toLocalDate(final DateTime input, final DateTimeZone refTz) {
        return ClockUtil.toLocalDate(input, refTz);
    }
}
