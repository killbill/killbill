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

package org.killbill.billing.junction.plumbing.billing;

import org.joda.time.DateTime;

import com.google.common.base.Preconditions;

class DisabledDuration implements Comparable<DisabledDuration> {

    private final DateTime start;
    private DateTime end;

    public DisabledDuration(final DateTime start, final DateTime end) {
        this.start = start;
        this.end = end;
    }

    public DateTime getStart() {
        return start;
    }

    public DateTime getEnd() {
        return end;
    }

    public void setEnd(final DateTime end) {
        this.end = end;
    }

    // Order by start date first and then end date
    @Override
    public int compareTo(final DisabledDuration o) {
        int result = start.compareTo(o.getStart());
        if (result == 0) {
            if (end == null && o.getEnd() == null) {
                result = 0;
            } else if (end != null && o.getEnd() != null) {
                result = end.compareTo(o.getEnd());
            } else if (o.getEnd() == null) {
                return -1;
            } else {
                return 1;
            }
        }
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DisabledDuration)) {
            return false;
        }

        final DisabledDuration that = (DisabledDuration) o;

        return compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        int result = start != null ? start.hashCode() : 0;
        result = 31 * result + (end != null ? end.hashCode() : 0);
        return result;
    }

    //
    //
    //  Assumptions (based on ordering):
    //   * this.start <= o.start
    //   * this.end <= o.end when this.start == o.start
    //
    // Case 1: this contained into o => false
    // |---------|       this
    // |--------------|  o
    //
    // Case 2: this overlaps with o => false
    // |---------|            this
    //      |--------------|  o
    //
    // Case 3: o contains into this => false
    // |---------| this
    //      |---|  o
    //
    // Case 4: this and o are adjacent => false
    // |---------| this
    //           |---|  o
    // Case 5: this and o are disjoint => true
    // |---------| this
    //             |---|  o
    public boolean isDisjoint(final DisabledDuration o) {
        return end!= null && end.compareTo(o.getStart()) < 0;
    }

    public static DisabledDuration mergeDuration(DisabledDuration d1, DisabledDuration d2) {
        Preconditions.checkState(d1.getStart().compareTo(d2.getStart()) <=0 );
        Preconditions.checkState(!d1.isDisjoint(d2));

        final DateTime endDate = (d1.getEnd() != null && d2.getEnd() != null) ?
                                 d1.getEnd().compareTo(d2.getEnd()) < 0 ? d2.getEnd() : d1.getEnd() :
                                 null;

        return new DisabledDuration(d1.getStart(), endDate);
    }

}
