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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.killbill.billing.catalog.DefaultDuration;
import org.killbill.billing.catalog.DefaultFixed;
import org.killbill.billing.catalog.DefaultInternationalPrice;
import org.killbill.billing.catalog.DefaultPlan;
import org.killbill.billing.catalog.DefaultPlanPhase;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.DefaultPriceListSet;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.DefaultRecurring;
import org.killbill.billing.catalog.DefaultUnit;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.PriceListDefault;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.CurrencyValueNull;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.Fixed;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.Recurring;
import org.killbill.billing.catalog.api.Unit;
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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class StandaloneCatalogMapper {

    private final String catalogName;
    private final BillingMode recurringBillingMode;

    private Iterable<DefaultProduct> tmpDefaultProducts;
    private Iterable<DefaultPlan> tmpDefaultPlans;
    private DefaultPriceListSet tmpDefaultPriceListSet;

    public StandaloneCatalogMapper(final String catalogName, final BillingMode recurringBillingMode) {
        this.catalogName = catalogName;
        this.recurringBillingMode = recurringBillingMode;
        this.tmpDefaultProducts = null;
        this.tmpDefaultPlans = null;
    }

    public StandaloneCatalog toStandaloneCatalog(final StandalonePluginCatalog pluginCatalog, @Nullable URI catalogURI) {
        final StandaloneCatalog result = new StandaloneCatalog();
        result.setCatalogName(catalogName);
        result.setEffectiveDate(pluginCatalog.getEffectiveDate().toDate());
        result.setProducts(toDefaultProducts(pluginCatalog.getProducts()));
        result.setPlans(toDefaultPlans(pluginCatalog.getPlans()));
        result.setPriceLists(toDefaultPriceListSet(pluginCatalog.getDefaultPriceList(), pluginCatalog.getChildrenPriceList()));
        result.setRecurringBillingMode(recurringBillingMode);
        result.setSupportedCurrencies(toArray(pluginCatalog.getCurrencies()));
        result.setUnits(toDefaultUnits(pluginCatalog.getUnits()));
        result.setPlanRules(toDefaultPlanRules(pluginCatalog.getPlanRules()));

        for (final Product cur : pluginCatalog.getProducts()) {
            for (DefaultProduct target : result.getCurrentProducts()) {
                if (target.getName().equals(cur.getName())) {
                    target.setAvailable(toFilteredDefaultProduct(ImmutableList.copyOf(cur.getAvailable())));
                    target.setIncluded(toFilteredDefaultProduct(ImmutableList.copyOf(cur.getIncluded())));
                    break;
                }
            }
        }
        result.initialize(result, catalogURI);
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
        return toArrayWithTransform(input, new Function<CaseChangePlanPolicy, DefaultCaseChangePlanPolicy>() {
            @Override
            public DefaultCaseChangePlanPolicy apply(final CaseChangePlanPolicy input) {
                return toDefaultCaseChangePlanPolicy(input);
            }
        }, true);
    }

    final DefaultCaseChangePlanAlignment[] toDefaultCaseChangePlanAlignments(final Iterable<CaseChangePlanAlignment> input) {
        return toArrayWithTransform(input, new Function<CaseChangePlanAlignment, DefaultCaseChangePlanAlignment>() {
            @Override
            public DefaultCaseChangePlanAlignment apply(final CaseChangePlanAlignment input) {
                return toDefaultCaseChangePlanAlignment(input);
            }
        }, true);
    }

    final DefaultCaseBillingAlignment[] toDefaultCaseBillingAlignments(final Iterable<CaseBillingAlignment> input) {
        return toArrayWithTransform(input, new Function<CaseBillingAlignment, DefaultCaseBillingAlignment>() {
            @Override
            public DefaultCaseBillingAlignment apply(final CaseBillingAlignment input) {
                return toDefaultCaseBillingAlignment(input);
            }
        }, true);
    }

    final DefaultCaseCancelPolicy[] toDefaultCaseCancelPolicys(final Iterable<CaseCancelPolicy> input) {
        return toArrayWithTransform(input, new Function<CaseCancelPolicy, DefaultCaseCancelPolicy>() {
            @Override
            public DefaultCaseCancelPolicy apply(final CaseCancelPolicy input) {
                return toDefaultCaseCancelPolicy(input);
            }
        }, true);
    }

    final DefaultCaseCreateAlignment[] toDefaultCaseCreateAlignments(final Iterable<CaseCreateAlignment> input) {
        return toArrayWithTransform(input, new Function<CaseCreateAlignment, DefaultCaseCreateAlignment>() {
            @Override
            public DefaultCaseCreateAlignment apply(final CaseCreateAlignment input) {
                return toCaseCreateAlignment(input);
            }
        }, true);
    }

    final DefaultCasePriceList[] toDefaultCasePriceLists(final Iterable<CasePriceList> input) {
        return toArrayWithTransform(input, new Function<CasePriceList, DefaultCasePriceList>() {
            @Override
            public DefaultCasePriceList apply(final CasePriceList input) {
                return toDefaultCasePriceList(input);
            }
        }, true);
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


    private DefaultProduct[] toDefaultProducts(final Iterable<Product> input) {
        if (tmpDefaultProducts == null) {
            final Function<Product, DefaultProduct> productTransformer = new Function<Product, DefaultProduct>() {
                @Override
                public DefaultProduct apply(final Product input) {
                    return toDefaultProduct(input);
                }
            };
            tmpDefaultProducts = ImmutableList.copyOf(Iterables.transform(input, productTransformer));
        }
        return toArray(tmpDefaultProducts);
    }

    private DefaultProduct[] toFilteredDefaultProduct(final Iterable<Product> input) {
        if (!input.iterator().hasNext()) {
            return new DefaultProduct[0];
        }
        final List<String> inputProductNames = ImmutableList.copyOf(Iterables.transform(input, new Function<Product, String>() {
            @Override
            public String apply(final Product input) {
                return input.getName();
            }
        }));
        final List<DefaultProduct> filteredAndOrdered = new ArrayList<DefaultProduct>(inputProductNames.size());
        for (final String cur : inputProductNames) {
            final DefaultProduct found = findOrIllegalState(tmpDefaultProducts, new Predicate<DefaultProduct>() {
                @Override
                public boolean apply(final DefaultProduct inputPredicate) {
                    return inputPredicate.getName().equals(cur);
                }
            }, "Failed to find product " + cur);
            filteredAndOrdered.add(found);
        }
        return toArray(filteredAndOrdered);
    }

    private DefaultPlan[] toDefaultPlans(final Iterable<Plan> input) {
        if (tmpDefaultPlans == null) {
            final Function<Plan, DefaultPlan> planTransformer = new Function<Plan, DefaultPlan>() {
                @Override
                public DefaultPlan apply(final Plan input) {
                    return toDefaultPlan(input);
                }
            };
            tmpDefaultPlans = ImmutableList.copyOf(Iterables.transform(input, planTransformer));
        }
        return toArray(tmpDefaultPlans);
    }

    private DefaultPlan[] toFilterDefaultPlans(final Iterable<Plan> input) {
        if (tmpDefaultPlans == null) {
            throw new IllegalStateException("Cannot filter on uninitialized plans");
        }
        final List<String> inputPlanNames = ImmutableList.copyOf(Iterables.transform(input, new Function<Plan, String>() {
            @Override
            public String apply(final Plan input) {
                return input.getName();
            }
        }));
        final List<DefaultPlan> filteredAndOrdered = new ArrayList<DefaultPlan>(inputPlanNames.size());
        for (final String cur : inputPlanNames) {
            final DefaultPlan found = findOrIllegalState(tmpDefaultPlans, new Predicate<DefaultPlan>() {
                @Override
                public boolean apply(final DefaultPlan inputPredicate) {
                    return inputPredicate.getName().equals(cur);
                }
            }, "Failed to find plan " + cur);
            filteredAndOrdered.add(found);
        }
        return toArray(filteredAndOrdered);
    }

    private DefaultPriceListSet toDefaultPriceListSet(final PriceList defaultPriceList, final Iterable<PriceList> childrenPriceLists) {
        if (tmpDefaultPriceListSet == null) {
            tmpDefaultPriceListSet = new DefaultPriceListSet(toPriceListDefault(defaultPriceList), toDefaultPriceLists(childrenPriceLists));
        }
        return tmpDefaultPriceListSet;
    }

    private DefaultPlanPhase[] toDefaultPlanPhases(final Iterable<PlanPhase> input) {
        if (!input.iterator().hasNext()) {
            return new DefaultPlanPhase[0];
        }
        return toArrayWithTransform(input, new Function<PlanPhase, DefaultPlanPhase>() {
            @Override
            public DefaultPlanPhase apply(final PlanPhase input) {
                return toDefaultPlanPhase(input);
            }
        }, false);
    }

    private DefaultPriceList[] toDefaultPriceLists(final Iterable<PriceList> input) {
        return toArrayWithTransform(input, new Function<PriceList, DefaultPriceList>() {
            @Override
            public DefaultPriceList apply(final PriceList input) {
                return toDefaultPriceList(input);
            }
        }, false);
    }

    private DefaultPrice[] toDefaultPrices(final Iterable<Price> input) {
        return toArrayWithTransform(input, new Function<Price, DefaultPrice>() {
            @Override
            public DefaultPrice apply(final Price input) {
                return toDefaultPrice(input);
            }
        }, false);
    }

    private DefaultUnit[] toDefaultUnits(final Iterable<Unit> input) {
        return toArrayWithTransform(input, new Function<Unit, DefaultUnit>() {
            @Override
            public DefaultUnit apply(final Unit inputTransform) {
                return toDefaultUnit(inputTransform);
            }
        }, true);
    }

    private DefaultUnit toDefaultUnit(final Unit input) {
        final DefaultUnit result = new DefaultUnit();
        result.setName(input.getName());
        return result;
    }


    private DefaultPriceList toDefaultPriceList(@Nullable final PriceList input) {
        if (input == null) {
            return null;
        }
        final DefaultPriceList result = new DefaultPriceList();
        result.setName(input.getName());
        result.setPlans(toFilterDefaultPlans(ImmutableList.copyOf(input.getPlans())));
        result.setRetired(input.isRetired());
        return result;
    }

    private PriceListDefault toPriceListDefault(@Nullable final PriceList input) {
        if (input == null) {
            return null;
        }
        final PriceListDefault result = new PriceListDefault();
        result.setName(input.getName());
        result.setPlans(toFilterDefaultPlans(ImmutableList.copyOf(input.getPlans())));
        result.setRetired(input.isRetired());
        return result;
    }

    private DefaultProduct toDefaultProduct(@Nullable final Product input) {
        if (input == null) {
            return null;
        }
        if (tmpDefaultProducts != null) {
            final DefaultProduct existingProduct = findOrIllegalState(tmpDefaultProducts, new Predicate<DefaultProduct>() {
                @Override
                public boolean apply(final DefaultProduct predicateInput) {
                    return predicateInput.getName().equals(input.getName());
                }
            }, "Unknown product " + input.getName());
            return existingProduct;
        }
        final DefaultProduct result = new DefaultProduct();
        result.setCatalogName(catalogName);
        result.setCatagory(input.getCategory());
        result.setName(input.getName());
        result.setRetired(input.isRetired());
        return result;
    }

    private DefaultPlan toDefaultPlan(final Plan input) {
        if (tmpDefaultPlans != null) {
            final DefaultPlan existingPlan = findOrIllegalState(tmpDefaultPlans, new Predicate<DefaultPlan>() {
                @Override
                public boolean apply(final DefaultPlan predicateInput) {
                    return predicateInput.getName().equals(input.getName());
                }
            }, "Unknown plan " + input.getName());
            return existingPlan;
        }
        final DefaultPlan result = new DefaultPlan();
        result.setName(input.getName());
        result.setRetired(input.isRetired());
        result.setEffectiveDateForExistingSubscriptons(input.getEffectiveDateForExistingSubscriptons());
        result.setFinalPhase(toDefaultPlanPhase(input.getFinalPhase()));
        result.setInitialPhases(toDefaultPlanPhases(ImmutableList.copyOf(input.getInitialPhases())));
        result.setPlansAllowedInBundle(input.getPlansAllowedInBundle());
        result.setProduct(toDefaultProduct(input.getProduct()));
        return result;
    }

    private DefaultPlanPhase toDefaultPlanPhase(final PlanPhase input) {
        final DefaultPlanPhase result = new DefaultPlanPhase();
        result.setDuration(toDefaultDuration(input.getDuration()));
        result.setFixed(toDefaultFixed(input.getFixed()));
        result.setPhaseType(input.getPhaseType());
        result.setRecurring(toDefaultRecurring(input.getRecurring()));
        result.setUsages(new DefaultUsage[0]);
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
        final DefaultInternationalPrice result = new DefaultInternationalPrice();
        result.setPrices(toDefaultPrices(ImmutableList.copyOf(input.getPrices())));
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

    private <I, C extends I> C[] toArrayWithTransform(final Iterable<I> input, final Function<I, C> transformer, boolean returnNullIfNothing) {
        if (returnNullIfNothing && (input == null || !input.iterator().hasNext())) {
            return null;
        }
        final Iterable<C> tmp = Iterables.transform(input, transformer);
        return toArray(tmp);
    }

    private <C> C[] toArray(final Iterable<C> input) {
        if (!input.iterator().hasNext()) {
            throw new IllegalStateException("Nothing to convert into array");
        }
        final C[] foo = (C[]) java.lang.reflect.Array
                .newInstance(input.iterator().next().getClass(), 1);
        return ImmutableList.<C>copyOf(input).toArray(foo);
    }

    private <T> T findOrIllegalState(final Iterable<T> input, final Predicate<T> predicate, final String msg) {
        T result = Iterables.<T> tryFind(input, predicate).orNull();
        if (result == null) {
            throw new IllegalStateException(msg);
        }
        return result;
    }
}
