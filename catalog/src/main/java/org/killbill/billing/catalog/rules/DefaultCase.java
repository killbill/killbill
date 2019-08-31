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

package org.killbill.billing.catalog.rules;


import org.killbill.billing.catalog.CatalogSafetyInitializer;
import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

public abstract class DefaultCase<T> extends ValidatingConfig<StandaloneCatalog> {

    protected abstract T getResult();

    public abstract DefaultProduct getProduct();

    public abstract ProductCategory getProductCategory();

    public abstract BillingPeriod getBillingPeriod();

    public abstract DefaultPriceList getPriceList();

    public T getResult(final PlanSpecifier planPhase, final StaticCatalog c) throws CatalogApiException {
        if (satisfiesCase(planPhase, c)) {
            return getResult();
        }
        return null;
    }

    protected boolean satisfiesCase(final PlanSpecifier planPhase, final StaticCatalog c) throws CatalogApiException {
        final Product product;
        final BillingPeriod billingPeriod;
        final ProductCategory productCategory;
        final PriceList priceList;
        if (planPhase.getPlanName() != null) {
            final Plan plan = c.findPlan(planPhase.getPlanName());
            product = plan.getProduct();
            billingPeriod = plan.getRecurringBillingPeriod();
            productCategory = plan.getProduct().getCategory();
            priceList =  plan.getPriceList();
        } else {
            product = c.findProduct(planPhase.getProductName());
            billingPeriod = planPhase.getBillingPeriod();
            productCategory = product.getCategory();
            priceList = getPriceList() != null ? c.findPriceList(planPhase.getPriceListName()) : null;
        }
        return (getProduct() == null || getProduct().equals(product)) &&
               (getProductCategory() == null || getProductCategory().equals(productCategory)) &&
               (getBillingPeriod() == null || getBillingPeriod().equals(billingPeriod)) &&
               (getPriceList() == null || getPriceList().equals(priceList));
    }

    public static <K> K getResult(final DefaultCase<K>[] cases, final PlanSpecifier planSpec, final StaticCatalog catalog) throws CatalogApiException {
        if (cases != null) {
            for (final DefaultCase<K> c : cases) {
                final K result = c.getResult(planSpec, catalog);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;

    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }

    protected abstract DefaultCase<T> setProduct(Product product);

    protected abstract DefaultCase<T> setProductCategory(ProductCategory productCategory);

    protected abstract DefaultCase<T> setBillingPeriod(BillingPeriod billingPeriod);

    protected abstract DefaultCase<T> setPriceList(DefaultPriceList priceList);
}
