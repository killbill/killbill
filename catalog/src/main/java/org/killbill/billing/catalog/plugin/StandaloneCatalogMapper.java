/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.catalog.plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.killbill.billing.catalog.DefaultBlock;
import org.killbill.billing.catalog.DefaultDuration;
import org.killbill.billing.catalog.DefaultFixed;
import org.killbill.billing.catalog.DefaultInternationalPrice;
import org.killbill.billing.catalog.DefaultLimit;
import org.killbill.billing.catalog.DefaultPlan;
import org.killbill.billing.catalog.DefaultPlanPhase;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.DefaultPriceListSet;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.DefaultRecurring;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultTieredBlock;
import org.killbill.billing.catalog.DefaultUnit;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.Block;
import org.killbill.billing.catalog.api.CurrencyValueNull;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.Fixed;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.Recurring;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.catalog.api.rules.Case;
import org.killbill.billing.catalog.api.rules.CaseBillingAlignment;
import org.killbill.billing.catalog.api.rules.CaseCancelPolicy;
import org.killbill.billing.catalog.api.rules.CaseChange;
import org.killbill.billing.catalog.api.rules.CaseChangePlanAlignment;
import org.killbill.billing.catalog.api.rules.CaseChangePlanPolicy;
import org.killbill.billing.catalog.api.rules.CaseCreateAlignment;
import org.killbill.billing.catalog.api.rules.CasePhase;
import org.killbill.billing.catalog.api.rules.CasePriceList;
import org.killbill.billing.catalog.api.rules.PlanRules;
import org.killbill.billing.catalog.plugin.api.StandalonePluginCatalog;
import org.killbill.billing.catalog.rules.DefaultCaseBillingAlignment;
import org.killbill.billing.catalog.rules.DefaultCaseCancelPolicy;
import org.killbill.billing.catalog.rules.DefaultCaseChange;
import org.killbill.billing.catalog.rules.DefaultCaseChangePlanAlignment;
import org.killbill.billing.catalog.rules.DefaultCaseChangePlanPolicy;
import org.killbill.billing.catalog.rules.DefaultCaseCreateAlignment;
import org.killbill.billing.catalog.rules.DefaultCasePhase;
import org.killbill.billing.catalog.rules.DefaultCasePriceList;
import org.killbill.billing.catalog.rules.DefaultCaseStandardNaming;
import org.killbill.billing.catalog.rules.DefaultPlanRules;
import org.killbill.commons.utils.collect.Iterables;

public class StandaloneCatalogMapper {

    private final String catalogName;
    private Map<String, Product> tmpDefaultProducts;
    private Map<String, Plan> tmpDefaultPlans;
    private DefaultPriceListSet tmpDefaultPriceListSet;
    private Map<String, DefaultPriceList> tmpDefaultPriceListMap;

    public StandaloneCatalogMapper(final String catalogName) {
        this.catalogName = catalogName;
        this.tmpDefaultProducts = null;
        this.tmpDefaultPlans = null;
        this.tmpDefaultPriceListMap = new HashMap<String, DefaultPriceList>();
    }

    public StandaloneCatalog toStandaloneCatalog(final StandalonePluginCatalog pluginCatalog) {

        final StandaloneCatalog result = new StandaloneCatalog();
        result.setCatalogName(catalogName);
        result.setEffectiveDate(pluginCatalog.getEffectiveDate().toDate());
        result.setProducts(toDefaultProducts(pluginCatalog.getProducts()));
        result.setPlans(toDefaultPlans(result, pluginCatalog.getPlans()));
        result.setPriceLists(toDefaultPriceListSet(pluginCatalog.getDefaultPriceList(), pluginCatalog.getChildrenPriceList()));
        result.setSupportedCurrencies(toArray(pluginCatalog.getCurrencies()));
        result.setUnits(toDefaultUnits(pluginCatalog.getUnits()));
        result.setPlanRules(toDefaultPlanRules(pluginCatalog.getPlanRules()));
        for (final Product cur : pluginCatalog.getProducts()) {
            final Product target = result.getCatalogEntityCollectionProduct().findByName(cur.getName());
            if (target != null) {
                ((DefaultProduct) target).setAvailable(toFilteredDefaultProduct(cur.getAvailable()));
                ((DefaultProduct) target).setIncluded(toFilteredDefaultProduct(cur.getIncluded()));
            }
        }
        result.initialize(result);
        return result;
    }

