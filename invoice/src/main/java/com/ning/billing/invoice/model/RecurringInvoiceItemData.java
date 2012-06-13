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

import java.math.BigDecimal;

import org.joda.time.DateTime;

public class RecurringInvoiceItemData {
    private final DateTime startDate;
    private final DateTime endDate;
    private final BigDecimal numberOfCycles;

    public RecurringInvoiceItemData(final DateTime startDate, final DateTime endDate, final BigDecimal numberOfCycles) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.numberOfCycles = numberOfCycles;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    public BigDecimal getNumberOfCycles() {
        return numberOfCycles;
    }
}
