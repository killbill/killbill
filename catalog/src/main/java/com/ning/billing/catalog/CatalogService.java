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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.ICatalogService;
import com.ning.billing.config.ICatalogConfig;
import com.ning.billing.lifecycle.IService;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.util.config.XMLLoader;

public class CatalogService implements IService, Provider<ICatalog>, ICatalogService {

    private static final String CATALOG_SERVICE_NAME = "catalog-service";

    private static ICatalog catalog;

    private final ICatalogConfig config;
    private boolean isInitialized;


    @Inject
    public CatalogService(ICatalogConfig config) {
        this.config = config;
        System.out.println(config.getCatalogURI());
        this.isInitialized = false;
    }

    @LifecycleHandlerType(LifecycleLevel.LOAD_CATALOG)
    public synchronized void loadCatalog() throws ServiceException {
        if (!isInitialized) {
            try {
                catalog = XMLLoader.getObjectFromProperty(config.getCatalogURI(), Catalog.class);
                isInitialized = true;
            } catch (Exception e) {
                throw new ServiceException(e);
            }
        }
    }


    @Override
    public String getName() {
        return CATALOG_SERVICE_NAME;
    }



    /* (non-Javadoc)
     * @see com.ning.billing.catalog.ICatlogService#getCatalog()
     */
    @Override
    public ICatalog getCatalog() {
        return catalog;
    }


    // Should be able to use bind(ICatalog.class).toProvider(CatalogService.class);
    @Override
    public ICatalog get() {
        return catalog;
    }

}
