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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class DateRangeHashMap extends HashMap<UUID, List<DateRange>> {
    public void collapseDateRanges() {
        for (UUID key: this.keySet()) {
            List<DateRange> dateRanges = this.get(key);

            if (dateRanges.size() > 1) {
                List<DateRange> newDateRanges = collapseDateRanges(dateRanges);

                this.put(key, newDateRanges);
            }
        }
    }

    private List<DateRange> collapseDateRanges(List<DateRange> dateRanges) {
        if (dateRanges.size() < 2) {return dateRanges;}

        int originalSize = dateRanges.size();
        List<DateRange> newDateRanges = new ArrayList<DateRange>();

        DateRange range = dateRanges.get(0);

        for (int i = 1; i < dateRanges.size(); i++) {
            DateRange thisRange = dateRanges.get(i);
            if (range.overlaps(thisRange)) {
                range = range.calculateUnionWith(thisRange);
                newDateRanges.add(range);
            } else {
                newDateRanges.add(range);
                range = thisRange;
            }
        }

        if (originalSize > newDateRanges.size()) {
            newDateRanges = collapseDateRanges(newDateRanges);
        }

        return newDateRanges;
    }
}
