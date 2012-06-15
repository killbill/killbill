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

import com.ning.billing.catalog.api.ProductCategory;

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

    public static DefaultProduct[] createAll() {
        return new MockProduct[]{
                createBicycle(),
                createPickup(),
                createSportsCar(),
                createJet(),
                createHorn(),
                createRedPaintJob()
        };
    }


}
