/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.invoice.model;

import java.math.BigDecimal;

import org.joda.time.LocalDate;

public class RecurringInvoiceItemData {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal numberOfCycles;

    public RecurringInvoiceItemData(final LocalDate startDate, final LocalDate endDate, final BigDecimal numberOfCycles) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.numberOfCycles = numberOfCycles;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getNumberOfCycles() {
        return numberOfCycles;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RecurringInvoiceItemData");
        sb.append("{startDate=").append(startDate);
        sb.append(", endDate=").append(endDate);
        sb.append(", numberOfCycles=").append(numberOfCycles);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final RecurringInvoiceItemData that = (RecurringInvoiceItemData) o;

        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
            return false;
        }
        if (numberOfCycles != null ? !numberOfCycles.equals(that.numberOfCycles) : that.numberOfCycles != null) {
            return false;
        }
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = startDate != null ? startDate.hashCode() : 0;
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (numberOfCycles != null ? numberOfCycles.hashCode() : 0);
        return result;
    }
}
