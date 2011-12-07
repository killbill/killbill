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
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.io.VersionedCatalogLoader;
import com.ning.billing.config.CatalogConfig;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;

public class DefaultCatalogService implements KillbillService, Provider<Catalog>, CatalogService {

    private static final String CATALOG_SERVICE_NAME = "catalog-service";

    private static Catalog catalog;

    private final CatalogConfig config;
    private boolean isInitialized;

	private VersionedCatalogLoader loader;


    @Inject
    public DefaultCatalogService(CatalogConfig config, VersionedCatalogLoader loader) {
        this.config = config;
        System.out.println(config.getCatalogURI());
        this.isInitialized = false;
        this.loader = loader;
    }

    @LifecycleHandlerType(LifecycleLevel.LOAD_CATALOG)
    public synchronized void loadCatalog() throws ServiceException {
        if (!isInitialized) {
            try {
            	System.out.println("Really really::" + config.getCatalogURI());
            	String url = config.getCatalogURI();
            	catalog = loader.load(url);
            	
                //catalog = XMLLoader.getObjectFromProperty(config.getCatalogURI(), Catalog.class);
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
    public Catalog getCatalog() {
        return catalog;
    }


    // Should be able to use bind(ICatalog.class).toProvider(CatalogService.class);
    @Override
    public Catalog get() {
        return catalog;
    }

}
