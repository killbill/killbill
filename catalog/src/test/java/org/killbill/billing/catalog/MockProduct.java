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

package org.killbill.billing.catalog;

import java.util.Collection;

import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;

import com.google.common.collect.ImmutableList;

public class MockProduct extends DefaultProduct {

    public MockProduct() {
        setName("TestProduct");
        setCatagory(ProductCategory.BASE);
        setCatalogName("Vehcles");
    }

    public MockProduct(final String name, final ProductCategory category, final String catalogName) {
        setName(name);
        setCatagory(category);
        setCatalogName(catalogName);
    }

    public static MockProduct createBicycle() {
        return new MockProduct("1-Bicycle", ProductCategory.BASE, "Vehcles");
    }

    public static MockProduct createPickup() {
        return new MockProduct("2-Pickup", ProductCategory.BASE, "Vehcles");
    }

    public static MockProduct createSportsCar() {
        return new MockProduct("3-SportsCar", ProductCategory.BASE, "Vehcles");
    }

    public static MockProduct createJet() {
        return new MockProduct("4-Jet", ProductCategory.BASE, "Vehcles");
    }

    public static MockProduct createHorn() {
        return new MockProduct("5-Horn", ProductCategory.ADD_ON, "Vehcles");
    }

    public static MockProduct createSpotlight() {
        return new MockProduct("spotlight", ProductCategory.ADD_ON, "Vehcles");
    }

    public static MockProduct createRedPaintJob() {
        return new MockProduct("6-RedPaintJob", ProductCategory.ADD_ON, "Vehcles");
    }

    public static Collection<Product> createAll() {
        return ImmutableList.<Product>of(
                createBicycle(),
                createPickup(),
                createSportsCar(),
                createJet(),
                createHorn(),
                createRedPaintJob());
    }


}
