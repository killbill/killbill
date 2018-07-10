/*
 * Copyright 2016-2017 Groupon, Inc
 * Copyright 2016-2017 The Billing Project, LLC
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

package org.killbill.billing.util.account;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.TimeZoneAwareEntity;

public abstract class AccountDateTimeUtils {

    public static DateTimeZone getFixedOffsetTimeZone(final TimeZoneAwareEntity account) {
        return getFixedOffsetTimeZone(account.getTimeZone(), account.getReferenceTime());
    }

    public static DateTimeZone getFixedOffsetTimeZone(final Account account) {
        return getFixedOffsetTimeZone(account.getTimeZone(), account.getReferenceTime());
    }

    private static DateTimeZone getFixedOffsetTimeZone(final DateTimeZone referenceDateTimeZone, final DateTime referenceDateTime) {
        // Check if DST was in effect at the reference date time
        final boolean shouldUseDST = !referenceDateTimeZone.isStandardOffset(referenceDateTime.getMillis());
        if (shouldUseDST) {
            return DateTimeZone.forOffsetMillis(referenceDateTimeZone.getOffset(referenceDateTime.getMillis()));
        } else {
            return DateTimeZone.forOffsetMillis(referenceDateTimeZone.getStandardOffset(referenceDateTime.getMillis()));
        }
    }
}
