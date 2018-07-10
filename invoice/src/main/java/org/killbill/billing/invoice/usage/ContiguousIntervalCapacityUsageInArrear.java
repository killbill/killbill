/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.invoice.usage;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.details.UsageCapacityInArrearAggregate;
import org.killbill.billing.invoice.usage.details.UsageInArrearAggregate;
import org.killbill.billing.invoice.usage.details.UsageInArrearTierUnitDetail;
import org.killbill.billing.usage.RawUsage;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import static org.killbill.billing.invoice.usage.UsageUtils.getCapacityInArrearTier;

public class ContiguousIntervalCapacityUsageInArrear extends ContiguousIntervalUsageInArrear {

    private static final Joiner joiner = Joiner.on(", ");

    public ContiguousIntervalCapacityUsageInArrear(final Usage usage,
                                                   final UUID accountId,
                                                   final UUID invoiceId,
                                                   final List<RawUsage> rawSubscriptionUsage,
                                                   final LocalDate targetDate,
                                                   final LocalDate rawUsageStartDate,
                                                   final UsageDetailMode usageDetailMode,
                                                   final InternalTenantContext internalTenantContext) {
        super(usage, accountId, invoiceId, rawSubscriptionUsage, targetDate, rawUsageStartDate, usageDetailMode, internalTenantContext);
    }

    @Override
    protected void populateResults(final LocalDate startDate, final LocalDate endDate, final BigDecimal billedUsage, final BigDecimal toBeBilledUsage, final UsageInArrearAggregate toBeBilledUsageDetails, final boolean areAllBilledItemsWithDetails, final boolean isPeriodPreviouslyBilled, final List<InvoiceItem> result) throws InvoiceApiException {
        // Compute final amount by subtracting  amount that was already billed.
        final BigDecimal amountToBill = toBeBilledUsage.subtract(billedUsage);

        if (amountToBill.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvoiceApiException(ErrorCode.UNEXPECTED_ERROR,
                                          String.format("ILLEGAL INVOICING STATE: Usage period start='%s', end='%s', previously billed amount='%.2f', new proposed amount='%.2f'",
                                                        startDate, endDate, billedUsage, toBeBilledUsage));
        } else /* amountToBill.compareTo(BigDecimal.ZERO) >= 0 */ {
            if (!isPeriodPreviouslyBilled || amountToBill.compareTo(BigDecimal.ZERO) > 0) {
                final String itemDetails = areAllBilledItemsWithDetails ? toJson(toBeBilledUsageDetails) : null;
                final InvoiceItem item = new UsageInvoiceItem(invoiceId, accountId, getBundleId(), getSubscriptionId(), getProductName(), getPlanName(),
                                                              getPhaseName(), usage.getName(), startDate, endDate, amountToBill, null, getCurrency(), null, itemDetails);
                result.add(item);
            }
        }
    }

    @Override
    protected UsageInArrearAggregate getToBeBilledUsageDetails(final List<RolledUpUnit> rolledUpUnits, final Iterable<InvoiceItem> billedItems, final boolean areAllBilledItemsWithDetails) throws CatalogApiException {
        return computeToBeBilledCapacityInArrear(rolledUpUnits);
    }

    private Limit getTierLimit(final Tier tier, final String unitType) {
        for (final Limit cur : tier.getLimits()) {
            if (cur.getUnit().getName().equals(unitType)) {
                return cur;
            }
        }
        Preconditions.checkState(false, "Could not find unit type " + unitType + " in usage tier ");
        return null;
    }

    @VisibleForTesting
    UsageCapacityInArrearAggregate computeToBeBilledCapacityInArrear(final List<RolledUpUnit> roUnits) throws CatalogApiException {
        Preconditions.checkState(isBuilt.get());

        final List<Tier> tiers = getCapacityInArrearTier(usage);

        final Set<String> perUnitTypeDetailTierLevel = new HashSet<String>();
        int tierNum = 0;
        final List<UsageInArrearTierUnitDetail> toBeBilledDetails = Lists.newLinkedList();
        for (final Tier cur : tiers) {
            tierNum++;
            final BigDecimal curTierPrice = cur.getRecurringPrice().getPrice(getCurrency());

            boolean complies = true;
            boolean allUnitAmountToZero = true;  // Support for $0 Usage item
            for (final RolledUpUnit ro : roUnits) {

                final Limit tierLimit = getTierLimit(cur, ro.getUnitType());
                // We ignore the min and only look at the max Limit as the tiers should be contiguous.
                // Specifying a -1 value for last max tier will make the validation works
                if (tierLimit.getMax() != (double) -1 && ro.getAmount().doubleValue() > tierLimit.getMax()) {
                    complies = false;
                } else {

                    allUnitAmountToZero = ro.getAmount() > 0 ? false : allUnitAmountToZero;

                    if (!perUnitTypeDetailTierLevel.contains(ro.getUnitType())) {
                        toBeBilledDetails.add(new UsageInArrearTierUnitDetail(tierNum, ro.getUnitType(), curTierPrice, ro.getAmount().intValue()));
                        perUnitTypeDetailTierLevel.add(ro.getUnitType());
                    }
                }
            }
            if (complies) {
                return new UsageCapacityInArrearAggregate(toBeBilledDetails, allUnitAmountToZero ? BigDecimal.ZERO : curTierPrice);
            }
        }
        // Probably invalid catalog config
        joiner.join(roUnits);
        Preconditions.checkState(false, "Could not find tier for usage " + usage.getName() + "matching with data = " + joiner.join(roUnits));
        return null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ContiguousIntervalCapacityUsageInArrear{");
        sb.append("transitionTimes=").append(transitionTimes);
        sb.append(", billingEvents=").append(billingEvents);
        sb.append(", rawSubscriptionUsage=").append(rawSubscriptionUsage);
        sb.append(", rawUsageStartDate=").append(rawUsageStartDate);
        sb.append('}');
        return sb.toString();
    }

}
