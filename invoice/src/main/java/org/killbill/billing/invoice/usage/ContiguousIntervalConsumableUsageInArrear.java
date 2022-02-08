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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.TierBlockPolicy;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata.TrackingRecordId;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.invoice.usage.details.UsageConsumableInArrearAggregate;
import org.killbill.billing.invoice.usage.details.UsageConsumableInArrearTierUnitAggregate;
import org.killbill.billing.invoice.usage.details.UsageInArrearAggregate;
import org.killbill.billing.usage.api.RawUsageRecord;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.util.config.definition.InvoiceConfig;
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
                                                     final List<RawUsageRecord> rawSubscriptionUsage,
                                                     final Set<TrackingRecordId> existingTrackingId,
                                                     final LocalDate targetDate,
                                                     final LocalDate rawUsageStartDate,
                                                     final UsageDetailMode usageDetailMode,
                                                     final InvoiceConfig invoiceConfig,
                                                     final boolean isDryRun,
                                                     final InternalTenantContext internalTenantContext) {
        super(usage, accountId, invoiceId, rawSubscriptionUsage, existingTrackingId, targetDate, rawUsageStartDate, usageDetailMode, invoiceConfig, isDryRun, internalTenantContext);
    }

    @Override
    protected void populateResults(final LocalDate startDate, final LocalDate endDate, final DateTime catalogEffectiveDate, final BigDecimal billedUsage, final BigDecimal toBeBilledUsage, final UsageInArrearAggregate toBeBilledUsageDetails, final boolean areAllBilledItemsWithDetails, final boolean isPeriodPreviouslyBilled, final List<InvoiceItem> result) throws InvoiceApiException {
        // In the case past invoice items showed the details (areAllBilledItemsWithDetails=true), billed usage has already been taken into account
        // as it part of the reconciliation logic, so no need to subtract it here
        final BigDecimal amountToBill = (usage.getTierBlockPolicy() == TierBlockPolicy.ALL_TIERS && areAllBilledItemsWithDetails) ? toBeBilledUsage : toBeBilledUsage.subtract(billedUsage);

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
                if (UsageDetailMode.DETAIL == usageDetailMode) {

                    for (UsageConsumableInArrearTierUnitAggregate toBeBilledUsageDetail : ((UsageConsumableInArrearAggregate) toBeBilledUsageDetails).getTierDetails()) {
                        final String itemDetails = toJson(toBeBilledUsageDetail);
                        // See https://github.com/killbill/killbill/issues/1325
                        // Our current sql schema limits to an int value ...
                        final Integer quantity = toBeBilledUsageDetail.getQuantity() <= Integer.MAX_VALUE ? toBeBilledUsageDetail.getQuantity().intValue() : -1;
                        final InvoiceItem item = new UsageInvoiceItem(invoiceId, accountId, getBundleId(), getSubscriptionId(), getProductName(), getPlanName(),
                                                                      getPhaseName(), usage.getName(), catalogEffectiveDate, startDate, endDate, toBeBilledUsageDetail.getAmount(), toBeBilledUsageDetail.getTierPrice(), getCurrency(), quantity, itemDetails);
                        result.add(item);
                    }
                } else {
                    final String itemDetails = toJson(toBeBilledUsageDetails);
                    final InvoiceItem item = new UsageInvoiceItem(invoiceId, accountId, getBundleId(), getSubscriptionId(), getProductName(), getPlanName(),
                                                                  getPhaseName(), usage.getName(), catalogEffectiveDate, startDate, endDate, amountToBill, null, getCurrency(), null, itemDetails);
                    result.add(item);
                }
            }
        }
    }

    @Override
    protected UsageInArrearAggregate getToBeBilledUsageDetails(final LocalDate startDate, final LocalDate endDate, final List<RolledUpUnit> rolledUpUnits, final Iterable<InvoiceItem> billedItems, final boolean areAllBilledItemsWithDetails) throws CatalogApiException {

        final Map<String, List<UsageConsumableInArrearTierUnitAggregate>> previousUnitsUsage;
        if (usageDetailMode == UsageDetailMode.DETAIL || areAllBilledItemsWithDetails) {
            previousUnitsUsage = new HashMap<String, List<UsageConsumableInArrearTierUnitAggregate>>();
            for (RolledUpUnit cur : rolledUpUnits) {
                final List<UsageConsumableInArrearTierUnitAggregate> usageInArrearDetailForUnitType = getBilledDetailsForUnitType(billedItems, cur.getUnitType());
                previousUnitsUsage.put(cur.getUnitType(), usageInArrearDetailForUnitType);
            }
        } else {
            previousUnitsUsage = ImmutableMap.of();
        }

        final List<UsageConsumableInArrearTierUnitAggregate> usageConsumableInArrearTierUnitAggregates = new ArrayList<UsageConsumableInArrearTierUnitAggregate>();
        for (final RolledUpUnit cur : rolledUpUnits) {
            if (!unitTypes.contains(cur.getUnitType())) {
                log.warn("ContiguousIntervalConsumableInArrear is skipping unitType " + cur.getUnitType());
                continue;
            }
            final List<UsageConsumableInArrearTierUnitAggregate> previousUsage = previousUnitsUsage.containsKey(cur.getUnitType()) ? previousUnitsUsage.get(cur.getUnitType()) : ImmutableList.<UsageConsumableInArrearTierUnitAggregate>of();

            final List<UsageConsumableInArrearTierUnitAggregate> toBeBilledConsumableInArrear = computeToBeBilledConsumableInArrear(startDate, endDate, cur, previousUsage);
            usageConsumableInArrearTierUnitAggregates.addAll(toBeBilledConsumableInArrear);
        }
        final UsageInArrearAggregate toBeBilledUsageDetails = new UsageConsumableInArrearAggregate(usageConsumableInArrearTierUnitAggregates);
        return toBeBilledUsageDetails;
    }

    @VisibleForTesting
    List<UsageConsumableInArrearTierUnitAggregate> getBilledDetailsForUnitType(final Iterable<InvoiceItem> billedItems, final String unitType) {

        // Aggregate on a per-tier level, will return a list with item per level -- for this 'unitType'
        final Map<Integer, UsageConsumableInArrearTierUnitAggregate> resultMap = new TreeMap<Integer, UsageConsumableInArrearTierUnitAggregate>(Ordering.<Integer>natural());

        List<UsageConsumableInArrearTierUnitAggregate> tierDetails = new ArrayList<UsageConsumableInArrearTierUnitAggregate>();
        for (final InvoiceItem bi : billedItems) {

            if (usageDetailMode == UsageDetailMode.DETAIL) {

                final UsageConsumableInArrearTierUnitAggregate targetTierUnitDetail = fromJson(bi.getItemDetails(), new TypeReference<UsageConsumableInArrearTierUnitAggregate>() {});
                if (targetTierUnitDetail.getTierUnit().equals(unitType)) {
                    // See https://github.com/killbill/killbill/issues/1325
                    final Long quantity = bi.getQuantity().longValue();
                    tierDetails.add(new UsageConsumableInArrearTierUnitAggregate(targetTierUnitDetail.getTier(), targetTierUnitDetail.getTierUnit(), bi.getRate(), targetTierUnitDetail.getTierBlockSize(), quantity, bi.getAmount()));
                }
            } else {
                final UsageConsumableInArrearAggregate usageDetail = fromJson(bi.getItemDetails(), new TypeReference<UsageConsumableInArrearAggregate>() {});
                for (final UsageConsumableInArrearTierUnitAggregate unitAgg : usageDetail.getTierDetails()) {
                    if (unitAgg.getTierUnit().equals(unitType)) {
                        tierDetails.add(unitAgg);
                    }
                }
            }
        }

        for (final UsageConsumableInArrearTierUnitAggregate curDetail : tierDetails) {
            if (!resultMap.containsKey(curDetail.getTier())) {
                resultMap.put(curDetail.getTier(), curDetail);
            } else {
                final UsageConsumableInArrearTierUnitAggregate perTierDetail = resultMap.get(curDetail.getTier());
                perTierDetail.updateQuantityAndAmount(curDetail.getQuantity());
            }
        }
        return ImmutableList.copyOf(resultMap.values());
    }

    @VisibleForTesting
    List<UsageConsumableInArrearTierUnitAggregate> computeToBeBilledConsumableInArrear(final LocalDate startDate, final LocalDate endDate, final RolledUpUnit roUnit, final List<UsageConsumableInArrearTierUnitAggregate> previousUsage) throws CatalogApiException {

        Preconditions.checkState(isBuilt.get());
        final List<TieredBlock> tieredBlocks = getConsumableInArrearTieredBlocks(usage, roUnit.getUnitType());

        switch (usage.getTierBlockPolicy()) {
            case ALL_TIERS:
                return computeToBeBilledConsumableInArrearWith_ALL_TIERS(startDate, endDate, tieredBlocks, previousUsage, roUnit.getAmount());
            case TOP_TIER:
                return Arrays.asList(computeToBeBilledConsumableInArrearWith_TOP_TIER(tieredBlocks, previousUsage, roUnit.getAmount()));
            default:
                throw new IllegalStateException("Unknown TierBlockPolicy " + usage.getTierBlockPolicy());
        }
    }

    List<UsageConsumableInArrearTierUnitAggregate> computeToBeBilledConsumableInArrearWith_ALL_TIERS(final LocalDate startDate, final LocalDate endDate, final List<TieredBlock> tieredBlocks, final List<UsageConsumableInArrearTierUnitAggregate> previousUsage, final Long units) throws CatalogApiException {

        List<UsageConsumableInArrearTierUnitAggregate> toBeBilledDetails = Lists.newLinkedList();
        long remainingUnits = units;
        int tierNum = 0;

        final int lastPreviousUsageTier = previousUsage.size(); // we count tier from 1, 2, ...
        final boolean hasPreviousUsage = lastPreviousUsageTier > 0;

        for (final TieredBlock tieredBlock : tieredBlocks) {

            tierNum++;
            final long blockTierSize = tieredBlock.getSize().longValue();
            final long tmp = remainingUnits / blockTierSize + (remainingUnits % blockTierSize == 0 ? 0 : 1);
            long nbUsedTierBlocks;
            if (tieredBlock.getMax() != (double) -1 && tmp > tieredBlock.getMax() ) {
                nbUsedTierBlocks = tieredBlock.getMax().longValue();
                remainingUnits -= tieredBlock.getMax() * blockTierSize;
            } else {
                nbUsedTierBlocks = tmp;
                remainingUnits = 0;
            }
            // We generate an entry if we consumed anything on this tier or if this is the first tier to also support $0 Usage item
            if (hasPreviousUsage) {
                final Long previousUsageQuantity = tierNum <= lastPreviousUsageTier ? previousUsage.get(tierNum - 1).getQuantity() : 0;
                // Be lenient for dryRun use cases (as we could have plugin optimizations not returning full usage data) or unless configured.
                if (!isDryRun &&
                    !invoiceConfig.isUsageMissingLenient(internalTenantContext)) {
                    if (tierNum < lastPreviousUsageTier) {
                        Preconditions.checkState(nbUsedTierBlocks == previousUsageQuantity, String.format("Expected usage for subscription='%s', targetDate='%s', startDt='%s', endDt='%s', tier='%d', unit='%s' to be full, instead found units='[%d/%d]'",
                                                                                                          getSubscriptionId(), targetDate, startDate, endDate, tierNum, tieredBlock.getUnit().getName(), nbUsedTierBlocks, previousUsageQuantity));
                    } else {
                        Preconditions.checkState(nbUsedTierBlocks - previousUsageQuantity >= 0, String.format("Expected usage for subscription='%s', targetDate='%s', startDt='%s', endDt='%s', tier='%d', unit='%s' to contain at least as much as current usage, instead found units='[%d/%d]'",
                                                                                                              getSubscriptionId(), targetDate, startDate, endDate, tierNum, tieredBlock.getUnit().getName(), nbUsedTierBlocks, previousUsageQuantity));
                    }
                }
                nbUsedTierBlocks = nbUsedTierBlocks - previousUsageQuantity;
            }
            if (tierNum == 1 || nbUsedTierBlocks > 0) {
                toBeBilledDetails.add(new UsageConsumableInArrearTierUnitAggregate(tierNum, tieredBlock.getUnit().getName(), tieredBlock.getPrice().getPrice(getCurrency()), blockTierSize, nbUsedTierBlocks));
            }
        }
        return toBeBilledDetails;
    }

    UsageConsumableInArrearTierUnitAggregate computeToBeBilledConsumableInArrearWith_TOP_TIER(final List<TieredBlock> tieredBlocks, final List<UsageConsumableInArrearTierUnitAggregate> previousUsage, final Long units) throws CatalogApiException {

        Long remainingUnits = units;
        // By default last last tierBlock
        TieredBlock targetBlock = tieredBlocks.get(tieredBlocks.size() - 1);
        int targetTierNum = tieredBlocks.size();
        int tierNum = 0;
        // Loop through all tier block
        for (final TieredBlock tieredBlock : tieredBlocks) {

            tierNum++;
            final long blockTierSize = tieredBlock.getSize().longValue();
            final long tmp = remainingUnits / blockTierSize + (remainingUnits % blockTierSize == 0 ? 0 : 1);
            if ( tmp > tieredBlock.getMax()) { /* Includes the case where max is unlimited (-1) */
                remainingUnits -= tieredBlock.getMax().longValue() * blockTierSize;
            } else {
                targetBlock = tieredBlock;
                targetTierNum = tierNum;
                break;
            }
        }
        final long lastBlockTierSize = targetBlock.getSize().longValue();
        final long nbBlocks = units / lastBlockTierSize + (units % lastBlockTierSize == 0 ? 0 : 1);
        return new UsageConsumableInArrearTierUnitAggregate(targetTierNum, targetBlock.getUnit().getName(), targetBlock.getPrice().getPrice(getCurrency()), targetBlock.getSize().longValue(), nbBlocks);
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

    public static <T> T fromJson(String itemDetails, TypeReference<T> ref) {
        T result = null;
        if (itemDetails != null) {
            try {
                result = objectMapper.readValue(itemDetails, ref);
            } catch (IOException e) {
                Preconditions.checkState(false, e.getMessage());
            }
        }
        return result;
    }

}
