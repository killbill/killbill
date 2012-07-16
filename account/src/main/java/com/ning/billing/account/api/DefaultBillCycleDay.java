/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.account.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultBillCycleDay implements BillCycleDay {

    private final int dayOfMonthLocal;
    private final int dayOfMonthUTC;

    public DefaultBillCycleDay(final int dayOfMonthUTC) {
        this(dayOfMonthUTC, dayOfMonthUTC);
    }

    public DefaultBillCycleDay(final BillCycleDay billCycleDay) {
        this(billCycleDay.getDayOfMonthLocal(), billCycleDay.getDayOfMonthUTC());
    }

    @JsonCreator
    public DefaultBillCycleDay(@JsonProperty("dayOfMonthLocal") final int dayOfMonthLocal,
                               @JsonProperty("dayOfMonthUTC") final int dayOfMonthUTC) {
        this.dayOfMonthLocal = dayOfMonthLocal;
        this.dayOfMonthUTC = dayOfMonthUTC;
    }

    @Override
    public int getDayOfMonthUTC() {
        return dayOfMonthUTC;
    }

    @Override
    public int getDayOfMonthLocal() {
        return dayOfMonthLocal;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultBillCycleDay");
        sb.append("{dayOfMonthLocal=").append(dayOfMonthLocal);
        sb.append(", dayOfMonthUTC=").append(dayOfMonthUTC);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final DefaultBillCycleDay that = (DefaultBillCycleDay) o;

        if (dayOfMonthLocal != that.dayOfMonthLocal) return false;
        if (dayOfMonthUTC != that.dayOfMonthUTC) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = dayOfMonthLocal;
        result = 31 * result + dayOfMonthUTC;
        return result;
    }
}