    private DefaultPlanRules toDefaultPlanRules(final PlanRules input) {
        final DefaultPlanRules result = new DefaultPlanRules();
        result.setBillingAlignmentCase(toDefaultCaseBillingAlignments(input.getCaseBillingAlignment()));
        result.setCancelCase(toDefaultCaseCancelPolicys(input.getCaseCancelPolicy()));
        result.setChangeAlignmentCase(toDefaultCaseChangePlanAlignments(input.getCaseChangePlanAlignment()));
        result.setChangeCase(toDefaultCaseChangePlanPolicies(input.getCaseChangePlanPolicy()));
        result.setCreateAlignmentCase(toDefaultCaseCreateAlignments(input.getCaseCreateAlignment()));
        result.setPriceListCase(toDefaultCasePriceLists(input.getCasePriceList()));
        return result;
    }

    final DefaultCaseChangePlanPolicy[] toDefaultCaseChangePlanPolicies(final Iterable<CaseChangePlanPolicy> input) {
        return toArrayWithTransform(input, this::toDefaultCaseChangePlanPolicy);
    }

    final DefaultCaseChangePlanAlignment[] toDefaultCaseChangePlanAlignments(final Iterable<CaseChangePlanAlignment> input) {
        return toArrayWithTransform(input, this::toDefaultCaseChangePlanAlignment);
    }

    final DefaultCaseBillingAlignment[] toDefaultCaseBillingAlignments(final Iterable<CaseBillingAlignment> input) {
        return toArrayWithTransform(input, this::toDefaultCaseBillingAlignment);
    }

    final DefaultCaseCancelPolicy[] toDefaultCaseCancelPolicys(final Iterable<CaseCancelPolicy> input) {
        return toArrayWithTransform(input, this::toDefaultCaseCancelPolicy);
    }

    final DefaultCaseCreateAlignment[] toDefaultCaseCreateAlignments(final Iterable<CaseCreateAlignment> input) {
        return toArrayWithTransform(input, this::toCaseCreateAlignment);
    }

    final DefaultCasePriceList[] toDefaultCasePriceLists(final Iterable<CasePriceList> input) {
        return toArrayWithTransform(input, this::toDefaultCasePriceList);
    }

    final DefaultCasePriceList toDefaultCasePriceList(final CasePriceList input) {
        final DefaultCasePriceList result = new DefaultCasePriceList();
        result.setToPriceList(toDefaultPriceList(input.getDestinationPriceList()));
        populateDefaultCase(input, result);
        return result;
    }

    final DefaultCaseCreateAlignment toCaseCreateAlignment(final CaseCreateAlignment input) {
        final DefaultCaseCreateAlignment result = new DefaultCaseCreateAlignment();
        result.setAlignment(input.getPlanAlignmentCreate());
        populateDefaultCase(input, result);
        return result;
    }

    final DefaultCaseBillingAlignment toDefaultCaseBillingAlignment(final CaseBillingAlignment input) {
        final DefaultCaseBillingAlignment result = new DefaultCaseBillingAlignment();
        result.setAlignment(input.getBillingAlignment());
        populateDefaultCasePhase(input, result);
        return result;
    }

    final DefaultCaseCancelPolicy toDefaultCaseCancelPolicy(final CaseCancelPolicy input) {
        final DefaultCaseCancelPolicy result = new DefaultCaseCancelPolicy();
        result.setPolicy(input.getBillingActionPolicy());
        populateDefaultCasePhase(input, result);
        return result;
    }

    final void populateDefaultCasePhase(final CasePhase input, final DefaultCasePhase result) {
        result.setPhaseType(input.getPhaseType());
        populateDefaultCase(input, result);
    }

    final void populateDefaultCase(final Case input, final DefaultCaseStandardNaming result) {
        result.setBillingPeriod(input.getBillingPeriod());
        result.setPriceList(toDefaultPriceList(input.getPriceList()));
        result.setProduct(toDefaultProduct(input.getProduct()));
        result.setProductCategory(input.getProductCategory());
    }

    final DefaultCaseChangePlanPolicy toDefaultCaseChangePlanPolicy(final CaseChangePlanPolicy input) {
        final DefaultCaseChangePlanPolicy result = new DefaultCaseChangePlanPolicy();
        result.setPolicy(input.getBillingActionPolicy());
        populateDefaultCaseChange(input, result);
        return result;
    }

