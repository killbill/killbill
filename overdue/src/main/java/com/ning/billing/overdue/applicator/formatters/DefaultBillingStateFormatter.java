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

package com.ning.billing.overdue.applicator.formatters;

import java.math.BigDecimal;

import com.ning.billing.overdue.config.api.BillingState;

import com.google.common.base.Objects;

import static com.ning.billing.util.DefaultAmountFormatter.round;

public class DefaultBillingStateFormatter extends BillingStateFormatter {

    public DefaultBillingStateFormatter(final BillingState billingState) {
        super(billingState);
    }

    @Override
    public String getFormattedBalanceOfUnpaidInvoices() {
        return round(Objects.firstNonNull(getBalanceOfUnpaidInvoices(), BigDecimal.ZERO)).toString();
    }
}
