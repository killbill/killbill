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

package org.killbill.billing.jaxrs.resources;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.TierPriceOverride;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;
import org.killbill.billing.catalog.api.UsagePriceOverride;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.jaxrs.json.BlockPriceJson;
import org.killbill.billing.jaxrs.json.PhasePriceJson;
import org.killbill.billing.jaxrs.json.SubscriptionJson;
import org.killbill.billing.jaxrs.json.TierPriceJson;
import org.killbill.billing.jaxrs.json.UsagePriceJson;

import com.google.common.base.Preconditions;

public class SubscriptionResourceHelpers {

    public static void buildEntitlementSpecifier(final SubscriptionJson subscriptionJson,
                                                 final Currency currency,
                                                 final Collection<EntitlementSpecifier> entitlementSpecifierList) {
        if (subscriptionJson.getPlanName() == null &&
            (subscriptionJson.getProductName() == null ||
             subscriptionJson.getProductCategory() == null ||
             subscriptionJson.getBillingPeriod() == null ||
             subscriptionJson.getPriceList() == null)) {
            return;
        }

        final PlanPhaseSpecifier planPhaseSpecifier = subscriptionJson.getPlanName() != null ?
                                                      new PlanPhaseSpecifier(subscriptionJson.getPlanName(), null) :
                                                      new PlanPhaseSpecifier(subscriptionJson.getProductName(),
                                                                             subscriptionJson.getBillingPeriod(),
                                                                             subscriptionJson.getPriceList(),
                                                                             subscriptionJson.getPhaseType());

        final List<PlanPhasePriceOverride> overrides = buildPlanPhasePriceOverrides(subscriptionJson.getPriceOverrides(), currency, planPhaseSpecifier);

        final EntitlementSpecifier specifier = new EntitlementSpecifier() {
            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return planPhaseSpecifier;
            }

            @Override
            public Integer getBillCycleDay() {
                return null;
            }

            @Override
            public List<PlanPhasePriceOverride> getOverrides() {
                return overrides;
            }
        };
        entitlementSpecifierList.add(specifier);
    }

    public static List<PlanPhasePriceOverride> buildPlanPhasePriceOverrides(final Iterable<PhasePriceJson> priceOverrides,
                                                                            final Currency currency,
                                                                            final PlanPhaseSpecifier planPhaseSpecifier) {
        final List<PlanPhasePriceOverride> overrides = new LinkedList<PlanPhasePriceOverride>();
        if (priceOverrides != null) {
            for (final PhasePriceJson input : priceOverrides) {
                Preconditions.checkNotNull(input);

                final List<UsagePriceOverride> usagePrices = new LinkedList<UsagePriceOverride>();
                if (input.getUsagePrices() != null) {
                    buildUsagePrices(currency, input, usagePrices);
                }

                overrides.add(buildPlanPhasePriceOverride(planPhaseSpecifier, currency, input, usagePrices));
            }
        }
        return overrides;
    }

    private static void buildUsagePrices(final Currency currency,
                                         final PhasePriceJson input,
                                         final Collection<UsagePriceOverride> usagePrices) {
        for (final UsagePriceJson usageOverrideJson : input.getUsagePrices()) {
            final List<TierPriceOverride> tierPriceOverrides = new LinkedList<TierPriceOverride>();
            for (final TierPriceJson tierPriceJson : usageOverrideJson.getTierPrices()) {
                final List<TieredBlockPriceOverride> blockPriceOverrides = new LinkedList<TieredBlockPriceOverride>();
                for (final BlockPriceJson block : tierPriceJson.getBlockPrices()) {
                    blockPriceOverrides.add(new TieredBlockPriceOverride() {

                        @Override
                        public String getUnitName() {
                            return block.getUnitName();
                        }

                        @Override
                        public Double getSize() {
                            return block.getSize();
                        }

                        @Override
                        public BigDecimal getPrice() {
                            return block.getPrice();
                        }

                        @Override
                        public Currency getCurrency() {
                            return currency;
                        }

                        @Override
                        public Double getMax() {
                            return block.getMax();
                        }
                    });
                }

                tierPriceOverrides.add(new TierPriceOverride() {

                    @Override
                    public List<TieredBlockPriceOverride> getTieredBlockPriceOverrides() {
                        return blockPriceOverrides;
                    }
                });
            }
            usagePrices.add(new UsagePriceOverride() {
                @Override
                public String getName() {
                    return usageOverrideJson.getUsageName();
                }

                @Override
                public UsageType getUsageType() {
                    return usageOverrideJson.getUsageType();
                }

                @Override
                public List<TierPriceOverride> getTierPriceOverrides() {
                    return tierPriceOverrides;
                }
            });
        }
    }

    private static PlanPhasePriceOverride buildPlanPhasePriceOverride(final PlanSpecifier spec,
                                                                      final Currency currency,
                                                                      final PhasePriceJson input,
                                                                      final List<UsagePriceOverride> usagePrices) {
        if (input.getPhaseName() != null) {
            return new PlanPhasePriceOverride() {
                @Override
                public String getPhaseName() {
                    return input.getPhaseName();
                }

                @Override
                public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                    return null;
                }

                @Override
                public Currency getCurrency() {
                    return currency;
                }

                @Override
                public BigDecimal getFixedPrice() {
                    return input.getFixedPrice();
                }

                @Override
                public BigDecimal getRecurringPrice() {
                    return input.getRecurringPrice();
                }

                @Override
                public List<UsagePriceOverride> getUsagePriceOverrides() {
                    return usagePrices;
                }
            };
        }

        final PhaseType phaseType = input.getPhaseType() != null ? PhaseType.valueOf(input.getPhaseType()) : null;

        final PlanPhaseSpecifier planPhaseSpecifier = spec.getPlanName() != null ?
                                                      new PlanPhaseSpecifier(spec.getPlanName(), phaseType) :
                                                      new PlanPhaseSpecifier(spec.getProductName(), spec.getBillingPeriod(), spec.getPriceListName(), phaseType);
        final Currency resolvedCurrency = input.getFixedPrice() != null || input.getRecurringPrice() != null ? currency : null;

        return new PlanPhasePriceOverride() {

            @Override
            public String getPhaseName() {
                return null;
            }

            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return planPhaseSpecifier;
            }

            @Override
            public Currency getCurrency() {
                return resolvedCurrency;
            }

            @Override
            public BigDecimal getFixedPrice() {
                return input.getFixedPrice();
            }

            @Override
            public BigDecimal getRecurringPrice() {
                return input.getRecurringPrice();
            }

            @Override
            public List<UsagePriceOverride> getUsagePriceOverrides() {
                return usagePrices;
            }
        };
    }

    public static BaseEntitlementWithAddOnsSpecifier buildBaseEntitlementWithAddOnsSpecifier(final Iterable<EntitlementSpecifier> entitlementSpecifierList,
                                                                                             final LocalDate resolvedEntitlementDate,
                                                                                             final LocalDate resolvedBillingDate,
                                                                                             @Nullable final UUID bundleId,
                                                                                             @Nullable final String bundleExternalKey,
                                                                                             final Boolean isMigrated) {
        return new BaseEntitlementWithAddOnsSpecifier() {
            @Override
            public UUID getBundleId() {
                return bundleId;
            }

            @Override
            public String getExternalKey() {
                return bundleExternalKey;
            }

            @Override
            public Iterable<EntitlementSpecifier> getEntitlementSpecifier() {
                return entitlementSpecifierList;
            }

            @Override
            public LocalDate getEntitlementEffectiveDate() {
                return resolvedEntitlementDate;
            }

            @Override
            public LocalDate getBillingEffectiveDate() {
                return resolvedBillingDate;
            }

            @Override
            public boolean isMigrated() {
                return isMigrated;
            }
        };
    }
}
