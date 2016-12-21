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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.rules.CasePriceList;

public class DefaultCasePriceList extends DefaultCaseStandardNaming<DefaultPriceList> implements CasePriceList {
    @XmlElement(required = false, name = "fromProduct")
    @XmlIDREF
    private DefaultProduct fromProduct;

    @XmlElement(required = false, name = "fromProductCategory")
    private ProductCategory fromProductCategory;

    @XmlElement(required = false, name = "fromBillingPeriod")
    private BillingPeriod fromBillingPeriod;

    @XmlElement(required = false, name = "fromPriceList")
    @XmlIDREF
    private DefaultPriceList fromPriceList;

    @XmlElement(required = true, name = "toPriceList")
    @XmlIDREF
    private DefaultPriceList toPriceList;

    @Override
    public DefaultProduct getProduct() {
        return fromProduct;
    }

    @Override
    public ProductCategory getProductCategory() {
        return fromProductCategory;
    }

    @Override
    public BillingPeriod getBillingPeriod() {
        return fromBillingPeriod;
    }

    @Override
    public DefaultPriceList getPriceList() {
        return fromPriceList;
    }

    @Override
    public PriceList getDestinationPriceList() {
        return toPriceList;
    }

    protected DefaultPriceList getResult() {
        return toPriceList;
    }

    public DefaultCasePriceList setProduct(final DefaultProduct product) {
        this.fromProduct = product;
        return this;
    }

    public DefaultCasePriceList setProductCategory(final ProductCategory productCategory) {
        this.fromProductCategory = productCategory;
        return this;
    }

    public DefaultCasePriceList setBillingPeriod(final BillingPeriod billingPeriod) {
        this.fromBillingPeriod = billingPeriod;
        return this;
    }

    public DefaultCasePriceList setPriceList(final DefaultPriceList priceList) {
        this.fromPriceList = priceList;
        return this;
    }


    public DefaultCasePriceList setToPriceList(final DefaultPriceList toPriceList) {
        this.toPriceList = toPriceList;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCasePriceList)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultCasePriceList that = (DefaultCasePriceList) o;

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
        if (toPriceList != null ? !toPriceList.equals(that.toPriceList) : that.toPriceList != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (fromProduct != null ? fromProduct.hashCode() : 0);
        result = 31 * result + (fromProductCategory != null ? fromProductCategory.hashCode() : 0);
        result = 31 * result + (fromBillingPeriod != null ? fromBillingPeriod.hashCode() : 0);
        result = 31 * result + (fromPriceList != null ? fromPriceList.hashCode() : 0);
        result = 31 * result + (toPriceList != null ? toPriceList.hashCode() : 0);
        return result;
    }


    @Override
    public String toString() {
        return "DefaultCasePriceList {" +
               "fromProduct=" + fromProduct +
               ", fromProductCategory=" + fromProductCategory +
               ", fromBillingPeriod=" + fromBillingPeriod +
               ", fromPriceList=" + fromPriceList +
               ", toPriceList=" + toPriceList +
               '}';
    }
}
