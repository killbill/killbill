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

package org.killbill.billing.catalog;

import java.lang.reflect.Array;
import java.util.Date;

import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogEntity;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.MutableStaticCatalog;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhasePriceOverridesWithCallContext;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DefaultMutableStaticCatalog extends StandaloneCatalog implements MutableStaticCatalog {

    public DefaultMutableStaticCatalog() {
    }

    public DefaultMutableStaticCatalog(final Date effectiveDate) {
        super(effectiveDate);
    }

    public DefaultMutableStaticCatalog(final StandaloneCatalog input) {
        this.setCatalogName(input.getCatalogName())
            .setEffectiveDate(input.getEffectiveDate())
            .setSupportedCurrencies(input.getCurrentSupportedCurrencies())
            .setUnits(input.getCurrentUnits())
            .setProducts(input.getCurrentProducts())
            .setPlans(input.getCurrentPlans())
            .setRecurringBillingMode(input.getRecurringBillingMode())
            .setPlanRules(input.getPlanRules())
            .setPriceLists(input.getPriceLists());
        initialize(this, null);
    }


    @Override
    public void addCurrency(final Currency currency) throws CatalogApiException {
        final Currency [] newEntries = allocateNewEntries(getCurrentSupportedCurrencies(), currency);
        setSupportedCurrencies(newEntries);
    }

    @Override
    public void addProduct(final Product product) throws CatalogApiException {
        final Product[] newEntries = allocateNewEntries(getCurrentProducts(), product);
        setProducts((DefaultProduct[]) newEntries);
    }

    @Override
    public void addPlan(final Plan plan) throws CatalogApiException {
        final Plan[] newEntries = allocateNewEntries(getCurrentPlans(), plan);
        setPlans((DefaultPlan[]) newEntries);

        final DefaultPriceList priceList = getPriceLists().findPriceListFrom(plan.getPriceListName());
        priceList.setPlans((DefaultPlan[])newEntries);
    }

    @Override
    public void addPriceList(final PriceList priceList) throws CatalogApiException {
        final PriceList[] newEntries = allocateNewEntries(getPriceLists().getChildPriceLists(), priceList);
        final DefaultPriceListSet priceListSet = new DefaultPriceListSet((PriceListDefault) getPriceLists().getDefaultPricelist(), (DefaultPriceList[]) newEntries);
        setPriceLists(priceListSet);
    }


    public void addRecurringPriceToPlan(final DefaultInternationalPrice currentPrices, final Price newPrice) throws CatalogApiException {
        final Price [] newEntries = allocateNewEntries(currentPrices.getPrices(), newPrice);
        currentPrices.setPrices((DefaultPrice []) newEntries);
    }

    private <T> T [] allocateNewEntries(final T [] existingEntries, final T newEntry) throws CatalogApiException  {

        // Verify entry does not already exists
        if (Iterables.any(ImmutableList.<T>copyOf(existingEntries), new Predicate<T>() {
            @Override
            public boolean apply(final T input) {
                if (input instanceof CatalogEntity) {
                    return ((CatalogEntity) input).getName().equals(((CatalogEntity) newEntry).getName());
                } else if (input instanceof Enum) {
                    return ((Enum) input).name().equals(((Enum) newEntry).name());
                } else if (input instanceof Price) {
                    return ((Price) input).getCurrency().equals(((Price) newEntry).getCurrency());
                }
                throw new IllegalStateException("Unexpected type " + newEntry.getClass());
            }
        })) {
            //throw new CatalogApiException();
            throw new IllegalStateException("Already existing " + newEntry);
        }

        // Realloc and assign new entry
        final T [] newEntries = (T[]) Array.newInstance(newEntry.getClass(), existingEntries.length + 1);
        for (int i = 0 ; i < newEntries.length + 1; i++) {
            if (i < newEntries.length - 1) {
                newEntries[i] = existingEntries[i];
            } else {
                newEntries[newEntries.length - 1] = newEntry;
            }
        }
        return newEntries;
    }

}
