/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.entitlement.api;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.clock.Clock;

public class EntitlementDateHelper {

    public EntitlementDateHelper() {
    }

    public DateTime fromLocalDateAndReferenceTime(@Nullable final LocalDate requestedDate, final DateTime now, final InternalTenantContext callContext) throws EntitlementApiException {
        return requestedDate == null ? now : callContext.toUTCDateTime(requestedDate);
    }

    public DateTime fromLocalDateAndReferenceTimeWithMinimum(@Nullable final LocalDate requestedDate, final DateTime min, final DateTime now, final InternalTenantContext callContext) throws EntitlementApiException {
        final DateTime candidate = fromLocalDateAndReferenceTime(requestedDate, now, callContext);
        return candidate.compareTo(min) < 0 ? min : candidate;
    }
}
