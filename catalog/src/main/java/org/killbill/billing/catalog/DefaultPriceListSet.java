/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.catalog;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPriceListSet extends ValidatingConfig<StandaloneCatalog> implements PriceListSet, Externalizable {

    @XmlElement(required = true, name = "defaultPriceList")
    private DefaultPriceList defaultPricelist;

    @XmlElement(required = false, name = "childPriceList")
    private DefaultPriceList[] childPriceLists;

    // Required for deserialization
    public DefaultPriceListSet() {
    }

    public DefaultPriceListSet(final DefaultPriceList defaultPricelist, final DefaultPriceList[] childPriceLists) {
        this.defaultPricelist = defaultPricelist;
        this.childPriceLists = childPriceLists != null ? childPriceLists : new DefaultPriceList[0];
    }

    public Plan getPlanFrom(final Product product, final BillingPeriod period, final String priceListName) throws CatalogApiException {

        Collection<Plan> plans = null;
        final DefaultPriceList pl = findPriceListFrom(priceListName);
        if (pl != null) {
            plans = pl.findPlans(product, period);
        }
        if (plans.size() == 0) {
            plans = defaultPricelist.findPlans(product, period);
        }
        switch (plans.size()) {
            case 0:
                return null;
            case 1:
                return plans.iterator().next();
            default:
                throw new CatalogApiException(ErrorCode.CAT_MULTIPLE_MATCHING_PLANS_FOR_PRICELIST,
                                              priceListName, product.getName(), period);
        }
    }

    public DefaultPriceList findPriceListFrom(final String priceListName) throws CatalogApiException {
        if (priceListName == null) {
            throw new CatalogApiException(ErrorCode.CAT_NULL_PRICE_LIST_NAME);
        }
        if (defaultPricelist.getName().equals(priceListName)) {
            return defaultPricelist;
        }
        for (final DefaultPriceList pl : childPriceLists) {
            if (pl.getName().equals(priceListName)) {
                return pl;
            }
        }
        throw new CatalogApiException(ErrorCode.CAT_PRICE_LIST_NOT_FOUND, priceListName);
    }

    @Override
    public StaticCatalog getCatalog() {
        return root;
    }


    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        defaultPricelist.validate(catalog, errors);
        //Check that the default pricelist name is not in use in the children
        for (final DefaultPriceList pl : childPriceLists) {
            if (pl.getName().equals(PriceListSet.DEFAULT_PRICELIST_NAME)) {
                errors.add(new ValidationError("Pricelists cannot use the reserved name '" + PriceListSet.DEFAULT_PRICELIST_NAME + "'",
                                               DefaultPriceListSet.class, pl.getName()));
            }
            pl.validate(catalog, errors); // and validate the individual pricelists
        }
        return errors;
    }

    @Override
    public void initialize(final StandaloneCatalog catalog) {
        super.initialize(catalog);
        defaultPricelist.initialize(catalog);
        if (childPriceLists != null) {
            for (DefaultPriceList cur : childPriceLists) {
                cur.initialize(catalog);
            }
        }
        CatalogSafetyInitializer.initializeNonRequiredNullFieldsWithDefaultValue(this);
    }

    public DefaultPriceList getDefaultPricelist() {
        return defaultPricelist;
    }

    public DefaultPriceList[] getChildPriceLists() {
        return childPriceLists;
    }

    @Override
    public List<PriceList> getAllPriceLists() {
        final List<PriceList> result = new ArrayList<PriceList>(childPriceLists.length + 1);
        result.add(getDefaultPricelist());
        for (final PriceList list : getChildPriceLists()) {
            result.add(list);
        }
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPriceListSet)) {
            return false;
        }

        final DefaultPriceListSet that = (DefaultPriceListSet) o;

        if (!Arrays.equals(childPriceLists, that.childPriceLists)) {
            return false;
        }
        if (defaultPricelist != null ? !defaultPricelist.equals(that.defaultPricelist) : that.defaultPricelist != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = defaultPricelist != null ? defaultPricelist.hashCode() : 0;
        result = 31 * result + (childPriceLists != null ? Arrays.hashCode(childPriceLists) : 0);
        return result;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeObject(defaultPricelist);
        out.writeObject(childPriceLists);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.defaultPricelist = (DefaultPriceList) in.readObject();
        this.childPriceLists = (DefaultPriceList[]) in.readObject();
    }
}
