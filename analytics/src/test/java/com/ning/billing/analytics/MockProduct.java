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

package com.ning.billing.analytics;

import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.ProductCategory;

public class MockProduct implements IProduct
{
    private final String name;
    private final String type;
    private final ProductCategory category;

    public MockProduct(final String name, final String type, final ProductCategory category)
    {
        this.name = name;
        this.type = type;
        this.category = category;
    }

    @Override
    public String getCatalogName()
    {
        return type;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public ProductCategory getCategory()
    {
        return category;
    }

    @Override
    public IProduct[] getAvailable()
    {
        return null;
    }

    @Override
    public IProduct[] getIncluded()
    {
        return null;
    }
}
