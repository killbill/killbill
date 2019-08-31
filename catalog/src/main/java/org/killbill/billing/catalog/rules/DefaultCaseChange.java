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

package org.killbill.billing.catalog.rules;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URI;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.billing.catalog.CatalogSafetyInitializer;
import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.rules.CaseChange;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public abstract class DefaultCaseChange<T> extends ValidatingConfig<StandaloneCatalog> implements CaseChange, Externalizable {

    @XmlElement(required = false)
    protected PhaseType phaseType;

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
                       final PlanSpecifier to, final StaticCatalog catalog) throws CatalogApiException {

        final Product inFromProduct;
        final BillingPeriod inFromBillingPeriod;
        final ProductCategory inFromProductCategory;
        final PriceList inFromPriceList;
        if (from.getPlanName() != null) {
            final Plan plan = catalog.findPlan(from.getPlanName());
            inFromProduct = plan.getProduct();
            inFromBillingPeriod = plan.getRecurringBillingPeriod();
            inFromProductCategory = plan.getProduct().getCategory();
            inFromPriceList = plan.getPriceList();
        } else {
            inFromProduct = catalog.findProduct(from.getProductName());
            inFromBillingPeriod = from.getBillingPeriod();
            inFromProductCategory = inFromProduct.getCategory();
            inFromPriceList = from.getPriceListName() != null ? catalog.findPriceList(from.getPriceListName()) : null;
        }

        final Product inToProduct;
        final BillingPeriod inToBillingPeriod;
        final ProductCategory inToProductCategory;
        final PriceList inToPriceList;
        if (to.getPlanName() != null) {
            final Plan plan = catalog.findPlan(to.getPlanName());
            inToProduct = plan.getProduct();
            inToBillingPeriod = plan.getRecurringBillingPeriod();
            inToProductCategory = plan.getProduct().getCategory();
            inToPriceList =  plan.getPriceList();
        } else {
            inToProduct = catalog.findProduct(to.getProductName());
            inToBillingPeriod = to.getBillingPeriod();
            inToProductCategory = inToProduct.getCategory();
            inToPriceList = to.getPriceListName() != null ? catalog.findPriceList(to.getPriceListName()) : null;
        }

        if (
                (phaseType == null || from.getPhaseType() == phaseType) &&
                (fromProduct == null || fromProduct.equals(inFromProduct)) &&
                (fromProductCategory == null || fromProductCategory.equals(inFromProductCategory)) &&
                (fromBillingPeriod == null || fromBillingPeriod.equals(inFromBillingPeriod)) &&
                (this.toProduct == null || this.toProduct.equals(inToProduct)) &&
                (this.toProductCategory == null || this.toProductCategory.equals(inToProductCategory)) &&
                (this.toBillingPeriod == null || this.toBillingPeriod.equals(inToBillingPeriod)) &&
                (fromPriceList == null || fromPriceList.equals(inFromPriceList)) &&
                (toPriceList == null || toPriceList.equals(inToPriceList))
        ) {
            return getResult();
        }
        return null;
    }

    public static <K> K getResult(final DefaultCaseChange<K>[] cases, final PlanPhaseSpecifier from,
                                  final PlanSpecifier to, final StaticCatalog catalog) throws CatalogApiException {
        if (cases != null) {
            for (final DefaultCaseChange<K> cc : cases) {
                final K result = cc.getResult(from, to, catalog);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;

    }
    @Override
    public StaticCatalog getCatalog() {
        return root;
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

    public DefaultCaseChange<T> setPhaseType(final PhaseType phaseType) {
        this.phaseType = phaseType;
        return this;
    }

    public DefaultCaseChange<T> setFromProduct(final Product fromProduct) {
        this.fromProduct = (DefaultProduct) fromProduct;
        return this;
    }

    public DefaultCaseChange<T> setFromProductCategory(final ProductCategory fromProductCategory) {
        this.fromProductCategory = fromProductCategory;
        return this;
    }

    public DefaultCaseChange<T> setFromBillingPeriod(final BillingPeriod fromBillingPeriod) {
        this.fromBillingPeriod = fromBillingPeriod;
        return this;
    }

    public DefaultCaseChange<T> setFromPriceList(final DefaultPriceList fromPriceList) {
        this.fromPriceList = fromPriceList;
        return this;
    }

    public DefaultCaseChange<T> setToProduct(final Product toProduct) {
        this.toProduct = (DefaultProduct) toProduct;
        return this;
    }

    public DefaultCaseChange<T> setToProductCategory(final ProductCategory toProductCategory) {
        this.toProductCategory = toProductCategory;
        return this;
    }

    public DefaultCaseChange<T> setToBillingPeriod(final BillingPeriod toBillingPeriod) {
        this.toBillingPeriod = toBillingPeriod;
        return this;
    }

    public DefaultCaseChange<T> setToPriceList(final DefaultPriceList toPriceList) {
        this.toPriceList = toPriceList;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCaseChange)) {
            return false;
        }

        final DefaultCaseChange that = (DefaultCaseChange) o;

        if (fromBillingPeriod != that.fromBillingPeriod) {
            return false;
        }
        if (fromPriceList != null ? !fromPriceList.equals(that.fromPriceList) : that.fromPriceList != null) {
            return false;
        }
        if (fromProduct != null ? !fromProduct.equals(that.fromProduct) : that.fromProduct != null) {
            return false;
        }
        if (fromProductCategory != that.fromProductCategory) {
            return false;
        }
        if (phaseType != that.phaseType) {
            return false;
        }
        if (toBillingPeriod != that.toBillingPeriod) {
            return false;
        }
        if (toPriceList != null ? !toPriceList.equals(that.toPriceList) : that.toPriceList != null) {
            return false;
        }
        if (toProduct != null ? !toProduct.equals(that.toProduct) : that.toProduct != null) {
            return false;
        }
        if (toProductCategory != that.toProductCategory) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = phaseType != null ? phaseType.hashCode() : 0;
        result = 31 * result + (fromProduct != null ? fromProduct.hashCode() : 0);
        result = 31 * result + (fromProductCategory != null ? fromProductCategory.hashCode() : 0);
        result = 31 * result + (fromBillingPeriod != null ? fromBillingPeriod.hashCode() : 0);
        result = 31 * result + (fromPriceList != null ? fromPriceList.hashCode() : 0);
        result = 31 * result + (toProduct != null ? toProduct.hashCode() : 0);
        result = 31 * result + (toProductCategory != null ? toProductCategory.hashCode() : 0);
        result = 31 * result + (toBillingPeriod != null ? toBillingPeriod.hashCode() : 0);
        result = 31 * result + (toPriceList != null ? toPriceList.hashCode() : 0);
        return result;
    }

    @Override
    public PhaseType getPhaseType() {
        return phaseType;
    }

    @Override
    public Product getFromProduct() {
        return fromProduct;
    }

    @Override
    public ProductCategory getFromProductCategory() {
        return fromProductCategory;
    }

    @Override
    public BillingPeriod getFromBillingPeriod() {
        return fromBillingPeriod;
    }

    @Override
    public PriceList getFromPriceList() {
        return fromPriceList;
    }

    @Override
    public Product getToProduct() {
        return toProduct;
    }

    @Override
    public ProductCategory getToProductCategory() {
        return toProductCategory;
    }

    @Override
    public BillingPeriod getToBillingPeriod() {
        return toBillingPeriod;
    }

    @Override
    public PriceList getToPriceList() {
        return toPriceList;
    }

    @Override
    public String toString() {
        return "DefaultCaseChange{" +
               "phaseType=" + phaseType +
               ", fromProduct=" + fromProduct +
               ", fromProductCategory=" + fromProductCategory +
               ", fromBillingPeriod=" + fromBillingPeriod +
               ", fromPriceList=" + fromPriceList +
               ", toProduct=" + toProduct +
               ", toProductCategory=" + toProductCategory +
               ", toBillingPeriod=" + toBillingPeriod +
               ", toPriceList=" + toPriceList +
               '}';
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeBoolean(phaseType != null);
        if (phaseType != null) {
            out.writeUTF(phaseType.name());
        }
        out.writeObject(fromProduct);
        out.writeBoolean(fromProductCategory != null);
        if (fromProductCategory != null) {
            out.writeUTF(fromProductCategory.name());
        }
        out.writeBoolean(fromBillingPeriod != null);
        if (fromBillingPeriod != null) {
            out.writeUTF(fromBillingPeriod.name());
        }
        out.writeObject(fromPriceList);
        out.writeObject(toProduct);
        out.writeBoolean(toProductCategory != null);
        if (toProductCategory != null) {
            out.writeUTF(toProductCategory.name());
        }
        out.writeBoolean(toBillingPeriod != null);
        if (toBillingPeriod != null) {
            out.writeUTF(toBillingPeriod.name());
        }
        out.writeObject(toPriceList);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.phaseType = in.readBoolean() ? PhaseType.valueOf(in.readUTF()) : null;
        this.fromProduct = (DefaultProduct) in.readObject();
        this.fromProductCategory = in.readBoolean() ? ProductCategory.valueOf(in.readUTF()) : null;
        this.fromBillingPeriod = in.readBoolean() ? BillingPeriod.valueOf(in.readUTF()) : null;
        this.fromPriceList = (DefaultPriceList) in.readObject();
        this.toProduct = (DefaultProduct) in.readObject();
        this.toProductCategory = in.readBoolean() ? ProductCategory.valueOf(in.readUTF()) : null;
        this.toBillingPeriod = in.readBoolean() ? BillingPeriod.valueOf(in.readUTF()) : null;
        this.toPriceList = (DefaultPriceList) in.readObject();
    }
}
