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

package org.killbill.billing.invoice.generator;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;

import com.google.common.annotations.VisibleForTesting;

public class BillingIntervalDetail {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final LocalDate targetDate;
    private final int billingCycleDay;
    private final BillingPeriod billingPeriod;
    private final BillingMode billingMode;
    private LocalDate firstBillingCycleDate;
    private LocalDate effectiveEndDate;
    private LocalDate lastBillingCycleDate;

    public BillingIntervalDetail(final LocalDate startDate,
                                 final LocalDate endDate,
                                 final LocalDate targetDate,
                                 final int billingCycleDay,
                                 final BillingPeriod billingPeriod,
                                 final BillingMode billingMode) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.targetDate = targetDate;
        this.billingCycleDay = billingCycleDay;
        this.billingPeriod = billingPeriod;
        this.billingMode = billingMode;
        computeAll();
    }

    public LocalDate getFirstBillingCycleDate() {
        return firstBillingCycleDate;
    }

    public LocalDate getEffectiveEndDate() {
        return effectiveEndDate;
    }

    public LocalDate getFutureBillingDateFor(int nbPeriod) {
        final int numberOfMonthsPerBillingPeriod = billingPeriod.getNumberOfMonths();
        LocalDate proposedDate = firstBillingCycleDate.plusMonths((nbPeriod) * numberOfMonthsPerBillingPeriod);
        return alignProposedBillCycleDate(proposedDate, billingCycleDay);
    }

    public LocalDate getLastBillingCycleDate() {
        return lastBillingCycleDate;
    }

    public boolean hasSomethingToBill() {
        return effectiveEndDate != null /* IN_ARREAR mode prior we have reached our firstBillingCycleDate */ &&
               (endDate == null || endDate.isAfter(startDate)); /* When there is an endDate, it should be > startDate since we don't bill for less than a day */
    }

    private void computeAll() {
        calculateFirstBillingCycleDate();
        calculateEffectiveEndDate();
        calculateLastBillingCycleDate();
    }

    @VisibleForTesting
    void calculateFirstBillingCycleDate() {

        final int lastDayOfMonth = startDate.dayOfMonth().getMaximumValue();
        final LocalDate billingCycleDate;
        if (billingCycleDay > lastDayOfMonth) {
            billingCycleDate = new LocalDate(startDate.getYear(), startDate.getMonthOfYear(), lastDayOfMonth, startDate.getChronology());
        } else {
            billingCycleDate = new LocalDate(startDate.getYear(), startDate.getMonthOfYear(), billingCycleDay, startDate.getChronology());
        }

        final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
        LocalDate proposedDate = billingCycleDate;
        while (proposedDate.isBefore(startDate)) {
            proposedDate = proposedDate.plusMonths(numberOfMonthsInPeriod);
        }
        firstBillingCycleDate = alignProposedBillCycleDate(proposedDate, billingCycleDay);
    }

    private void calculateEffectiveEndDate() {
        if (billingMode == BillingMode.IN_ADVANCE) {
            calculateInAdvanceEffectiveEndDate();
        } else {
            calculateInArrearEffectiveEndDate();
        }
    }

    private void calculateInArrearEffectiveEndDate() {
        if (targetDate.isBefore(firstBillingCycleDate)) {
            // Nothing to bill for, hasSomethingToBill will return false
            effectiveEndDate = null;
            return;
        }

        if (endDate != null && endDate.isBefore(firstBillingCycleDate)) {
            effectiveEndDate = endDate;
            return;
        }

        final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
        int numberOfPeriods = 0;
        LocalDate proposedDate = firstBillingCycleDate;

        while (proposedDate.isBefore(targetDate)) {
            proposedDate = firstBillingCycleDate.plusMonths(numberOfPeriods * numberOfMonthsInPeriod);
            numberOfPeriods += 1;
        }
        proposedDate = alignProposedBillCycleDate(proposedDate, billingCycleDay);

        // The proposedDate is greater to our endDate => return it
        if (endDate != null && endDate.isBefore(proposedDate)) {
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

        final int numberOfMonthsInPeriod = billingPeriod.getNumberOfMonths();
        int numberOfPeriods = 0;
        LocalDate proposedDate = firstBillingCycleDate;

        while (!proposedDate.isAfter(targetDate)) {
            proposedDate = firstBillingCycleDate.plusMonths(numberOfPeriods * numberOfMonthsInPeriod);
            numberOfPeriods += 1;
        }
        proposedDate = alignProposedBillCycleDate(proposedDate, billingCycleDay);

        // The proposedDate is greater to our endDate => return it
        if (endDate != null && endDate.isBefore(proposedDate)) {
            effectiveEndDate = endDate;
        } else {
            effectiveEndDate = proposedDate;
        }
    }

    private void calculateLastBillingCycleDate() {

        if (effectiveEndDate == null) {
            lastBillingCycleDate = firstBillingCycleDate;
            return;
        }

        // Start from firstBillingCycleDate and billingPeriod until we pass the effectiveEndDate
        LocalDate proposedDate = firstBillingCycleDate;
        int numberOfPeriods = 0;
        while (!proposedDate.isAfter(effectiveEndDate)) {
            proposedDate = firstBillingCycleDate.plusMonths(numberOfPeriods * billingPeriod.getNumberOfMonths());
            numberOfPeriods += 1;
        }

        // Our proposed date is billingCycleDate prior to the effectiveEndDate
        proposedDate = proposedDate.plusMonths(-billingPeriod.getNumberOfMonths());
        proposedDate = alignProposedBillCycleDate(proposedDate, billingCycleDay);

        if (proposedDate.isBefore(firstBillingCycleDate)) {
            // Make sure not to go too far in the past
            lastBillingCycleDate = firstBillingCycleDate;
        } else {
            lastBillingCycleDate = proposedDate;
        }
    }

    //
    // We start from a billCycleDate
    //
    public static LocalDate alignProposedBillCycleDate(final LocalDate proposedDate, final int billingCycleDay) {
        final int lastDayOfMonth = proposedDate.dayOfMonth().getMaximumValue();
        int proposedBillCycleDate = proposedDate.getDayOfMonth();
        if (proposedBillCycleDate < billingCycleDay && billingCycleDay <= lastDayOfMonth) {
            proposedBillCycleDate = billingCycleDay;
        }
        return new LocalDate(proposedDate.getYear(), proposedDate.getMonthOfYear(), proposedBillCycleDate, proposedDate.getChronology());
    }
}
