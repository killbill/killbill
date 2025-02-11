/*
 * Copyright 2010-2012 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.glue;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.api.ExportUserApi;
import org.killbill.billing.util.config.definition.ExportConfig;
import org.killbill.billing.util.export.api.DefaultExportUserApi;
import org.skife.config.ConfigurationObjectFactory;

public class ExportModule extends KillBillModule {

    public ExportModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installUserApi() {
        bind(ExportUserApi.class).to(DefaultExportUserApi.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installUserApi();
        installConfig();
    }

    protected void installConfig() {
        final ExportConfig config = new ConfigurationObjectFactory(skifeConfigSource).build(ExportConfig.class);
        bind(ExportConfig.class).toInstance(config);
    }

}
