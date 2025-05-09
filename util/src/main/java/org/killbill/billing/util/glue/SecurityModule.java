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

package org.killbill.billing.util.glue;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.billing.util.security.api.DefaultSecurityApi;
import org.killbill.billing.util.security.api.DefaultSecurityService;
import org.killbill.billing.util.security.api.SecurityService;
import org.killbill.billing.util.security.shiro.dao.DefaultUserDao;
import org.killbill.billing.util.security.shiro.dao.UserDao;
import org.skife.config.AugmentedConfigurationObjectFactory;

public class SecurityModule extends KillBillModule {

    public SecurityModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    public void configure() {
        installConfig();
        installDao();
        installSecurityApi();
        installSecurityService();
    }

    protected void installDao() {
        bind(UserDao.class).to(DefaultUserDao.class).asEagerSingleton();
    }

    private void installConfig() {
        final SecurityConfig securityConfig = new AugmentedConfigurationObjectFactory(skifeConfigSource).build(SecurityConfig.class);
        bind(SecurityConfig.class).toInstance(securityConfig);
    }

    private void installSecurityApi() {
        bind(SecurityApi.class).to(DefaultSecurityApi.class).asEagerSingleton();
    }

    protected void installSecurityService() {
        bind(SecurityService.class).to(DefaultSecurityService.class).asEagerSingleton();
    }
}
