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

package org.killbill.billing.util.timezone;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.AccountDateAndTimeZoneContext;

/**
 * Used by junction and invoice to calculate:
 * - a LocalDate from DateTime and the timeZone set on the account
 * - A DateTime from a LocalDate and the referenceTime attached to the account.
 */
public final class DefaultAccountDateAndTimeZoneContext implements AccountDateAndTimeZoneContext {

    private final DateTime referenceTime;
    private final DateTimeZone accountTimeZone;
    private final InternalTenantContext internalTenantContext;

    public DefaultAccountDateAndTimeZoneContext(final DateTime referenceTime, final DateTimeZone accountTimeZone, final InternalTenantContext internalTenantContext) {
        this.referenceTime = referenceTime;
        this.accountTimeZone = accountTimeZone;
        this.internalTenantContext = internalTenantContext;
    }

    @Override
    public LocalDate computeLocalDateFromFixedAccountOffset(final DateTime targetDateTime) {
        return internalTenantContext.toLocalDate(targetDateTime, referenceTime, accountTimeZone);
    }

    @Override
    public DateTime computeUTCDateTimeFromLocalDate(final LocalDate invoiceItemEndDate) {
        return internalTenantContext.toUTCDateTime(invoiceItemEndDate, referenceTime, accountTimeZone);
    }

    @Override
    public DateTimeZone getAccountTimeZone() {
        return accountTimeZone;
    }
}
