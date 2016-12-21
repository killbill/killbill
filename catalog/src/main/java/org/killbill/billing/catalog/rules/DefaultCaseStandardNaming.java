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
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.rules.Case;

public abstract class DefaultCaseStandardNaming<T> extends DefaultCase<T> implements Case {
    @XmlElement(required = false, name = "product")
    @XmlIDREF
    private DefaultProduct product;
    @XmlElement(required = false, name = "productCategory")
    private ProductCategory productCategory;

    @XmlElement(required = false, name = "billingPeriod")
    private BillingPeriod billingPeriod;

    @XmlElement(required = false, name = "priceList")
    @XmlIDREF
    private DefaultPriceList priceList;

    @Override
    public DefaultProduct getProduct() {
        return product;
    }

    @Override
    public ProductCategory getProductCategory() {
        return productCategory;
    }

    @Override
    public BillingPeriod getBillingPeriod() {
        return billingPeriod;
    }

    @Override
    public DefaultPriceList getPriceList() {
        return priceList;
    }

    public DefaultCaseStandardNaming<T> setProduct(final Product product) {
        this.product = (DefaultProduct) product;
        return this;
    }

    public DefaultCaseStandardNaming<T> setProductCategory(final ProductCategory productCategory) {
        this.productCategory = productCategory;
        return this;
    }

    public DefaultCaseStandardNaming<T> setBillingPeriod(final BillingPeriod billingPeriod) {
        this.billingPeriod = billingPeriod;
        return this;
    }

    public DefaultCaseStandardNaming<T> setPriceList(final DefaultPriceList priceList) {
        this.priceList = priceList;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultCaseStandardNaming)) {
            return false;
        }

        final DefaultCaseStandardNaming that = (DefaultCaseStandardNaming) o;

        if (billingPeriod != that.billingPeriod) {
            return false;
        }
        if (priceList != null ? !priceList.equals(that.priceList) : that.priceList != null) {
            return false;
        }
        if (product != null ? !product.equals(that.product) : that.product != null) {
            return false;
        }
        if (productCategory != that.productCategory) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = product != null ? product.hashCode() : 0;
        result = 31 * result + (productCategory != null ? productCategory.hashCode() : 0);
        result = 31 * result + (billingPeriod != null ? billingPeriod.hashCode() : 0);
        result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
        return result;
    }

}
