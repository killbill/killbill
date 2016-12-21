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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.CurrencyValueNull;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.Tier;
import org.killbill.billing.catalog.api.TieredBlock;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.catalog.api.Usage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class CatalogJson {

    private final String name;
    private final Date effectiveDate;
    private final List<Currency> currencies;
    private final List<ProductJson> products;
    private final List<PriceListJson> priceLists;

    @JsonCreator
    public CatalogJson(@JsonProperty("name") final String name,
                       @JsonProperty("effectiveDate") final Date effectiveDate,
                       @JsonProperty("currencies") final List<Currency> currencies,
                       @JsonProperty("products") final List<ProductJson> products,
                       @JsonProperty("priceLists") final List<PriceListJson> priceLists) {
        this.name = name;
        this.effectiveDate = effectiveDate;
        this.currencies = currencies;
        this.products = products;
        this.priceLists = priceLists;
    }


    public CatalogJson(final Catalog catalog, final DateTime requestedDate) throws CatalogApiException {
        name = catalog.getCatalogName();
        effectiveDate = catalog.getStandaloneCatalogEffectiveDate(requestedDate);
        currencies = Arrays.asList(catalog.getSupportedCurrencies(requestedDate));
        priceLists = new ArrayList<PriceListJson>();

        final Collection<Plan> plans = catalog.getPlans(requestedDate);
        final Map<String, ProductJson> productMap = new HashMap<String, ProductJson>();
        for (final Plan plan : plans) {
            // Build the product associated with this plan
            final Product product = plan.getProduct();
            if (product == null) {
                // TODO Should we import a logger here?
                continue;
            }
            ProductJson productJson = productMap.get(product.getName());
            if (productJson == null) {
                productJson = new ProductJson(product.getCategory().toString(),
                                              product.getName(),
                                              toProductNames(product.getIncluded()),
                                              toProductNames(product.getAvailable()));
                productMap.put(product.getName(), productJson);
            }

            // Build the phases associated with this plan
            final List<PhaseJson> phases = new LinkedList<PhaseJson>();
            for (final PlanPhase phase : plan.getAllPhases()) {
                final List<PriceJson> prices = new LinkedList<PriceJson>();
                if (phase.getRecurring() != null && phase.getRecurring().getRecurringPrice() != null) {
                    for (final Price price : phase.getRecurring().getRecurringPrice().getPrices()) {
                        prices.add(new PriceJson(price));
                    }
                }

                final List<PriceJson> fixedPrices = new LinkedList<PriceJson>();
                if (phase.getFixed() != null && phase.getFixed().getPrice() != null) {
                    for (final Price price : phase.getFixed().getPrice().getPrices()) {
                        fixedPrices.add(new PriceJson(price));
                    }
                }

                final DurationJson durationJson = new DurationJson(phase.getDuration().getUnit(), phase.getDuration().getNumber());
                final List<UsageJson> usagesJson = buildUsagesJson(phase.getUsages());
                final PhaseJson phaseJson = new PhaseJson(phase.getPhaseType().toString(), prices, fixedPrices, durationJson, usagesJson);
                phases.add(phaseJson);
            }

            final PlanJson planJson = new PlanJson(plan.getName(), plan.getRecurringBillingPeriod(), phases);
            productJson.getPlans().add(planJson);
        }

        products = ImmutableList.<ProductJson>copyOf(productMap.values());

        final PriceListSet priceLists = catalog.getPriceLists(requestedDate);
        for (PriceList childPriceList : priceLists.getAllPriceLists()) {
            this.priceLists.add(new PriceListJson(childPriceList));
        }

    }

    private List<UsageJson> buildUsagesJson(final Usage[] usages) throws CurrencyValueNull {
        List<UsageJson> usagesJson = new ArrayList<UsageJson>();
        for (int i = 0; i < usages.length; i++) {
            usagesJson.add(new UsageJson(usages[i].getBillingPeriod().toString(), buildTiers(usages[i].getTiers())));
        }
        return usagesJson;
    }

    private List<TierJson> buildTiers(final Tier[] tiers) throws CurrencyValueNull {
        List<TierJson> tiersJson = new ArrayList<TierJson>();
        if (tiers != null && tiers.length > 0) {
            for (int i=0; i < tiers.length; i++) {
                tiersJson.add(new TierJson(buildTieredBlocks(tiers[i].getTieredBlocks()),
                                           buildLimits(tiers[i].getLimits()),
                                           buildPrices(tiers[i].getFixedPrice()),
                                           buildPrices(tiers[i].getRecurringPrice())));
            }
        }
        return tiersJson;
    }

    private List<LimitJson> buildLimits(final Limit[] limits) {
        List<LimitJson> limitsJson = new ArrayList<LimitJson>();
        if (limits != null && limits.length > 0) {
            for (int i=0; i < limits.length; i++) {
                limitsJson.add(new LimitJson(limits[i].getUnit().getName(),
                                             limits[i].getMax().toString(),
                                             limits[i].getMin().toString()));
            }
        }
        return limitsJson;
    }

    private List<TieredBlockJson> buildTieredBlocks(final TieredBlock[] tieredBlocks) throws CurrencyValueNull {
        List<TieredBlockJson> tieredBlocksJson = new ArrayList<TieredBlockJson>();
        if (tieredBlocks != null && tieredBlocks.length > 0) {
            for (int i=0; i < tieredBlocks.length; i++) {
                tieredBlocksJson.add(new TieredBlockJson(tieredBlocks[i].getUnit().getName(),
                                                         tieredBlocks[i].getSize().toString(),
                                                         tieredBlocks[i].getMax().toString(),
                                                         buildPrices(tieredBlocks[i].getPrice())));
            }
        }
        return tieredBlocksJson;
    }

    private List<PriceJson> buildPrices(final InternationalPrice internationalPrice) throws CurrencyValueNull {
        List<PriceJson> pricesJson = new ArrayList<PriceJson>();
        Price[] prices = (internationalPrice != null) ? internationalPrice.getPrices() : null;
        if (prices != null && prices.length > 0) {
            for (int i=0; i < prices.length; i++) {
                pricesJson.add(new PriceJson(prices[i].getCurrency().name(),
                                             prices[i].getValue()));
            }
        }
        return pricesJson;
    }

    private List<String> toProductNames(final Collection<Product> in) {
        return Lists.transform(ImmutableList.<Product>copyOf(in),
                               new Function<Product, String>() {
                                   @Override
                                   public String apply(final Product input) {
                                       return input.getName();
                                   }
                               });
    }

    public List<ProductJson> getProducts() {
        return products;
    }

    public String getName() {
        return name;
    }

    public Date getEffectiveDate() {
        return effectiveDate;
    }

    public List<Currency> getCurrencies() {
        return currencies;
    }

    public List<PriceListJson> getPriceLists() {
        return priceLists;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CatalogJson{");
        sb.append("name='").append(name).append('\'');
        sb.append(", effectiveDate='").append(effectiveDate).append('\'');
        sb.append(", currencies='").append(currencies).append('\'');
        sb.append(", products=").append(products);
        sb.append(", priceLists=").append(priceLists);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final CatalogJson that = (CatalogJson) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        if (currencies != null ? !currencies.equals(that.currencies) : that.currencies != null) {
            return false;
        }
        if (products != null ? !products.equals(that.products) : that.products != null) {
            return false;
        }
        if (priceLists != null ? !priceLists.equals(that.priceLists) : that.priceLists != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (currencies != null ? currencies.hashCode() : 0);
        result = 31 * result + (products != null ? products.hashCode() : 0);
        return result;
    }

    public static class ProductJson {

        private final String type;
        private final String name;
        private final List<PlanJson> plans;
        private final List<String> included;
        private final List<String> available;

        @JsonCreator
        public ProductJson(@JsonProperty("type") final String type,
                           @JsonProperty("name") final String name,
                           @JsonProperty("plans") final List<PlanJson> plans,
                           @JsonProperty("included") final List<String> included,
                           @JsonProperty("available") final List<String> available) {
            this.type = type;
            this.name = name;
            this.plans = plans;
            this.included = included;
            this.available = available;
        }

        public ProductJson(final String type, final String name, final List<String> included, final List<String> available) {
            this(type, name, new LinkedList<PlanJson>(), included, available);
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public List<PlanJson> getPlans() {
            return plans;
        }

        public List<String> getIncluded() {
            return included;
        }

        public List<String> getAvailable() {
            return available;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ProductJson{");
            sb.append("type='").append(type).append('\'');
            sb.append(", name='").append(name).append('\'');
            sb.append(", plans=").append(plans);
            sb.append(", included=").append(included);
            sb.append(", available=").append(available);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final ProductJson that = (ProductJson) o;

            if (available != null ? !available.equals(that.available) : that.available != null) {
                return false;
            }
            if (included != null ? !included.equals(that.included) : that.included != null) {
                return false;
            }
            if (name != null ? !name.equals(that.name) : that.name != null) {
                return false;
            }
            if (plans != null ? !plans.equals(that.plans) : that.plans != null) {
                return false;
            }
            if (type != null ? !type.equals(that.type) : that.type != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (plans != null ? plans.hashCode() : 0);
            result = 31 * result + (included != null ? included.hashCode() : 0);
            result = 31 * result + (available != null ? available.hashCode() : 0);
            return result;
        }
    }

    public static class PlanJson {

        private final String name;
        private final BillingPeriod billingPeriod;
        private final List<PhaseJson> phases;

        @JsonCreator
        public PlanJson(@JsonProperty("name") final String name,
                        @JsonProperty("billingPeriod") final BillingPeriod billingPeriod,
                        @JsonProperty("phases") final List<PhaseJson> phases) {
            this.name = name;
            this.billingPeriod = billingPeriod;
            this.phases = phases;
        }

        public String getName() {
            return name;
        }

        public BillingPeriod getBillingPeriod() {
            return billingPeriod;
        }

        public List<PhaseJson> getPhases() {
            return phases;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PlanJson{");
            sb.append("name='").append(name).append('\'');
            sb.append("billingPeriod='").append(billingPeriod).append('\'');
            sb.append(", phases=").append(phases);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final PlanJson planJson = (PlanJson) o;

            if (name != null ? !name.equals(planJson.name) : planJson.name != null) {
                return false;
            }
            if (billingPeriod != null ? !billingPeriod.equals(planJson.billingPeriod) : planJson.billingPeriod != null) {
                return false;
            }
            if (phases != null ? !phases.equals(planJson.phases) : planJson.phases != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
            result = 31 * result + (phases != null ? phases.hashCode() : 0);
            return result;
        }
    }

    public static class TieredBlockJson {
        private final String unit;
        private final String size;
        private final String max;
        private final List<PriceJson> prices;

        @JsonCreator
        public TieredBlockJson(@JsonProperty("unit") final String unit,
                               @JsonProperty("size") final String size,
                               @JsonProperty("max") final String max,
                               @JsonProperty("prices") final List<PriceJson> prices) {
            this.unit = unit;
            this.size = size;
            this.max = max;
            this.prices = prices;
        }

        public String getUnit() {
            return unit;
        }
        public String getSize() {
            return size;
        }
        public String getMax() {
            return max;
        }
        public List<PriceJson> getPrices() {
            return prices;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("TieredBlockJson{");
            sb.append("unit='").append(unit).append('\'');
            sb.append(", size=").append(size);
            sb.append(", max=").append(max);
            sb.append(", prices=").append(prices);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final TieredBlockJson blockJson = (TieredBlockJson) o;

            if (unit != null ? !unit.equals(blockJson.unit) : blockJson.unit != null) {
                return false;
            }
            if (size != null ? !size.equals(blockJson.size) : blockJson.size != null) {
                return false;
            }
            if (max != null ? !max.equals(blockJson.max) : blockJson.max != null) {
                return false;
            }
            if (prices != null ? !prices.equals(blockJson.prices) : blockJson.prices != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = unit != null ? unit.hashCode() : 0;
            result = 31 * result + (size != null ? size.hashCode() : 0);
            result = 31 * result + (max != null ? max.hashCode() : 0);
            result = 31 * result + (prices != null ? prices.hashCode() : 0);
            return result;
        }
    }

    public static class LimitJson {
        private final String unit;
        private final String max;
        private final String min;

        @JsonCreator
        public LimitJson(@JsonProperty("unit") final String unit,
                         @JsonProperty("max") final String max,
                         @JsonProperty("min") final String min) {
            this.unit = unit;
            this.max = max;
            this.min = min;
        }

        public String getUnit() {
            return unit;
        }
        public String getMax() {
            return max;
        }
        public String getMin() {
            return min;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("LimitJson{");
            sb.append("unit='").append(unit).append('\'');
            sb.append(", max=").append(max);
            sb.append(", min=").append(min);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final LimitJson limitJson = (LimitJson) o;

            if (unit != null ? !unit.equals(limitJson.unit) : limitJson.unit != null) {
                return false;
            }
            if (min != null ? !min.equals(limitJson.min) : limitJson.min != null) {
                return false;
            }
            if (max != null ? !max.equals(limitJson.max) : limitJson.max != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = unit != null ? unit.hashCode() : 0;
            result = 31 * result + (max != null ? max.hashCode() : 0);
            result = 31 * result + (min != null ? min.hashCode() : 0);
            return result;
        }
    }

    public static class TierJson {
        private final List<TieredBlockJson> blocks;
        private final List<LimitJson> limits;
        private final List<PriceJson> fixedPrice;
        private final List<PriceJson> recurringPrice;

        @JsonCreator
        public TierJson(@JsonProperty("tiers") final List<TieredBlockJson> blocks,
                        @JsonProperty("limits") final List<LimitJson> limits,
                        @JsonProperty("fixedPrice") final List<PriceJson> fixedPrice,
                        @JsonProperty("recurringPrice") final List<PriceJson> recurringPrice) {
            this.blocks = blocks;
            this.limits = limits;
            this.fixedPrice = fixedPrice;
            this.recurringPrice = recurringPrice;
        }

        public List<TieredBlockJson> getBlocks() {
            return blocks;
        }
        public List<LimitJson> getLimits() {
            return limits;
        }
        public List<PriceJson> getFixedPrice() {
            return fixedPrice;
        }
        public List<PriceJson> getRecurringPrice() {
            return recurringPrice;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("TierJson{");
            sb.append("blocks='").append(blocks);
            sb.append(", limits=").append(limits);
            sb.append(", fixedPrice=").append(fixedPrice);
            sb.append(", recurringPrice=").append(recurringPrice);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final TierJson tierJson = (TierJson) o;

            if (blocks != null ? !blocks.equals(tierJson.blocks) : tierJson.blocks != null) {
                return false;
            }
            if (limits != null ? !limits.equals(tierJson.limits) : tierJson.limits != null) {
                return false;
            }
            if (fixedPrice != null ? !fixedPrice.equals(tierJson.fixedPrice) : tierJson.fixedPrice != null) {
                return false;
            }
            if (recurringPrice != null ? !recurringPrice.equals(tierJson.recurringPrice) : tierJson.recurringPrice != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = blocks != null ? blocks.hashCode() : 0;
            result = 31 * result + (limits != null ? limits.hashCode() : 0);
            result = 31 * result + (fixedPrice != null ? fixedPrice.hashCode() : 0);
            result = 31 * result + (recurringPrice != null ? recurringPrice.hashCode() : 0);
            return result;
        }
    }

    public static class UsageJson {
        private final String billingPeriod;
        private final List<TierJson> tiers;

        @JsonCreator
        public UsageJson(@JsonProperty("billingPeriod") final String billingPeriod,
                        @JsonProperty("tiers") final List<TierJson> tiers) {
            this.billingPeriod = billingPeriod;
            this.tiers = tiers;
        }

        public String getBillingPeriod() {
            return billingPeriod;
        }
        public List<TierJson> getTiers() {
            return tiers;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("UsageJson{");
            sb.append("billingPeriod='").append(billingPeriod).append('\'');
            sb.append(", tiers=").append(tiers);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final UsageJson usageJson = (UsageJson) o;

            if (billingPeriod != null ? !billingPeriod.equals(usageJson.billingPeriod) : usageJson.billingPeriod != null) {
                return false;
            }
            if (tiers != null ? !tiers.equals(usageJson.tiers) : usageJson.tiers != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = billingPeriod != null ? billingPeriod.hashCode() : 0;
            result = 31 * result + (tiers != null ? tiers.hashCode() : 0);
            return result;
        }
    }

    public static class PhaseJson {

        private final String type;
        private final List<PriceJson> prices;
        private final List<PriceJson> fixedPrices;
        private final DurationJson duration;
        private final List<UsageJson> usages;

        @JsonCreator
        public PhaseJson(@JsonProperty("type") final String type,
                         @JsonProperty("prices") final List<PriceJson> prices,
                         @JsonProperty("fixedPrices") final List<PriceJson> fixedPrices,
                         @JsonProperty("duration") final DurationJson duration,
                         @JsonProperty("usages") final List<UsageJson> usages) {
            this.type = type;
            this.prices = prices;
            this.fixedPrices = fixedPrices;
            this.duration = duration;
            this.usages = usages;
        }

        public String getType() {
            return type;
        }
        public List<PriceJson> getPrices() {
            return prices;
        }
        public List<PriceJson> getFixedPrices() {
            return fixedPrices;
        }
        public DurationJson getDuration() {
            return duration;
        }
        public List<UsageJson> getUsages() {
            return usages;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PhaseJson{");
            sb.append("type='").append(type).append('\'');
            sb.append(", prices=").append(prices);
            sb.append(", fixedPrices=").append(fixedPrices);
            sb.append(", duration=").append(duration);
            sb.append(", usages=").append(usages);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final PhaseJson phaseJson = (PhaseJson) o;

            if (prices != null ? !prices.equals(phaseJson.prices) : phaseJson.prices != null) {
                return false;
            }
            if (fixedPrices != null ? !fixedPrices.equals(phaseJson.fixedPrices) : phaseJson.fixedPrices != null) {
                return false;
            }
            if (type != null ? !type.equals(phaseJson.type) : phaseJson.type != null) {
                return false;
            }
            if (duration != null ? !duration.equals(phaseJson.duration) : phaseJson.duration != null) {
                return false;
            }
            if (usages != null ? !usages.equals(phaseJson.usages) : phaseJson.usages!= null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (prices != null ? prices.hashCode() : 0);
            result = 31 * result + (fixedPrices != null ? fixedPrices.hashCode() : 0);
            result = 31 * result + (duration != null ? duration.hashCode() : 0);
            result = 31 * result + (usages != null ? usages.hashCode() : 0);
            return result;
        }
    }

    public static class PriceJson {

        private final String currency;
        private final BigDecimal value;

        @JsonCreator
        public PriceJson(@JsonProperty("currency") final String currency,
                         @JsonProperty("value") final BigDecimal value) {
            this.currency = currency;
            this.value = value;
        }

        public PriceJson(final Price price) throws CurrencyValueNull {
            this(price.getCurrency().toString(), price.getValue());
        }

        public String getCurrency() {
            return currency;
        }

        public BigDecimal getValue() {
            return value;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PriceJson{");
            sb.append("currency='").append(currency).append('\'');
            sb.append(", value=").append(value);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final PriceJson priceJson = (PriceJson) o;

            if (currency != null ? !currency.equals(priceJson.currency) : priceJson.currency != null) {
                return false;
            }
            if (value != null ? !value.equals(priceJson.value) : priceJson.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = currency != null ? currency.hashCode() : 0;
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }
    }

    public static class PriceListJson {

        private String name;
        private List<String> plans;

        @JsonCreator
        public PriceListJson(@JsonProperty("name") final String name,
                             @JsonProperty("plans") final List<String> plans) {
            this.name = name;
            this.plans = plans;
        }

        public PriceListJson(final PriceList priceList) {
            this.name = priceList.getName();
            List<String> plans = new ArrayList<String>();
            for (Plan plan : priceList.getPlans()) {
                plans.add(plan.getName());
            }
            this.plans = plans;
        }

        public String getName() {
            return name;
        }

        public List<String> getPlans() {
            return plans;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PriceListJson{");
            sb.append("name='").append(name).append('\'');
            sb.append(", plans=").append(plans);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final PriceListJson that = (PriceListJson) o;

            if (name != null ? !name.equals(that.name) : that.name != null) {
                return false;
            }
            return !(plans != null ? !plans.equals(that.plans) : that.plans != null);

        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (plans != null ? plans.hashCode() : 0);
            return result;
        }

    }

    public static class DurationJson {

        private final TimeUnit unit;
        private final int number;

        @JsonCreator
        public DurationJson(@JsonProperty("unit") final TimeUnit unit,
                            @JsonProperty("number") final int number) {
            this.unit = unit;
            this.number = number;
        }

        public DurationJson(final Duration duration) {
            this(duration.getUnit(), duration.getNumber());
        }

        public TimeUnit getUnit() {
            return unit;
        }

        public int getNumber() {
            return number;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("DurationJson{");
            sb.append("unit='").append(unit).append('\'');
            sb.append(", number=").append(number);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final DurationJson that = (DurationJson) o;

            if (unit != null ? !unit.equals(that.unit) : that.unit != null) {
                return false;
            }

            return number == that.number;

        }

        @Override
        public int hashCode() {
            int result = unit != null ? unit.hashCode() : 0;
            result = 31 * result + number;
            return result;
        }
    }
}
