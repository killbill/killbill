/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.entitlement.alignment;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Duration;

public class BaseAligner {

    protected DateTime addDuration(final DateTime input, final Duration duration) {
        return addOrRemoveDuration(input, duration, true);
    }

    protected DateTime removeDuration(final DateTime input, final Duration duration) {
        return addOrRemoveDuration(input, duration, false);
    }

    private DateTime addOrRemoveDuration(final DateTime input, final Duration duration, boolean add) {
        DateTime result = input;
        switch (duration.getUnit()) {
            case DAYS:
                result = add ? result.plusDays(duration.getNumber()) : result.minusDays(duration.getNumber());
                ;
                break;

            case MONTHS:
                result = add ? result.plusMonths(duration.getNumber()) : result.minusMonths(duration.getNumber());
                break;

            case YEARS:
                result = add ? result.plusYears(duration.getNumber()) : result.minusYears(duration.getNumber());
                break;
            case UNLIMITED:
            default:
                throw new RuntimeException("Trying to move to unlimited time period");
        }
        return result;
    }
}
