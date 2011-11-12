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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import com.ning.billing.catalog.Product;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.IProductTier;

@XmlAccessorType(XmlAccessType.NONE)
public class ProductTier implements IProductTier {

	@XmlElement(name="product",required=true)
	@XmlIDREF
	private Product[] products;
	
	public ProductTier(){}

	protected ProductTier(Product[] products) {
		this.products = products;
	}
	/* (non-Javadoc)
	 * @see com.ning.billing.catalog.IPlanTierSet#getProducts()
	 */
	@Override
	public IProduct[] getProducts() {
		return products;
	}
	public void setProducts(Product[] products) {
		this.products = products;
	}
}
