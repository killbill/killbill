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

import com.ning.billing.catalog.api.BillingPeriod;
import org.joda.time.DateTime;

import java.math.BigDecimal;

public interface BillingMode {
    BigDecimal calculateNumberOfBillingCycles(DateTime startDate, DateTime endDate, DateTime targetDate, int billingCycleDay, BillingPeriod billingPeriod) throws InvalidDateSequenceException;

    BigDecimal calculateNumberOfBillingCycles(DateTime startDate, DateTime targetDate, int billingCycleDay, BillingPeriod billingPeriod) throws InvalidDateSequenceException;

    DateTime calculateEffectiveEndDate(DateTime startDate, DateTime targetDate, int billingCycleDay, BillingPeriod billingPeriod);

    DateTime calculateEffectiveEndDate(DateTime startDate, DateTime endDate, DateTime targetDate, int billingCycleDay, BillingPeriod billingPeriod);
}