/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.invoice.generator;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.util.bcd.BillCycleDayCalculator;
import org.killbill.billing.util.config.definition.InvoiceConfig.InArrearMode;

public class BillingIntervalDetail {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final LocalDate targetDate;
    private final int billingCycleDay;
    private final BillingPeriod billingPeriod;
    private final BillingMode billingMode;
    // First date after the startDate aligned with the BCD
    private LocalDate firstBillingCycleDate;
    // Date up to which we should bill
    private LocalDate effectiveEndDate;

    private LocalDate lastBillingCycleDate;

    // Variation of in-arrear where we bill early
    // Any targetDate in the current period allows to bill such period
    // In normal scenario, we would only bill as we reach the end of the period.
    private boolean inArrearGreedy;

    public BillingIntervalDetail(final LocalDate startDate,
                                 final LocalDate endDate,
                                 final LocalDate targetDate,
                                 final int billingCycleDay,
                                 final BillingPeriod billingPeriod,
                                 final BillingMode billingMode,
                                 final InArrearMode inArrearMode) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.targetDate = targetDate;
        if (billingPeriod.getPeriod().getMonths() != 0 || billingPeriod.getPeriod().getYears() != 0) {
            this.billingCycleDay = billingCycleDay;
        } else {
            this.billingCycleDay = startDate.getDayOfMonth();
        }
        this.billingPeriod = billingPeriod;
        this.billingMode = billingMode;
        this.inArrearGreedy = inArrearMode == InArrearMode.GREEDY;
        computeAll();
    }

    public LocalDate getFirstBillingCycleDate() {
        return firstBillingCycleDate;
    }

    public LocalDate getEffectiveEndDate() {
        return effectiveEndDate;
    }

    public LocalDate getFutureBillingDateFor(final int nbPeriod) {
        final LocalDate proposedDate = InvoiceDateUtils.advanceByNPeriods(firstBillingCycleDate, billingPeriod, nbPeriod);
        return BillCycleDayCalculator.alignProposedBillCycleDate(proposedDate, billingCycleDay, billingPeriod);
    }

    public LocalDate getLastBillingCycleDate() {
        return lastBillingCycleDate;
    }

    public LocalDate getNextBillingCycleDate() {
        final LocalDate proposedDate = lastBillingCycleDate != null ? lastBillingCycleDate.plus(billingPeriod.getPeriod()) : firstBillingCycleDate;
        final LocalDate nextBillingCycleDate = BillCycleDayCalculator.alignProposedBillCycleDate(proposedDate, billingCycleDay, billingPeriod);
        return nextBillingCycleDate;
    }


    public boolean hasSomethingToBill() {
        return effectiveEndDate != null /* IN_ARREAR mode prior we have reached our firstBillingCycleDate */ &&
               (endDate == null || endDate.isAfter(startDate)); /* When there is an endDate, it should be > startDate since we don't bill for less than a day */
    }

    public boolean isInArrearGreedy() {
        return inArrearGreedy;
    }


    private void computeAll() {
        calculateFirstBillingCycleDate();
        calculateEffectiveEndDate();
        calculateLastBillingCycleDate();
    }

    private void calculateFirstBillingCycleDate() {

        final int lastDayOfMonth = startDate.dayOfMonth().getMaximumValue();
        final LocalDate billingCycleDate;
        if (billingCycleDay > lastDayOfMonth) {
            billingCycleDate = new LocalDate(startDate.getYear(), startDate.getMonthOfYear(), lastDayOfMonth, startDate.getChronology());
        } else {
            billingCycleDate = new LocalDate(startDate.getYear(), startDate.getMonthOfYear(), billingCycleDay, startDate.getChronology());
        }

        LocalDate proposedDate = billingCycleDate;
        while (proposedDate.isBefore(startDate)) {
            proposedDate = proposedDate.plus(billingPeriod.getPeriod());
        }
        firstBillingCycleDate = BillCycleDayCalculator.alignProposedBillCycleDate(proposedDate, billingCycleDay, billingPeriod);
    }

    private void calculateEffectiveEndDate() {
        if (billingMode == BillingMode.IN_ADVANCE) {
            calculateInAdvanceEffectiveEndDate();
        } else {
            calculateInArrearEffectiveEndDate();
        }
    }


    private void calculateInArrearEffectiveEndDate() {

        //
        // If we have an event mid-billing period (CHANGE, CANCELLATION) that aligns
        // with the target date, we bill immediately for the period instead of waiting for
        // the next billing cycle date, a.k.a firstBillingCycleDate. See #1907
        //
        // The following condition may be even more generic, but targetDate will typically align with the event so perhaps unnecessary:
        // if (endDate != null && targetDate.compareTo(endDate) >= 0 && targetDate.isBefore(cutoffStartDt)) { ...}
        if (endDate != null && targetDate.compareTo(endDate) == 0) {
            effectiveEndDate = targetDate;
            return;
        }

        final LocalDate cutoffStartDt = inArrearGreedy ? startDate : firstBillingCycleDate;
        if (targetDate.isBefore(cutoffStartDt)) {
            // Nothing to bill for, hasSomethingToBill will return false
            effectiveEndDate = null;
            return;

        }

        if (endDate != null && endDate.isBefore(firstBillingCycleDate)) {
            effectiveEndDate = endDate;
            return;
        }

        int numberOfPeriods = 0;
        LocalDate proposedDate = firstBillingCycleDate;
        LocalDate nextProposedDate = getFutureBillingDateFor(numberOfPeriods);
        while (!nextProposedDate.isAfter(targetDate)) {
            proposedDate = nextProposedDate;
            numberOfPeriods += 1;
            nextProposedDate = getFutureBillingDateFor(numberOfPeriods);
        }

        if (inArrearGreedy && !proposedDate.isEqual(targetDate)) {
            proposedDate = nextProposedDate;
        }

        final LocalDate cutoffEndDt = inArrearGreedy ? nextProposedDate : targetDate;
        // We honor the endDate as long as it does not go beyond our targetDate (by construction this cannot be after the nextProposedDate neither.
        if (endDate != null && !endDate.isAfter(cutoffEndDt)) {
            effectiveEndDate = endDate;
        } else {
            effectiveEndDate = proposedDate;
        }
    }

    private void calculateInAdvanceEffectiveEndDate() {

        // We have an endDate and the targetDate is greater or equal to our endDate => return it
        if (endDate != null && !targetDate.isBefore(endDate)) {
            effectiveEndDate = endDate;
            return;
        }

        if (targetDate.isBefore(firstBillingCycleDate)) {
            effectiveEndDate = firstBillingCycleDate;
            return;
        }

        int numberOfPeriods = 0;
        LocalDate proposedDate = firstBillingCycleDate;

        while (!proposedDate.isAfter(targetDate)) {
            proposedDate = getFutureBillingDateFor(numberOfPeriods);
            numberOfPeriods += 1;
        }
        proposedDate = BillCycleDayCalculator.alignProposedBillCycleDate(proposedDate, billingCycleDay, billingPeriod);

        // The proposedDate is greater to our endDate => return it
        if (endDate != null && endDate.isBefore(proposedDate)) {
            effectiveEndDate = endDate;
        } else {
            effectiveEndDate = proposedDate;
        }
    }

    private void calculateLastBillingCycleDate() {

        // IN_ARREAR cases
        if (effectiveEndDate == null || effectiveEndDate.compareTo(firstBillingCycleDate) < 0 ) {
            lastBillingCycleDate = null;
            return;
        }

        // Start from firstBillingCycleDate and billingPeriod until we pass the effectiveEndDate
        LocalDate proposedDate = firstBillingCycleDate;
        int numberOfPeriods = 0;
        while (!proposedDate.isAfter(effectiveEndDate)) {
            proposedDate = getFutureBillingDateFor(numberOfPeriods);
            numberOfPeriods += 1;
        }

        // Our proposed date is billingCycleDate prior to the effectiveEndDate
        proposedDate = proposedDate.minus(billingPeriod.getPeriod());
        proposedDate = BillCycleDayCalculator.alignProposedBillCycleDate(proposedDate, billingCycleDay, billingPeriod);


        if (proposedDate.isBefore(firstBillingCycleDate)) {
            // Make sure not to go too far in the past
            lastBillingCycleDate = firstBillingCycleDate;
        } else {
            lastBillingCycleDate = proposedDate;
        }
    }
}
