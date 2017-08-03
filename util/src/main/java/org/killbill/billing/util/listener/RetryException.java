/*
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

package org.killbill.billing.util.listener;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.Period;

public class RetryException extends RuntimeException {

    public static final List<Period> DEFAULT_RETRY_SCHEDULE = Arrays.asList(Period.minutes(5),
                                                                            Period.minutes(15),
                                                                            Period.hours(1),
                                                                            Period.hours(6),
                                                                            Period.hours(24));

    private final List<Period> retrySchedule;

    public RetryException() {
        this(null, null);
    }

    public RetryException(final Exception e) {
        this(e, null);
    }

    public RetryException(final Exception e, @Nullable final List<Period> retrySchedule) {
        super(e);
        this.retrySchedule = retrySchedule != null ? retrySchedule : DEFAULT_RETRY_SCHEDULE;
    }

    public List<Period> getRetrySchedule() {
        return retrySchedule;
    }

    @Override
    public String toString() {
        return String.format("%s (retrySchedule: %s)", super.toString(), retrySchedule);
    }
}
