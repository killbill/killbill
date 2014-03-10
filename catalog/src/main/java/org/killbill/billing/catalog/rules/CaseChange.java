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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.util.config.catalog.ValidatingConfig;
import org.killbill.billing.util.config.catalog.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public abstract class CaseChange<T> extends ValidatingConfig<StandaloneCatalog> {

    @XmlElement(required = false)
    private PhaseType phaseType;

    @XmlElement(required = false)
    @XmlIDREF
    private DefaultProduct fromProduct;

    @XmlElement(required = false)
    private ProductCategory fromProductCategory;

    @XmlElement(required = false)
    private BillingPeriod fromBillingPeriod;

    @XmlElement(required = false)
    @XmlIDREF
    private DefaultPriceList fromPriceList;

    @XmlElement(required = false)
    @XmlIDREF
    private DefaultProduct toProduct;

    @XmlElement(required = false)
    private ProductCategory toProductCategory;

    @XmlElement(required = false)
    private BillingPeriod toBillingPeriod;

    @XmlElement(required = false)
    @XmlIDREF
    private DefaultPriceList toPriceList;

    protected abstract T getResult();

    public T getResult(final PlanPhaseSpecifier from,
                       final PlanSpecifier to, final StandaloneCatalog catalog) throws CatalogApiException {
        if (
                (phaseType == null || from.getPhaseType() == phaseType) &&
                        (fromProduct == null || fromProduct.equals(catalog.findCurrentProduct(from.getProductName()))) &&
                        (fromProductCategory == null || fromProductCategory.equals(from.getProductCategory())) &&
                        (fromBillingPeriod == null || fromBillingPeriod.equals(from.getBillingPeriod())) &&
                        (toProduct == null || toProduct.equals(catalog.findCurrentProduct(to.getProductName()))) &&
                        (toProductCategory == null || toProductCategory.equals(to.getProductCategory())) &&
                        (toBillingPeriod == null || toBillingPeriod.equals(to.getBillingPeriod())) &&
                        (fromPriceList == null || fromPriceList.equals(catalog.findCurrentPriceList(from.getPriceListName()))) &&
                        (toPriceList == null || toPriceList.equals(catalog.findCurrentPriceList(to.getPriceListName())))
                ) {
            return getResult();
        }
        return null;
    }

    public static <K> K getResult(final CaseChange<K>[] cases, final PlanPhaseSpecifier from,
                                  final PlanSpecifier to, final StandaloneCatalog catalog) throws CatalogApiException {
        if (cases != null) {
            for (final CaseChange<K> cc : cases) {
                final K result = cc.getResult(from, to, catalog);
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

    protected CaseChange<T> setPhaseType(final PhaseType phaseType) {
        this.phaseType = phaseType;
        return this;
    }

    protected CaseChange<T> setFromProduct(final DefaultProduct fromProduct) {
        this.fromProduct = fromProduct;
        return this;
    }

    protected CaseChange<T> setFromProductCategory(final ProductCategory fromProductCategory) {
        this.fromProductCategory = fromProductCategory;
        return this;
    }

    protected CaseChange<T> setFromBillingPeriod(final BillingPeriod fromBillingPeriod) {
        this.fromBillingPeriod = fromBillingPeriod;
        return this;
    }

    protected CaseChange<T> setFromPriceList(final DefaultPriceList fromPriceList) {
        this.fromPriceList = fromPriceList;
        return this;
    }

    protected CaseChange<T> setToProduct(final DefaultProduct toProduct) {
        this.toProduct = toProduct;
        return this;
    }

    protected CaseChange<T> setToProductCategory(final ProductCategory toProductCategory) {
        this.toProductCategory = toProductCategory;
        return this;
    }

    protected CaseChange<T> setToBillingPeriod(final BillingPeriod toBillingPeriod) {
        this.toBillingPeriod = toBillingPeriod;
        return this;
    }

    protected CaseChange<T> setToPriceList(final DefaultPriceList toPriceList) {
        this.toPriceList = toPriceList;
        return this;
    }

}
