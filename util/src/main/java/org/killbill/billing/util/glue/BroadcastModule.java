/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import org.killbill.billing.broadcast.BroadcastApi;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.broadcast.BroadcastService;
import org.killbill.billing.util.broadcast.DefaultBroadcastApi;
import org.killbill.billing.util.broadcast.DefaultBroadcastService;
import org.killbill.billing.util.broadcast.dao.BroadcastDao;
import org.killbill.billing.util.broadcast.dao.DefaultBroadcastDao;
import org.killbill.billing.util.config.definition.BroadcastConfig;
import org.skife.config.AugmentedConfigurationObjectFactory;

public class BroadcastModule extends KillBillModule {

    public BroadcastModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installDaos() {
        bind(BroadcastDao.class).to(DefaultBroadcastDao.class).asEagerSingleton();
    }

    protected void installUserApi() {

        bind(BroadcastService.class).to(DefaultBroadcastService.class).asEagerSingleton();
        bind(BroadcastApi.class).to(DefaultBroadcastApi.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        final AugmentedConfigurationObjectFactory factory = new AugmentedConfigurationObjectFactory(skifeConfigSource);
        final BroadcastConfig broadcastConfig = factory.build(BroadcastConfig.class);
        bind(BroadcastConfig.class).toInstance(broadcastConfig);
        installDaos();
        installUserApi();
    }
}
