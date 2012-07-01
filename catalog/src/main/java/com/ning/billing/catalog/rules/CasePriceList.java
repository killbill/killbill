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

package com.ning.billing.catalog.rules;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import com.ning.billing.catalog.DefaultPriceList;
import com.ning.billing.catalog.DefaultProduct;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;

public class CasePriceList extends Case<DefaultPriceList> {
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

    public DefaultProduct getProduct() {
        return fromProduct;
    }

    public ProductCategory getProductCategory() {
        return fromProductCategory;
    }

    public BillingPeriod getBillingPeriod() {
        return fromBillingPeriod;
    }

    public DefaultPriceList getPriceList() {
        return fromPriceList;
    }

    protected DefaultPriceList getResult() {
        return toPriceList;
    }

    protected CasePriceList setProduct(final DefaultProduct product) {
        this.fromProduct = product;
        return this;
    }

    protected CasePriceList setProductCategory(final ProductCategory productCategory) {
        this.fromProductCategory = productCategory;
        return this;
    }

    protected CasePriceList setBillingPeriod(final BillingPeriod billingPeriod) {
        this.fromBillingPeriod = billingPeriod;
        return this;
    }

    protected CasePriceList setPriceList(final DefaultPriceList priceList) {
        this.fromPriceList = priceList;
        return this;
    }


    protected CasePriceList setToPriceList(final DefaultPriceList toPriceList) {
        this.toPriceList = toPriceList;
        return this;
    }


}
