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

package com.ning.billing.catalog.api;


/**
 * The interface {@code Product}
 */
public interface Product {

    /**
     * 
     * @return the name of the {@code Product}
     */
    public String getName();

    /**
     * 
     * @return whether this {@code Product} has been retired
     */
    public boolean isRetired();

    /**
     * 
     * @return an array of other {@code Product} that can be purchased with that one
     */
    public Product[] getAvailable();

    /**
     * 
     * @return an array of other {@code Product} that are already included within this one
     */
    public Product[] getIncluded();

    /**
     * 
     * @return the {@code ProductCategory} associated with that {@code Product}
     */
    public ProductCategory getCategory();

    /**
     * 
     * @return the name of the catalog where this {@code Product} has been defined
     */
    public String getCatalogName();

    /**
     * 
     * @return the limits associated with this product
     */
    public Limit[] getLimits();

    /**
     * 
     * @return whether the given unit-value pair meets the limits of the product
     */
    public boolean compliesWithLimits(String unit, double value);


}
