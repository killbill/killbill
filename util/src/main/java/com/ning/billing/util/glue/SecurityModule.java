/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.util.glue;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;

import com.ning.billing.security.api.SecurityApi;
import com.ning.billing.util.config.SecurityConfig;
import com.ning.billing.util.security.api.DefaultSecurityApi;
import com.ning.billing.util.security.api.DefaultSecurityService;
import com.ning.billing.util.security.api.SecurityService;

import com.google.inject.AbstractModule;

public class SecurityModule extends AbstractModule {

    private final ConfigSource configSource;

    public SecurityModule() {
        this(new SimplePropertyConfigSource(System.getProperties()));
    }

    public SecurityModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    public void configure() {
        installConfig();
        installSecurityApi();
        installSecurityService();
    }

    private void installConfig() {
        final SecurityConfig securityConfig = new ConfigurationObjectFactory(configSource).build(SecurityConfig.class);
        bind(SecurityConfig.class).toInstance(securityConfig);
    }

    private void installSecurityApi() {
        bind(SecurityApi.class).to(DefaultSecurityApi.class).asEagerSingleton();
    }

    protected void installSecurityService() {
        bind(SecurityService.class).to(DefaultSecurityService.class).asEagerSingleton();
    }
}
