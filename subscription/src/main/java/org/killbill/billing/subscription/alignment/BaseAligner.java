/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.subscription.alignment;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Duration;

public class BaseAligner {

    protected DateTime addDuration(final DateTime input, final Duration duration) {
        return addOrRemoveDuration(input, duration, true);
    }

    protected DateTime removeDuration(final DateTime input, final Duration duration) {
        return addOrRemoveDuration(input, duration, false);
    }

    private DateTime addOrRemoveDuration(final DateTime input, final Duration duration, final boolean add) {
        return add ? input.plus(duration.toJodaPeriod()) : input.minus(duration.toJodaPeriod());
    }
}
