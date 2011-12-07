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

package com.ning.billing.catalog.glue;

import com.google.inject.AbstractModule;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.io.ICatalogLoader;
import com.ning.billing.catalog.io.VersionedCatalogLoader;
import com.ning.billing.config.CatalogConfig;
import org.skife.config.ConfigurationObjectFactory;

public class CatalogModule extends AbstractModule {

    protected void installConfig() {
        final CatalogConfig config = new ConfigurationObjectFactory(System.getProperties()).build(CatalogConfig.class);
        bind(CatalogConfig.class).toInstance(config);
    }

    protected void installCatalog() {
        bind(CatalogService.class).to(DefaultCatalogService.class).asEagerSingleton();
        bind(ICatalogLoader.class).to(VersionedCatalogLoader.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();
        installCatalog();
    }

}
