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

import java.io.File;
import java.net.URI;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.ICatalogService;
import com.ning.billing.catalog.io.CatalogLoader;
import com.ning.billing.config.ICatalogConfig;
import com.ning.billing.lifecycle.IService;


public class CatalogService implements IService, Provider<ICatalog>, ICatalogService {


    private static final String CATALOG_SERVICE_NAME = "catalog-service";

    private final ICatalogConfig config;

    private static ICatalog catalog;


    @Inject
    public CatalogService(ICatalogConfig config) {
        this.config = config;
        System.out.println(config.getCatalogURI());
    }

    @Override
    public void initialize() throws ServiceException {
        try {
            catalog = CatalogLoader.getCatalogFromProperty(config.getCatalogURI());
        } catch (Exception e) {
            throw new ServiceException(e);
        }
    }

    @Override
    public String getName() {
        return CATALOG_SERVICE_NAME;
    }


    @Override
    public void start() throws ServiceException {
        // Intentionally blank

    }

    @Override
    public void stop() throws ServiceException {
        // Intentionally blank

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
