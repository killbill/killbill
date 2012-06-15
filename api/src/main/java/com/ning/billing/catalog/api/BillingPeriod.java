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

package com.ning.billing.catalog.api;

public enum BillingPeriod {
    MONTHLY(1),
    QUARTERLY(3),
    //SEMI_ANNUAL(6), ** not yet supported
    ANNUAL(12),
    //BI_ANNUAL(24); ** not yet supported
    NO_BILLING_PERIOD(0);

    private final int numberOfMonths;

    BillingPeriod(final int numberOfMonths) {
        this.numberOfMonths = numberOfMonths;
    }

    public int getNumberOfMonths() {
        return numberOfMonths;
    }
}
