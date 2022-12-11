/*
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.invoice.usage;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.commons.utils.Preconditions;

public class UsageUtils {

    public static List<TieredBlock> getConsumableInArrearTieredBlocks(final Usage usage, final String unitType) {

        Preconditions.checkArgument(usage.getBillingMode() == BillingMode.IN_ARREAR && usage.getUsageType() == UsageType.CONSUMABLE, "#getConsumableInArrearTieredBlocks(): usage: %s", usage);
        Preconditions.checkArgument(usage.getTiers().length > 0, "#getConsumableInArrearTieredBlocks(): usage tier's length is 0");


        final List<TieredBlock> result = new LinkedList<>();
        for (final Tier tier : usage.getTiers()) {
            boolean found = false;
            for (final TieredBlock tierBlock : tier.getTieredBlocks()) {
                if (tierBlock.getUnit().getName().equals(unitType)) {
                    result.add(tierBlock);
                    found = true;
                    break;
                }
            }
            // We expect this method to return an ordered list of TieredBlock, each for each tier.
            Preconditions.checkState(found, "Catalog issue in usage section '%s': Missing tierBlock definition for unit '%s'", usage.getName(), unitType);
        }
        return result;
    }

    public static Set<String> getConsumableInArrearUnitTypes(final Usage usage) {
        Preconditions.checkArgument(usage.getBillingMode() == BillingMode.IN_ARREAR && usage.getUsageType() == UsageType.CONSUMABLE, "#getConsumableInArrearUnitTypes(): usage: %s", usage);
        Preconditions.checkArgument(usage.getTiers().length > 0, "#getConsumableInArrearUnitTypes(): usage tier's length is 0");

        final Set<String> result = new HashSet<>();
        for (final Tier tier : usage.getTiers()) {
            for (final TieredBlock tierBlock : tier.getTieredBlocks()) {
                result.add(tierBlock.getUnit().getName());
            }
        }
        return result;
    }

    public static List<Tier> getCapacityInArrearTier(final Usage usage) {
        Preconditions.checkArgument(usage.getBillingMode() == BillingMode.IN_ARREAR && usage.getUsageType() == UsageType.CAPACITY, "#getCapacityInArrearTier(): usage");
        Preconditions.checkArgument(usage.getTiers().length > 0, "#getCapacityInArrearTier(): usage tier's length is 0");

        return  List.of(usage.getTiers());
    }

    public static Set<String> getCapacityInArrearUnitTypes(final Usage usage) {
        Preconditions.checkArgument(usage.getBillingMode() == BillingMode.IN_ARREAR && usage.getUsageType() == UsageType.CAPACITY, "#getCapacityInArrearTier(): usage");
        Preconditions.checkArgument(usage.getTiers().length > 0, "#getCapacityInArrearUnitTypes(): usage tier's length is 0");

        final Set<String> result = new HashSet<>();
        for (final Tier tier : usage.getTiers()) {
            for (final Limit limit : tier.getLimits()) {
                result.add(limit.getUnit().getName());
            }
        }
        return result;
    }

}
