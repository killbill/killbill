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

package org.killbill.billing.mock;

import java.util.Collection;

import org.killbill.billing.catalog.api.Limit;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;

import com.google.common.collect.ImmutableList;

public class MockProduct implements Product {

    private final String name;
    private final ProductCategory category;
    private final String catalogName;
    private final Collection<Product> included;
    private final Collection<Product> available;

    public MockProduct() {
        this("TestProduct", ProductCategory.BASE, "Vehicules");
    }

    public MockProduct(final String name, final ProductCategory category, final String catalogName) {
        this(name, category, catalogName, null, null);
    }

    public MockProduct(final String name, final ProductCategory category, final String catalogName, final Product[] included, final Product[] available) {
        this.name = name;
        this.category = category;
        this.catalogName = catalogName;
        this.included = ImmutableList.copyOf(included);
        this.available = ImmutableList.copyOf(available);
    }

    @Override
    public String getCatalogName() {
        return catalogName;
    }

    @Override
    public ProductCategory getCategory() {
        return category;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPrettyName() {
        return name;
    }

    @Override
    public Collection<Product> getAvailable() {
        return available;
    }

    @Override
    public Collection<Product> getIncluded() {
        return included;
    }

    public static MockProduct createBicycle() {
        return new MockProduct("Bicycle", ProductCategory.BASE, "Vehcles");
    }

    public static MockProduct createPickup() {
        return new MockProduct("Pickup", ProductCategory.BASE, "Vehcles");
    }

    public static MockProduct createSportsCar() {
        return new MockProduct("SportsCar", ProductCategory.BASE, "Vehcles");
    }

    public static MockProduct createJet() {
        return new MockProduct("Jet", ProductCategory.BASE, "Vehcles");
    }

    public static MockProduct createHorn() {
        return new MockProduct("Horn", ProductCategory.ADD_ON, "Vehcles");
    }

    public static MockProduct createSpotlight() {
        return new MockProduct("spotlight", ProductCategory.ADD_ON, "Vehcles");
    }

    public static MockProduct createRedPaintJob() {
        return new MockProduct("RedPaintJob", ProductCategory.ADD_ON, "Vehcles");
    }

    public static Product[] createAll() {
        return new MockProduct[]{
                createBicycle(),
                createPickup(),
                createSportsCar(),
                createJet(),
                createHorn(),
                createRedPaintJob()
        };
    }

    @Override
    public Limit[] getLimits() {
        return new Limit[0];
    }

    @Override
    public boolean compliesWithLimits(String unit, double value) {
        return false;
    }
}
