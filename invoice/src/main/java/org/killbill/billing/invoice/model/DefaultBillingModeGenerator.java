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
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.invoice.generator.BillingIntervalDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.invoice.generator.InvoiceDateUtils.calculateNumberOfWholeBillingPeriods;
import static org.killbill.billing.invoice.generator.InvoiceDateUtils.calculateProRationAfterLastBillingCycleDate;
import static org.killbill.billing.invoice.generator.InvoiceDateUtils.calculateProRationBeforeFirstBillingPeriod;

public class DefaultBillingModeGenerator implements BillingModeGenerator {

    private static final Logger log = LoggerFactory.getLogger(DefaultBillingModeGenerator.class);

    @Override
    public List<RecurringInvoiceItemData> generateInvoiceItemData(final LocalDate startDate, @Nullable final LocalDate endDate,
                                                                  final LocalDate targetDate,
                                                                  final int billingCycleDayLocal,
                                                                  final BillingPeriod billingPeriod,
                                                                  final BillingMode billingMode) throws InvalidDateSequenceException {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidDateSequenceException();
        }
        if (targetDate.isBefore(startDate)) {
            throw new InvalidDateSequenceException();
        }

        final List<RecurringInvoiceItemData> results = new ArrayList<RecurringInvoiceItemData>();

        final BillingIntervalDetail billingIntervalDetail = new BillingIntervalDetail(startDate, endDate, targetDate, billingCycleDayLocal, billingPeriod, billingMode);

        // We are not billing for less than a day
        if (!billingIntervalDetail.hasSomethingToBill()) {
            return results;
        }
        //
        // If there is an endDate and that endDate is before our first coming firstBillingCycleDate, all we have to do
        // is to charge for that period
        //
        if (endDate != null && !endDate.isAfter(billingIntervalDetail.getFirstBillingCycleDate())) {
            final BigDecimal leadingProRationPeriods = calculateProRationBeforeFirstBillingPeriod(startDate, endDate, billingPeriod);
            final RecurringInvoiceItemData itemData = new RecurringInvoiceItemData(startDate, endDate, leadingProRationPeriods);
            results.add(itemData);
            return results;
        }

        //
        // Leading proration if
        // i) The first firstBillingCycleDate is strictly after our start date AND
        // ii) The endDate is is not null and is strictly after our firstBillingCycleDate (previous check)
        //
        if (billingIntervalDetail.getFirstBillingCycleDate().isAfter(startDate)) {
            final BigDecimal leadingProRationPeriods = calculateProRationBeforeFirstBillingPeriod(startDate, billingIntervalDetail.getFirstBillingCycleDate(), billingPeriod);
            if (leadingProRationPeriods != null && leadingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                // Not common - add info in the logs for debugging purposes
                final RecurringInvoiceItemData itemData = new RecurringInvoiceItemData(startDate, billingIntervalDetail.getFirstBillingCycleDate(), leadingProRationPeriods);
                log.info("Adding pro-ration: {}", itemData);
                results.add(itemData);
            }
        }

        //
        // Calculate the effectiveEndDate from the firstBillingCycleDate:
        // - If endDate != null and targetDate is after endDate => this is the endDate and will lead to a trailing pro-ration
        // - If not, this is the last billingCycleDate calculation right after the targetDate
        //
        final LocalDate effectiveEndDate = billingIntervalDetail.getEffectiveEndDate();

        //
        // Based on what we calculated previously, code recompute one more time the numberOfWholeBillingPeriods
        //
        final LocalDate lastBillingCycleDate = billingIntervalDetail.getLastBillingCycleDate();
        final int numberOfWholeBillingPeriods = calculateNumberOfWholeBillingPeriods(billingIntervalDetail.getFirstBillingCycleDate(), lastBillingCycleDate, billingPeriod);

        for (int i = 0; i < numberOfWholeBillingPeriods; i++) {
            final LocalDate servicePeriodStartDate;
            if (results.size() > 0) {
                // Make sure the periods align, especially with the pro-ration calculations above
                servicePeriodStartDate = results.get(results.size() - 1).getEndDate();
            } else if (i == 0) {
                // Use the specified start date
                servicePeriodStartDate = startDate;
            } else {
                throw new IllegalStateException("We should at least have one invoice item!");
            }

            // Make sure to align the end date with the BCD
            final LocalDate servicePeriodEndDate = billingIntervalDetail.getFutureBillingDateFor(i + 1);
            results.add(new RecurringInvoiceItemData(servicePeriodStartDate, servicePeriodEndDate, BigDecimal.ONE));
        }

        //
        // Now we check if indeed we need a trailing proration and add that incomplete item
        //
        if (effectiveEndDate.isAfter(lastBillingCycleDate)) {
            final BigDecimal trailingProRationPeriods = calculateProRationAfterLastBillingCycleDate(effectiveEndDate, lastBillingCycleDate, billingPeriod);
            if (trailingProRationPeriods.compareTo(BigDecimal.ZERO) > 0) {
                // Not common - add info in the logs for debugging purposes
                final RecurringInvoiceItemData itemData = new RecurringInvoiceItemData(lastBillingCycleDate, effectiveEndDate, trailingProRationPeriods);
                log.info("Adding trailing pro-ration: {}", itemData);
                results.add(itemData);
            }
        }
        return results;
    }
}
