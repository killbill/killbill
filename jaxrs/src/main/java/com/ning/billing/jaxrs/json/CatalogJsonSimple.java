/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CurrencyValueNull;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Price;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.StaticCatalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class CatalogJsonSimple {

    private final String name;
    private final List<ProductJson> products;

    @JsonCreator
    public CatalogJsonSimple(@JsonProperty("name") final String name,
                             @JsonProperty("products") final List<ProductJson> products) {
        this.name = name;
        this.products = products;
    }

    public CatalogJsonSimple(final StaticCatalog catalog) throws CatalogApiException {
        name = catalog.getCatalogName();

        final Plan[] plans = catalog.getCurrentPlans();
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
                if (phase.getRecurringPrice() != null) {
                    for (final Price price : phase.getRecurringPrice().getPrices()) {
                        prices.add(new PriceJson(price));
                    }
                }

                final PhaseJson phaseJson = new PhaseJson(phase.getPhaseType().toString(), prices);
                phases.add(phaseJson);
            }

            final PlanJson planJson = new PlanJson(plan.getName(), phases);
            productJson.getPlans().add(planJson);
        }

        products = ImmutableList.<ProductJson>copyOf(productMap.values());
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CatalogJsonSimple{");
        sb.append("name='").append(name).append('\'');
        sb.append(", products=").append(products);
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

        final CatalogJsonSimple that = (CatalogJsonSimple) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (products != null ? !products.equals(that.products) : that.products != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
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
        private final List<PhaseJson> phases;

        @JsonCreator
        public PlanJson(@JsonProperty("name") final String name,
                        @JsonProperty("phases") final List<PhaseJson> phases) {
            this.name = name;
            this.phases = phases;
        }

        public String getName() {
            return name;
        }

        public List<PhaseJson> getPhases() {
            return phases;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PlanJson{");
            sb.append("name='").append(name).append('\'');
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
            if (phases != null ? !phases.equals(planJson.phases) : planJson.phases != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (phases != null ? phases.hashCode() : 0);
            return result;
        }
    }

    public static class PhaseJson {

        private final String type;
        private final List<PriceJson> prices;

        @JsonCreator
        public PhaseJson(@JsonProperty("type") final String type,
                         @JsonProperty("prices") final List<PriceJson> prices) {
            this.type = type;
            this.prices = prices;
        }

        public String getType() {
            return type;
        }

        public List<PriceJson> getPrices() {
            return prices;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PhaseJson{");
            sb.append("type='").append(type).append('\'');
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

            final PhaseJson phaseJson = (PhaseJson) o;

            if (prices != null ? !prices.equals(phaseJson.prices) : phaseJson.prices != null) {
                return false;
            }
            if (type != null ? !type.equals(phaseJson.type) : phaseJson.type != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (prices != null ? prices.hashCode() : 0);
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
}