    final DefaultCaseChangePlanAlignment toDefaultCaseChangePlanAlignment(final CaseChangePlanAlignment input) {
        final DefaultCaseChangePlanAlignment result = new DefaultCaseChangePlanAlignment();
        result.setAlignment(input.getAlignment());
        populateDefaultCaseChange(input, result);
        return result;
    }

    final void populateDefaultCaseChange(final CaseChange input, final DefaultCaseChange result) {
        result.setPhaseType(input.getPhaseType());
        result.setFromBillingPeriod(input.getFromBillingPeriod());
        result.setFromPriceList(toDefaultPriceList(input.getFromPriceList()));
        result.setFromProduct(toDefaultProduct(input.getFromProduct()));
        result.setFromProductCategory(input.getFromProductCategory());
        result.setToBillingPeriod(input.getToBillingPeriod());
        result.setToPriceList(toDefaultPriceList(input.getToPriceList()));
        result.setToProduct(toDefaultProduct(input.getToProduct()));
        result.setToProductCategory(input.getToProductCategory());
    }

    private Iterable<Product> toDefaultProducts(final Iterable<Product> input) {
        if (tmpDefaultProducts == null) {
            final Map<String, Product> map = new HashMap<>();
            for (final Product product : input) {
                map.put(product.getName(), toDefaultProduct(product));
            }
            tmpDefaultProducts = map;
        }
        return tmpDefaultProducts.values();
    }

    private Collection<Product> toFilteredDefaultProduct(final Collection<Product> input) {
        if (!input.iterator().hasNext()) {
            return Collections.emptyList();
        }
        final Iterable<String> inputProductNames = input.stream()
                .map(Product::getName)
                .collect(Collectors.toUnmodifiableList());
        final Collection<Product> filteredAndOrdered = new ArrayList<Product>(input.size());
        for (final String cur : inputProductNames) {
            final Product found = tmpDefaultProducts.get(cur);
            if (found == null) {
                throw new IllegalStateException("Failed to find product " + cur);
            }
            filteredAndOrdered.add(found);
        }
        return filteredAndOrdered;
    }

    private Iterable<Plan> toDefaultPlans(final StaticCatalog staticCatalog, final Iterable<Plan> input) {
        if (tmpDefaultPlans == null) {
            final Map<String, Plan> map = new HashMap<>();
            for (final Plan plan : input) {
                map.put(plan.getName(), toDefaultPlan(staticCatalog, plan));
            }
            tmpDefaultPlans = map;
        }
        return tmpDefaultPlans.values();

    }

    private Iterable<Plan> toFilterDefaultPlans(final String priceListName) {
        if (tmpDefaultPlans == null) {
            throw new IllegalStateException("Cannot filter on uninitialized plans");
        }
        return tmpDefaultPlans.values().stream()
                .filter(input -> ((DefaultPlan)input).getPriceListName().equals(priceListName))
                .collect(Collectors.toUnmodifiableList());
    }

    private DefaultPriceListSet toDefaultPriceListSet(final PriceList defaultPriceList, final Iterable<PriceList> childrenPriceLists) {
        if (tmpDefaultPriceListSet == null) {
            tmpDefaultPriceListSet = new DefaultPriceListSet(toDefaultPriceList(defaultPriceList), toDefaultPriceLists(childrenPriceLists));
        }
        return tmpDefaultPriceListSet;
    }

    private DefaultPlanPhase[] toDefaultPlanPhases(final Iterable<PlanPhase> input) {
        if (!input.iterator().hasNext()) {
            return new DefaultPlanPhase[0];
        }
        return toArrayWithTransform(input, this::toDefaultPlanPhase);
    }

    private DefaultPriceList[] toDefaultPriceLists(final Iterable<PriceList> input) {
        return toArrayWithTransform(input, this::toDefaultPriceList);
    }

    private DefaultPrice[] toDefaultPrices(final Iterable<Price> input) {
        return toArrayWithTransform(input, this::toDefaultPrice);
    }

    private DefaultUnit[] toDefaultUnits(final Iterable<Unit> input) {
        return toArrayWithTransform(input, this::toDefaultUnit);
    }

    private DefaultUnit toDefaultUnit(final Unit input) {
        final DefaultUnit result = new DefaultUnit();
        result.setName(input.getName());
        result.setPrettyName(input.getPrettyName());
        return result;
    }

