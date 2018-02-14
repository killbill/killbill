/*
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

package org.killbill.billing.invoice.usage;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.TierBlockPolicy;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.details.UsageConsumableInArrearDetail;
import org.killbill.billing.invoice.usage.details.UsageConsumableInArrearTierUnitDetail;
import org.killbill.billing.invoice.usage.details.UsageInArrearDetail;
import org.killbill.billing.usage.RawUsage;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.util.config.definition.InvoiceConfig.UsageDetailMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import static org.killbill.billing.invoice.usage.UsageUtils.getConsumableInArrearTieredBlocks;

public class ContiguousIntervalConsumableUsageInArrear extends ContiguousIntervalUsageInArrear {

    private static final Logger log = LoggerFactory.getLogger(ContiguousIntervalConsumableUsageInArrear.class);

    public ContiguousIntervalConsumableUsageInArrear(final Usage usage,
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
    protected void populateResults(final LocalDate startDate, final LocalDate endDate, final BigDecimal billedUsage, final BigDecimal toBeBilledUsage, final UsageInArrearDetail toBeBilledUsageDetails, final boolean areAllBilledItemsWithDetails, final List<InvoiceItem> result) {
        // In the case past invoice items showed the details (areAllBilledItemsWithDetails=true), billed usage has already been taken into account
        // as it part of the reconciliation logic, so no need to subtract it here
        final BigDecimal amountToBill = (usage.getTierBlockPolicy() == TierBlockPolicy.ALL_TIERS && areAllBilledItemsWithDetails) ? toBeBilledUsage : toBeBilledUsage.subtract(billedUsage);

        if (amountToBill.compareTo(BigDecimal.ZERO) > 0) {
            if (UsageDetailMode.DETAIL == usageDetailMode) {
                for (UsageConsumableInArrearTierUnitDetail toBeBilledUsageDetail : ((UsageConsumableInArrearDetail) toBeBilledUsageDetails).getTierDetails()) {
                    final InvoiceItem item = new UsageInvoiceItem(invoiceId, accountId, getBundleId(), getSubscriptionId(), getPlanName(),
                                                                  getPhaseName(), usage.getName(), startDate, endDate, toBeBilledUsageDetail.getAmount(), toBeBilledUsageDetail.getTierPrice(), getCurrency(), toBeBilledUsageDetail.getQuantity(), null);
                    result.add(item);
                }
            } else {
                final String itemDetails = toJson(toBeBilledUsageDetails);
                final InvoiceItem item = new UsageInvoiceItem(invoiceId, accountId, getBundleId(), getSubscriptionId(), getPlanName(),
                                                              getPhaseName(), usage.getName(), startDate, endDate, amountToBill, null, getCurrency(), null, itemDetails);
                result.add(item);
            }
        }
    }

    @Override
    protected UsageInArrearDetail getToBeBilledUsageDetails(final List<RolledUpUnit> rolledUpUnits, final Iterable<InvoiceItem> billedItems, final boolean areAllBilledItemsWithDetails) throws CatalogApiException {

        final Map<String, List<UsageConsumableInArrearTierUnitDetail>> previousUnitsUsage;
        if (areAllBilledItemsWithDetails) {
            previousUnitsUsage = new HashMap<String, List<UsageConsumableInArrearTierUnitDetail>>();
            for (RolledUpUnit cur : rolledUpUnits) {
                final List<UsageConsumableInArrearTierUnitDetail> usageInArrearDetailForUnitType = getBilledDetailsForUnitType(billedItems, cur.getUnitType());
                previousUnitsUsage.put(cur.getUnitType(), usageInArrearDetailForUnitType);
            }
        } else {
            previousUnitsUsage = ImmutableMap.of();
        }

        final List<UsageConsumableInArrearTierUnitDetail> usageConsumableInArrearTierUnitDetails = new ArrayList<UsageConsumableInArrearTierUnitDetail>();
        for (final RolledUpUnit cur : rolledUpUnits) {
            if (!unitTypes.contains(cur.getUnitType())) {
                log.warn("ContiguousIntervalConsumableInArrear is skipping unitType " + cur.getUnitType());
                continue;
            }
            final List<UsageConsumableInArrearTierUnitDetail> previousUsage = previousUnitsUsage.containsKey(cur.getUnitType()) ? previousUnitsUsage.get(cur.getUnitType()) : ImmutableList.<UsageConsumableInArrearTierUnitDetail>of();

            final List<UsageConsumableInArrearTierUnitDetail> toBeBilledConsumableInArrear = computeToBeBilledConsumableInArrear(cur, previousUsage);
            usageConsumableInArrearTierUnitDetails.addAll(toBeBilledConsumableInArrear);
        }
        final UsageInArrearDetail toBeBilledUsageDetails = new UsageConsumableInArrearDetail(usageConsumableInArrearTierUnitDetails);
        return toBeBilledUsageDetails;
    }

    @VisibleForTesting
    List<UsageConsumableInArrearTierUnitDetail> getBilledDetailsForUnitType(final Iterable<InvoiceItem> billedItems, final String unitType) {

        // Aggregate on a per-tier level, will return a list with item per level -- for this 'unitType'
        final Map<Integer, UsageConsumableInArrearTierUnitDetail> resultMap = new TreeMap<Integer, UsageConsumableInArrearTierUnitDetail>(Ordering.<Integer>natural());
        for (final InvoiceItem bi : billedItems) {

            final UsageConsumableInArrearDetail usageDetail = fromJson(bi.getItemDetails());
            for (final UsageConsumableInArrearTierUnitDetail curDetail : usageDetail.getTierDetails()) {

                if (curDetail.getTierUnit().equals(unitType)) {

                    if (!resultMap.containsKey(curDetail.getTier())) {
                        resultMap.put(curDetail.getTier(), curDetail);
                    } else {
                        final UsageConsumableInArrearTierUnitDetail perTierDetail = resultMap.get(curDetail.getTier());
                        perTierDetail.updateQuantityAndAmount(perTierDetail.getQuantity() + curDetail.getQuantity());
                    }
                }
            }
        }
        return ImmutableList.copyOf(resultMap.values());
    }

    @VisibleForTesting
    List<UsageConsumableInArrearTierUnitDetail> computeToBeBilledConsumableInArrear(final RolledUpUnit roUnit, final List<UsageConsumableInArrearTierUnitDetail> previousUsage) throws CatalogApiException {

        Preconditions.checkState(isBuilt.get());
        final List<TieredBlock> tieredBlocks = getConsumableInArrearTieredBlocks(usage, roUnit.getUnitType());

        switch (usage.getTierBlockPolicy()) {
            case ALL_TIERS:
                return computeToBeBilledConsumableInArrearWith_ALL_TIERS(tieredBlocks, previousUsage, roUnit.getAmount());
            case TOP_TIER:
                return Arrays.asList(computeToBeBilledConsumableInArrearWith_TOP_TIER(tieredBlocks, previousUsage, roUnit.getAmount()));
            default:
                throw new IllegalStateException("Unknown TierBlockPolicy " + usage.getTierBlockPolicy());
        }
    }

    List<UsageConsumableInArrearTierUnitDetail> computeToBeBilledConsumableInArrearWith_ALL_TIERS(final List<TieredBlock> tieredBlocks, final List<UsageConsumableInArrearTierUnitDetail> previousUsage, final Long units) throws CatalogApiException {

        List<UsageConsumableInArrearTierUnitDetail> toBeBilledDetails = Lists.newLinkedList();
        int remainingUnits = units.intValue();
        int tierNum = 0;

        final int lastPreviousUsageTier = previousUsage.size(); // we count tier from 1, 2, ...
        final boolean hasPreviousUsage = lastPreviousUsageTier > 0;

        for (final TieredBlock tieredBlock : tieredBlocks) {

            tierNum++;
            final int blockTierSize = tieredBlock.getSize().intValue();
            final int tmp = remainingUnits / blockTierSize + (remainingUnits % blockTierSize == 0 ? 0 : 1);
            int nbUsedTierBlocks;
            if (tmp > tieredBlock.getMax()) {
                nbUsedTierBlocks = tieredBlock.getMax().intValue();
                remainingUnits -= tieredBlock.getMax() * blockTierSize;
            } else {
                nbUsedTierBlocks = tmp;
                remainingUnits = 0;
            }

            if (nbUsedTierBlocks > 0) {
                if (hasPreviousUsage) {
                    final Integer previousUsageQuantity = tierNum <= lastPreviousUsageTier ? previousUsage.get(tierNum - 1).getQuantity() : 0;
                    if (tierNum < lastPreviousUsageTier) {
                        Preconditions.checkState(nbUsedTierBlocks == previousUsageQuantity, "Expected usage for tier='%d', unit='%s' to be full, instead found units='[%d/%d]'",
                                                                                                          tierNum, tieredBlock.getUnit().getName(), nbUsedTierBlocks, previousUsageQuantity);
                    } else {
                        Preconditions.checkState(nbUsedTierBlocks - previousUsageQuantity >= 0, "Expected usage for tier='%d', unit='%s' to contain at least as mush as current usage, instead found units='[%d/%d]",
                                                                                                              tierNum, tieredBlock.getUnit().getName(), nbUsedTierBlocks, previousUsageQuantity);
                    }
                    nbUsedTierBlocks = nbUsedTierBlocks - previousUsageQuantity;
                }
                if (nbUsedTierBlocks > 0) {
                    toBeBilledDetails.add(new UsageConsumableInArrearTierUnitDetail(tierNum, tieredBlock.getUnit().getName(), tieredBlock.getPrice().getPrice(getCurrency()), blockTierSize, nbUsedTierBlocks));
                }
            }
        }
        return toBeBilledDetails;
    }

    UsageConsumableInArrearTierUnitDetail computeToBeBilledConsumableInArrearWith_TOP_TIER(final List<TieredBlock> tieredBlocks, final List<UsageConsumableInArrearTierUnitDetail> previousUsage, final Long units) throws CatalogApiException {

        int remainingUnits = units.intValue();

        // By default last last tierBlock
        TieredBlock targetBlock = tieredBlocks.get(tieredBlocks.size() - 1);
        int targetTierNum = tieredBlocks.size();
        int tierNum = 0;
        // Loop through all tier block
        for (final TieredBlock tieredBlock : tieredBlocks) {

            tierNum++;
            final int blockTierSize = tieredBlock.getSize().intValue();
            final int tmp = remainingUnits / blockTierSize + (remainingUnits % blockTierSize == 0 ? 0 : 1);
            if (tmp > tieredBlock.getMax()) {
                remainingUnits -= tieredBlock.getMax() * blockTierSize;
            } else {
                targetBlock = tieredBlock;
                targetTierNum = tierNum;
                break;
            }
        }
        final int lastBlockTierSize = targetBlock.getSize().intValue();
        final int nbBlocks = units.intValue() / lastBlockTierSize + (units.intValue() % lastBlockTierSize == 0 ? 0 : 1);

        return new UsageConsumableInArrearTierUnitDetail(targetTierNum, targetBlock.getUnit().getName(), targetBlock.getPrice().getPrice(getCurrency()), targetBlock.getSize().intValue(), nbBlocks);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ContiguousIntervalConsumableUsageInArrear{");
        sb.append("transitionTimes=").append(transitionTimes);
        sb.append(", billingEvents=").append(billingEvents);
        sb.append(", rawSubscriptionUsage=").append(rawSubscriptionUsage);
        sb.append(", rawUsageStartDate=").append(rawUsageStartDate);
        sb.append('}');
        return sb.toString();
    }

    public static UsageConsumableInArrearDetail fromJson(String itemDetails) {
        UsageConsumableInArrearDetail result = null;
        if (itemDetails != null) {
            try {
                result = objectMapper.readValue(itemDetails, new TypeReference<UsageConsumableInArrearDetail>() {});
            } catch (IOException e) {
                Preconditions.checkState(false, e.getMessage());
            }
        }
        return result;
    }

}
