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

package org.killbill.billing.util.config;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.joda.time.Period;
import org.skife.config.TimeSpan;

public abstract class TimeSpanConverter {

    public static Period toPeriod(final TimeSpan timeSpan) {
        // Will truncate millis
        final int delaySec = (int) TimeUnit.SECONDS.convert(timeSpan.getMillis(), TimeUnit.MILLISECONDS);
        return new Period().withSeconds(delaySec);
    }

    public static List<Period> toListPeriod(final List<TimeSpan> timeSpans) {
        return timeSpans == null || timeSpans.isEmpty() ?
               Collections.emptyList() :
               timeSpans.stream().map(TimeSpanConverter::toPeriod).collect(Collectors.toList());
    }
}