    private DefaultPriceList toDefaultPriceList(@Nullable final PriceList input) {
        if (input == null) {
            return null;
        }

        DefaultPriceList result = tmpDefaultPriceListMap.get(input.getName());
        if (result == null) {
            result = new DefaultPriceList();
            result.setName(input.getName());
            result.setPlans(toFilterDefaultPlans(input.getName()));
            tmpDefaultPriceListMap.put(input.getName(), result);
        }
        return result;
    }

    private Product toDefaultProduct(@Nullable final Product input) {
        if (input == null) {
            return null;
        }
        if (tmpDefaultProducts != null) {
            final Product existingProduct = tmpDefaultProducts.get(input.getName());
            if (existingProduct == null) throw new IllegalStateException("Unknown product " + input.getName());
            return existingProduct;
        }
        final DefaultProduct result = new DefaultProduct();
        result.setCatalogName(catalogName);
        result.setCatagory(input.getCategory());
        result.setName(input.getName());
        result.setPrettyName(input.getPrettyName());
        return result;
    }

    private Plan toDefaultPlan(final StaticCatalog staticCatalog, final Plan input) {
        if (tmpDefaultPlans != null) {
            final Plan existingPlan = tmpDefaultPlans.get(input.getName());
            if (existingPlan == null) throw new IllegalStateException("Unknown plan " + input.getName());
            return existingPlan;
        }
        final DefaultPlan result = new DefaultPlan();
        result.setName(input.getName());
        result.setPrettyName(input.getPrettyName());
        result.setRecurringBillingMode(input.getRecurringBillingMode());
        result.setEffectiveDateForExistingSubscriptions(input.getEffectiveDateForExistingSubscriptions());
        result.setFinalPhase(toDefaultPlanPhase(input.getFinalPhase()));
        result.setInitialPhases(toDefaultPlanPhases(List.of(input.getInitialPhases())));
        result.setPlansAllowedInBundle(input.getPlansAllowedInBundle());
        result.setProduct(toDefaultProduct(input.getProduct()));
        result.setPriceListName(input.getPriceList().getName());
        result.initialize((StandaloneCatalog) staticCatalog);
        return result;
    }

    private DefaultPlanPhase toDefaultPlanPhase(final PlanPhase input) {
        final DefaultPlanPhase result = new DefaultPlanPhase();
        result.setDuration(toDefaultDuration(input.getDuration()));
        result.setFixed(toDefaultFixed(input.getFixed()));
        result.setPhaseType(input.getPhaseType());
        result.setRecurring(toDefaultRecurring(input.getRecurring()));
        if (input.getPrettyName() != null) {
            result.setPrettyName(input.getPrettyName());
        }
        if (input.getUsages() != null && input.getUsages().length > 0) {
            result.setUsages(toDefaultUsages(Arrays.asList(input.getUsages())));
        }
        return result;
    }

    private DefaultRecurring toDefaultRecurring(final Recurring input) {
        DefaultRecurring result = null;
        if (input != null) {
            result = new DefaultRecurring();
            result.setBillingPeriod(input.getBillingPeriod());
            result.setRecurringPrice(toDefaultInternationalPrice(input.getRecurringPrice()));
        }
        return result;
    }

    private final DefaultDuration toDefaultDuration(final Duration input) {
        final DefaultDuration result = new DefaultDuration();
        result.setNumber(input.getNumber());
        result.setUnit(input.getUnit());
        return result;
    }

    private final DefaultFixed toDefaultFixed(@Nullable final Fixed input) {
        DefaultFixed result = null;
        if (input != null) {
            result = new DefaultFixed();
            result.setFixedPrice(toDefaultInternationalPrice(input.getPrice()));
            result.setType(input.getType());
        }
        return result;
    }

    private DefaultInternationalPrice toDefaultInternationalPrice(final InternationalPrice input) {
        DefaultInternationalPrice result = null;
        if (input != null && input.getPrices() != null) {
            result = new DefaultInternationalPrice();
            result.setPrices(toDefaultPrices(List.of(input.getPrices())));
        }
        return result;
    }

    private DefaultPrice toDefaultPrice(final Price input) {
        try {
            final DefaultPrice result = new DefaultPrice();
            result.setCurrency(input.getCurrency());
            result.setValue(input.getValue());
            return result;
        } catch (CurrencyValueNull currencyValueNull) {
            throw new IllegalStateException(currencyValueNull);
        }
    }

    private DefaultUsage[] toDefaultUsages(final Iterable<Usage> input) {
        return toArrayWithTransform(input, this::toDefaultUsage);
    }

