/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.invoice.model;

import org.joda.time.DateTime;

public class DateRange {
    private final DateTime startDate;
    private final DateTime endDate;

    public DateRange(DateTime startDate, DateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    /**
     *
     * @param date the DateTime in question
     * @return whether the DateRange contains (inclusively) the DateTime in question
     */
    public boolean contains(DateTime date) {
        return (!date.isBefore(startDate)) && (!date.isAfter(endDate));
    }

    public boolean overlaps(DateRange range) {
        return (this.contains(range.getStartDate()) || this.contains(range.getEndDate()));
    }

    public DateRange calculateUnionWith(DateRange range) {
        if (this.contains(range.startDate) && this.contains(range.endDate)) {
            return this;
        }

        if (this.contains(range.startDate)) {
            return new DateRange(this.startDate, range.endDate);
        }

        if (this.contains(range.endDate)) {
            return new DateRange(range.startDate, this.endDate);
        }

        return null;
    }
}
