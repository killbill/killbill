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

package com.ning.billing.catalog;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlIDREF;

import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.IProductType;
import com.ning.billing.catalog.api.ProductCategory;

@XmlAccessorType(XmlAccessType.NONE)
public class Product extends ValidatingConfig implements IProduct {

	@XmlAttribute (required=true)
	@XmlID
    private String name;

	@XmlIDREF @XmlElement(required=true)
    private ProductType type;

	@XmlElement(required=true)
    private ProductCategory category;
	
	@XmlElementWrapper(name="included", required=false)
	@XmlIDREF @XmlElement(name="addonProduct", required=true)
    private Product[] included;
	
	@XmlElementWrapper(name="available", required=false)
	@XmlIDREF @XmlElement(name="addonProduct", required=true)
    private Product[] available;
	
	@Override
    public ProductCategory getCategory() {
		return category;
	}

    @Override
	public Product[] getIncluded() {
		return included;
	}

    @Override
	public Product[] getAvailable() {
		return available;
	}

	public Product() {
    }

    protected Product(IProductType type, String name) {
    	this.name = name;
    }

    @Override
	public ProductType getType() {
        return type;
    }

    @Override
	public String getName() {
        return name;
    }

	public void setType(ProductType type) {
		this.type = type;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setCatagory(ProductCategory category) {
		this.category = category;
	}

	@Override
	public ValidationErrors validate(Catalog catalog, ValidationErrors errors) {
		return errors;
	}

	public boolean isIncluded(Product addon) {
		for(Product p : included) {
			if (addon == p) {
				return true;
			}
		}
		return false;
	}

	public boolean isAvailable(Product addon) {
		for(Product p : included) {
			if (addon == p) {
				return true;
			}
		}
		return false;
	}

	//TODO: MDW validation: inclusion and exclusion lists can only contain addon products
	//TODO: MDW validation: a given product can only be in, at most, one of inclusion and exclusion lists
}
