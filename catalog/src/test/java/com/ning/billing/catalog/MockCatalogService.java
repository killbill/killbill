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

import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.StaticCatalog;


public class MockCatalogService extends DefaultCatalogService {

    private MockCatalog catalog;

    public MockCatalogService(MockCatalog catalog) {
        super(null, null);
        this.catalog = catalog;
    }

    @Override
    public synchronized void loadCatalog() throws ServiceException {
    }

    @Override
    public String getName() {
        return "Mock Catalog";
    }

    @Override
    public Catalog getFullCatalog() {
        return catalog;
    }

    @Override
    public Catalog get() {
         return catalog;
    }

    @Override
    public StaticCatalog getCurrentCatalog() {
        return catalog;
    }

    
}
