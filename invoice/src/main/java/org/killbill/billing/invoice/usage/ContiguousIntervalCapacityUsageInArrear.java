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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.TrackingRecordId;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.details.UsageCapacityInArrearAggregate;
import org.killbill.billing.invoice.usage.details.UsageInArrearAggregate;
import org.killbill.billing.invoice.usage.details.UsageInArrearTierUnitDetail;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.commons.utils.Joiner;
import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;

import static org.killbill.billing.invoice.usage.UsageUtils.getCapacityInArrearTier;

public class ContiguousIntervalCapacityUsageInArrear extends ContiguousIntervalUsageInArrear {

    private static final Joiner joiner = Joiner.on(", ");

    public ContiguousIntervalCapacityUsageInArrear(final Usage usage,
                                                   final UUID accountId,
                                                   final UUID invoiceId,
                                                   final LocalDate targetDate,
                                                   final DateTime rawUsageStartDate,
                                                   final UsageDetailMode usageDetailMode,
                                                   final InvoiceConfig invoiceConfig,
                                                   final InternalTenantContext internalTenantContext) {
        super(usage, accountId, invoiceId, targetDate, rawUsageStartDate, usageDetailMode, invoiceConfig, internalTenantContext);
    }

    @Override
    protected void populateResults(final DateTime startDate,
                                   final DateTime endDate,
                                   final DateTime catalogEffectiveDate,
                                   final BigDecimal billedUsage,
                                   final BigDecimal toBeBilledUsage,
                                   final UsageInArrearAggregate toBeBilledUsageDetails,
                                   final boolean areAllBilledItemsWithDetails,
                                   final boolean isPeriodPreviouslyBilled,
                                   final boolean isDryRun,
                                   final List<InvoiceItem> result) throws InvoiceApiException {
        // Compute final amount by subtracting  amount that was already billed.
        final BigDecimal amountToBill = toBeBilledUsage.subtract(billedUsage);

        if (amountToBill.compareTo(BigDecimal.ZERO) < 0) {
            if (isDryRun || invoiceConfig.isUsageMissingLenient(internalTenantContext)) {
                return;
            } else {
                throw new InvoiceApiException(ErrorCode.UNEXPECTED_ERROR,
                                              String.format("ILLEGAL INVOICING STATE: Usage period start='%s', end='%s', amountToBill='%s', (previously billed amount='%s', new proposed amount='%s')",
                                                            startDate, endDate, amountToBill, billedUsage, toBeBilledUsage));
            }
        } else /* amountToBill.compareTo(BigDecimal.ZERO) >= 0 */ {
            if (!isPeriodPreviouslyBilled || amountToBill.compareTo(BigDecimal.ZERO) > 0) {
                final String itemDetails = areAllBilledItemsWithDetails ? toJson(toBeBilledUsageDetails) : null;
                final InvoiceItem item = new UsageInvoiceItem(invoiceId, accountId, getBundleId(), getSubscriptionId(), getProductName(), getPlanName(),
                                                              getPhaseName(), usage.getName(), catalogEffectiveDate, usageClockUtil.toLocalDate(startDate, internalTenantContext), usageClockUtil.toLocalDate(endDate, internalTenantContext), amountToBill, null, getCurrency(), null, itemDetails);
                result.add(item);
            }
        }
    }

    @Override
    protected UsageInArrearAggregate getToBeBilledUsageDetails(final DateTime startDate, final DateTime endDate, final List<RolledUpUnit> rolledUpUnits, final Iterable<InvoiceItem> billedItems, final boolean areAllBilledItemsWithDetails, final boolean isDryRun) throws CatalogApiException {
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
        Preconditions.checkState(isBuilt.get(), "#computeToBeBilledCapacityInArrear() isBuilt.get() return false");

        final List<Tier> tiers = getCapacityInArrearTier(usage);

        final Set<String> perUnitTypeDetailTierLevel = new HashSet<>();
        int tierNum = 0;
        final List<UsageInArrearTierUnitDetail> toBeBilledDetails = new LinkedList<>();
        for (final Tier cur : tiers) {
            tierNum++;
            final BigDecimal curTierPrice = cur.getRecurringPrice().getPrice(getCurrency());

            boolean complies = true;
            boolean allUnitAmountToZero = true;  // Support for $0 Usage item
            for (final RolledUpUnit ro : roUnits) {

                final Limit tierLimit = getTierLimit(cur, ro.getUnitType());
                // We ignore the min and only look at the max Limit as the tiers should be contiguous.
                // Specifying a -1 value for last max tier will make the validation works
                if (tierLimit.getMax().compareTo(new BigDecimal("-1")) != 0 && ro.getAmount().compareTo(tierLimit.getMax())> 0) {
                    complies = false;
                } else {
                    allUnitAmountToZero = ro.getAmount().compareTo(BigDecimal.ZERO) <= 0 && allUnitAmountToZero;

                    if (!perUnitTypeDetailTierLevel.contains(ro.getUnitType())) {
                        toBeBilledDetails.add(new UsageInArrearTierUnitDetail(tierNum, ro.getUnitType(), curTierPrice, ro.getAmount()));
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
        sb.append(", rawUsageStartDate=").append(rawUsageStartDate);
        sb.append('}');
        return sb.toString();
    }

}
