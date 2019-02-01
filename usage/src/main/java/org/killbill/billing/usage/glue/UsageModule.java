/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.usage.glue;

import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.usage.InternalUserApi;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.usage.api.svcs.DefaultInternalUserApi;
import org.killbill.billing.usage.api.user.DefaultUsageUserApi;
import org.killbill.billing.usage.dao.DefaultRolledUpUsageDao;
import org.killbill.billing.usage.dao.RolledUpUsageDao;
import org.killbill.billing.usage.plugin.api.UsagePluginApi;
import org.killbill.billing.util.glue.KillBillModule;

import com.google.inject.TypeLiteral;

public class UsageModule extends KillBillModule {

    public UsageModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installRolledUpUsageDao() {
        bind(RolledUpUsageDao.class).to(DefaultRolledUpUsageDao.class).asEagerSingleton();
    }

    protected void installUsageUserApi() {
        bind(UsageUserApi.class).to(DefaultUsageUserApi.class).asEagerSingleton();
    }

    protected void installInternalUserApi() {
        bind(InternalUserApi.class).to(DefaultInternalUserApi.class).asEagerSingleton();
    }


    protected void installUsagePluginApi() {
        bind(new TypeLiteral<OSGIServiceRegistration<UsagePluginApi>>() {}).toProvider(DefaultUsageProviderPluginRegistryProvider.class).asEagerSingleton();
    }


    @Override
    protected void configure() {
        installRolledUpUsageDao();
        installUsageUserApi();
        installInternalUserApi();
        installUsagePluginApi();
    }
}
