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

import com.ning.billing.catalog.PriceList;
import com.ning.billing.catalog.Product;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.ProductCategory;

public class CasePriceList extends Case<PriceList> {

	@XmlElement(required=true)
	private PriceList toPriceList;

	@Override
	protected PriceList getResult() {
		return toPriceList;
	}

	@XmlElement(required=false, name="fromProduct")
	@XmlIDREF
	public Product getProduct(){
		return product;
	}

	@XmlElement(required=false, name="fromProductCategory")
	public ProductCategory getProductCategory() {
		return productCategory;
	}

	@XmlElement(required=false, name="fromBillingPeriod")
	public BillingPeriod getBillingPeriod() {
		return billingPeriod;
	}
	
	@XmlElement(required=false, name="fromPriceList")
	@XmlIDREF
	public PriceList getPriceList() {
		return priceList;
	}
}
