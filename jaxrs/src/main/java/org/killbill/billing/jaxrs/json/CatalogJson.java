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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.DefaultPriceListSet;
import org.killbill.billing.catalog.VersionedCatalog;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.CurrencyValueNull;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.TimeUnit;

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


    public CatalogJson(final VersionedCatalog catalog, final DateTime requestedDate) throws CatalogApiException {
        name = catalog.getCatalogName();
        effectiveDate = catalog.getEffectiveDate(requestedDate);
        currencies = Arrays.asList(catalog.getSupportedCurrencies(requestedDate));
        priceLists = new ArrayList<PriceListJson>();

        final Plan[] plans = catalog.getPlans(requestedDate);
        final Map<String, ProductJson> productMap = new HashMap<String, ProductJson>();
        for (final Plan plan : plans) {
            // Build the product associated with this plan
            final Product product = plan.getProduct();
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
                final PhaseJson phaseJson = new PhaseJson(phase.getPhaseType().toString(), prices, fixedPrices, durationJson);
                phases.add(phaseJson);
            }

            final PlanJson planJson = new PlanJson(plan.getName(), plan.getRecurringBillingPeriod(), phases);
            productJson.getPlans().add(planJson);
        }

        products = ImmutableList.<ProductJson>copyOf(productMap.values());

        final DefaultPriceListSet priceLists = catalog.getPriceLists(requestedDate);
        for (PriceList childPriceList : priceLists.getAllPriceLists()) {
            this.priceLists.add(new PriceListJson(childPriceList));
        }

    }

    private List<String> toProductNames(final Product[] in) {
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
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
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

    public static class PhaseJson {

        private final String type;
        private final List<PriceJson> prices;
        private final List<PriceJson> fixedPrices;
        private final DurationJson duration;

        @JsonCreator
        public PhaseJson(@JsonProperty("type") final String type,
                         @JsonProperty("prices") final List<PriceJson> prices,
                         @JsonProperty("fixedPrices") final List<PriceJson> fixedPrices,
                         @JsonProperty("duration") final DurationJson duration) {
            this.type = type;
            this.prices = prices;
            this.fixedPrices = fixedPrices;
            this.duration = duration;
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

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PhaseJson{");
            sb.append("type='").append(type).append('\'');
            sb.append(", prices=").append(prices);
            sb.append(", fixedPrices=").append(fixedPrices);
            sb.append(", duration=").append(duration);
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

            return true;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (prices != null ? prices.hashCode() : 0);
            result = 31 * result + (fixedPrices != null ? fixedPrices.hashCode() : 0);
            result = 31 * result + (duration != null ? duration.hashCode() : 0);
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

        public DurationJson(final Duration duration) throws CurrencyValueNull {
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