    private DefaultUsage toDefaultUsage(final Usage input) {
        final DefaultUsage result = new DefaultUsage();
        result.setName(input.getName());
        result.setBillingMode(input.getBillingMode());
        result.setBillingPeriod(input.getBillingPeriod());
        result.setUsageType(input.getUsageType());
        result.setTierBlockPolicy(input.getTierBlockPolicy());
        if (input.getPrettyName() != null) {
            result.setPrettyName(input.getPrettyName());
        }
        if (input.getLimits() != null && input.getLimits().length > 0) {
            result.setLimits(toDefaultLimits(Arrays.asList(input.getLimits())));
        }
        if (input.getBlocks() != null && input.getBlocks().length > 0) {
            result.setBlocks(toDefaultBlocks(Arrays.asList(input.getBlocks())));
        }
        if (input.getTiers() != null && input.getTiers().length > 0) {
            result.setTiers(toDefaultTiers(Arrays.asList(input.getTiers())));
        }
        result.setFixedPrice(toDefaultInternationalPrice(input.getFixedPrice()));
        result.setRecurringPrice(toDefaultInternationalPrice(input.getRecurringPrice()));
        return result;
    }

    private DefaultLimit[] toDefaultLimits(final Iterable<Limit> input) {
        return toArrayWithTransform(input, this::toDefaultLimit);
    }

    private DefaultLimit toDefaultLimit(final Limit input) {
        DefaultLimit result = null;
        if (input != null) {
            result = new DefaultLimit();
            result.setUnit(toDefaultUnit(input.getUnit()));
            result.setMax(input.getMax());
            result.setMin(input.getMin());
        }
        return result;
    }

    private DefaultBlock[] toDefaultBlocks(final Iterable<Block> input) {
        return toArrayWithTransform(input, this::toDefaultBlock);
    }

    private DefaultBlock toDefaultBlock(final Block input) {
        DefaultBlock result = null;
        if (input != null) {
            result = new DefaultBlock();
            result.setType(input.getType());
            result.setPrice(toDefaultInternationalPrice(input.getPrice()));
            result.setUnit(toDefaultUnit(input.getUnit()));
            result.setSize(input.getSize());
        }
        return result;
    }

    private DefaultTier[] toDefaultTiers(final Iterable<Tier> input) {
        return toArrayWithTransform(input, this::toDefaultTier);
    }

    private DefaultTier toDefaultTier(final Tier input) {
        DefaultTier result = null;
        if (input != null) {
            result = new DefaultTier();
            if (input.getLimits() != null && input.getLimits().length > 0) {
                result.setLimits(toDefaultLimits(Arrays.asList(input.getLimits())));
            }
            result.setFixedPrice(toDefaultInternationalPrice(input.getFixedPrice()));
            result.setRecurringPrice(toDefaultInternationalPrice(input.getRecurringPrice()));
            if (input.getTieredBlocks() != null && input.getTieredBlocks().length > 0) {
                result.setBlocks(toDefaultTieredBlocks(Arrays.asList(input.getTieredBlocks())));
            }
        }
        return result;
    }

    private DefaultTieredBlock[] toDefaultTieredBlocks(Iterable<TieredBlock> input) {
        return toArrayWithTransform(input, this::toDefaultTieredBlock);
    }

    private DefaultTieredBlock toDefaultTieredBlock(TieredBlock input) {
        DefaultTieredBlock result = null;
        if (input != null) {
            result = new DefaultTieredBlock();
            result.setUnit(toDefaultUnit(input.getUnit()));
            result.setMax(input.getMax());
            result.setType(input.getType());
            result.setSize(input.getSize());
            result.setPrice(toDefaultInternationalPrice(input.getPrice()));
        }
        return result;
    }

    private <I, C extends I> C[] toArrayWithTransform(final Iterable<I> input, final Function<I, C> transformer) {
        if (input == null || !input.iterator().hasNext()) {
            return null;
        }

        final Iterable<C> tmp = Iterables.toStream(input).map(transformer).collect(Collectors.toUnmodifiableList());
        return toArray(tmp);
    }

    private <C> C[] toArray(final Iterable<C> input) {
        if (!input.iterator().hasNext()) {
            throw new IllegalStateException("Nothing to convert into array");
        }
        final C[] foo = (C[]) java.lang.reflect.Array
                .newInstance(input.iterator().next().getClass(), 1);
        return Iterables.toUnmodifiableList(input).toArray(foo);
    }

}
