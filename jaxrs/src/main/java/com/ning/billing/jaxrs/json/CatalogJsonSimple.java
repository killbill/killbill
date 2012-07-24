/*
 * Copyright 2010-2011 Ning, Inc.
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.Price;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.StaticCatalog;

@JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
public class CatalogJsonSimple {

    private final ProductJson [] products;

    public CatalogJsonSimple(StaticCatalog catalog) throws CatalogApiException {

        Map<String, ProductJson> productMap = new HashMap<String, CatalogJsonSimple.ProductJson>();

        Plan [] plans = catalog.getCurrentPlans();
        for (Plan plan : plans) {

            Product product = plan.getProduct();
            ProductJson jProduct = productMap.get(product.getName());
            if (jProduct == null) {
                jProduct = new ProductJson(product.getCategory().toString(),
                        product.getName(),
                        toProductNames(product.getIncluded()), toProductNames(product.getAvailable()));
                productMap.put(product.getName(), jProduct);
            }

            int i = 0 ;
            PhaseJson [] phases = new PhaseJson[plan.getAllPhases().length];
            for (PlanPhase phase : plan.getAllPhases()) {

                Map<String, BigDecimal> prices = new HashMap<String, BigDecimal>();
                if (phase.getRecurringPrice() != null) {
                    for (Price cur : phase.getRecurringPrice().getPrices()) {
                        prices.put(cur.getCurrency().toString(), cur.getValue());
                    }
                }
                PhaseJson jPhase = new PhaseJson(phase.getPhaseType().toString(), prices);
                phases[i++] = jPhase;
            }
            PlanJson jPlan = new PlanJson(plan.getName(), phases);
            jProduct.addPlan(jPlan);
        }
        products = productMap.values().toArray(new ProductJson[productMap.values().size()]);
    }

    private Collection<String> toProductNames(Product [] in) {
        return Collections2.transform(Lists.newArrayList(in), new Function<Product, String>() {
            @Override
            public String apply(Product input) {
                return input.getName();
            }
        });
    }

    @JsonCreator
    public CatalogJsonSimple(@JsonProperty("products")  ProductJson[] products) {
        super();
        this.products = products;
    }



    public ProductJson[] getProducts() {
        return products;
    }


    @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
    public static class ProductJson {

        private final String type;
        private final String name;
        private final String [] included;
        private final String [] available;
        private final List<PlanJson> plans;


        @JsonCreator
        public ProductJson(@JsonProperty("type") String type,
                @JsonProperty("name") String name,
                @JsonProperty("plans") List<PlanJson> plans,
                @JsonProperty("included") Collection<String> included,
                @JsonProperty("available") Collection<String> available) {
            super();
            this.type = type;
            this.name = name;
            this.included = included.toArray(new String[included.size()]);
            this.available = available.toArray(new String[available.size()]);
            this.plans = plans;
        }

        public ProductJson(String type, String name, Collection<String> included, Collection<String> available) {
            this(type, name, new LinkedList<CatalogJsonSimple.PlanJson>(), included, available);
        }

        public void addPlan(PlanJson plan) {
            plans.add(plan);
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

        public String[] getIncluded() {
            return included;
        }

        public String[] getAvailable() {
            return available;
        }
    }

    @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
    public static class PlanJson {

        private final String name;
        private final PhaseJson [] phases;

        @JsonCreator
        public PlanJson(@JsonProperty("name") String name,
                @JsonProperty("phases") PhaseJson[] phases) {
            super();
            this.name = name;
            this.phases = phases;
        }
        public String getName() {
            return name;
        }
        public PhaseJson[] getPhases() {
            return phases;
        }
    }

    @JsonAutoDetect(fieldVisibility=JsonAutoDetect.Visibility.ANY)
    public static class PhaseJson {

        private final String type;
        private final Map<String, BigDecimal> prices;

        @JsonCreator
        public PhaseJson(@JsonProperty("type") String type,
                @JsonProperty("prices") Map<String, BigDecimal> prices) {
            super();
            this.type = type;
            this.prices = prices;
        }
        public String getType() {
            return type;
        }
        public Map<String, BigDecimal> getPrices() {
            return prices;
        }
    }
}
