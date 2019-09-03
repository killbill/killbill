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

import org.killbill.billing.catalog.api.CatalogEntity;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.MutableStaticCatalog;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.Price;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.rules.DefaultPlanRules;

public class DefaultMutableStaticCatalog extends StandaloneCatalog implements MutableStaticCatalog {

    public DefaultMutableStaticCatalog() {
    }

    public DefaultMutableStaticCatalog(final Date effectiveDate) {
        super(effectiveDate);
    }

    public DefaultMutableStaticCatalog(final StandaloneCatalog input) {
        this.setCatalogName(input.getCatalogName())
            .setRecurringBillingMode(input.getRecurringBillingMode())
            .setEffectiveDate(input.getEffectiveDate())
            .setSupportedCurrencies(input.getSupportedCurrencies())
            .setUnits((DefaultUnit[]) input.getUnits())
            .setProducts(input.getProducts())
            .setPlans(input.getPlans())
            .setPlanRules((DefaultPlanRules) input.getPlanRules())
            .setPriceLists(input.getPriceLists());
        initialize(this);
    }

    @Override
    public void addCurrency(final Currency currency) {
        final Currency[] newEntries = allocateNewEntries(getSupportedCurrencies(), currency);
        setSupportedCurrencies(newEntries);
    }

    @Override
    public void addProduct(final Product product) {
        getCatalogEntityCollectionProduct().add(product);
    }

    @Override
    public void addPlan(final Plan plan) {

        getPlansMap().add(plan);

        final DefaultPriceList priceList = (DefaultPriceList) plan.getPriceList();
        priceList.getCatalogEntityCollectionPlan().add(plan);
    }

    @Override
    public void addPriceList(final PriceList priceList) {
        final PriceList[] newEntries = allocateNewEntries(getPriceLists().getChildPriceLists(), priceList);
        final DefaultPriceListSet priceListSet = new DefaultPriceListSet(getPriceLists().getDefaultPricelist(), (DefaultPriceList[]) newEntries);
        setPriceLists(priceListSet);
    }

    public void addRecurringPriceToPlan(final DefaultInternationalPrice currentPrices, final Price newPrice) {
        final Price[] newEntries = allocateNewEntries(currentPrices.getPrices(), newPrice);
        currentPrices.setPrices((DefaultPrice[]) newEntries);
    }

    public void addProductAvailableAO(final Product targetBasePlan, final DefaultProduct aoProduct) {
        ((DefaultProduct) targetBasePlan).getCatalogEntityCollectionAvailable().add(aoProduct);
    }

    private <T> T[] allocateNewEntries(final T[] existingEntries, final T newEntry) {

        if (existingEntries != null) {
            for (T input : existingEntries) {
                boolean found;
                if (input instanceof CatalogEntity) {
                    found = ((CatalogEntity) input).getName().equals(((CatalogEntity) newEntry).getName());
                } else if (input instanceof Enum) {
                    found = ((Enum) input).name().equals(((Enum) newEntry).name());
                } else if (input instanceof Price) {
                    found = ((Price) input).getCurrency().equals(((Price) newEntry).getCurrency());
                } else {
                    throw new IllegalStateException("Unexpected type " + newEntry.getClass());
                }
                if (found) {
                    //throw new CatalogApiException();
                    throw new IllegalStateException("Already existing " + newEntry);
                }
            }
        }

        // Realloc and assign new entry
        final int length = existingEntries != null ? existingEntries.length : 0;
        final T[] newEntries = (T[]) Array.newInstance(newEntry.getClass(), length + 1);
        for (int i = 0; i < newEntries.length + 1; i++) {
            if (i < newEntries.length - 1) {
                newEntries[i] = existingEntries[i];
            } else {
                newEntries[newEntries.length - 1] = newEntry;
            }
        }
        return newEntries;
    }

}
