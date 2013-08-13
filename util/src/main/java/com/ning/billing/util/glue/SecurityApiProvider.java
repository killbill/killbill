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

import javax.inject.Provider;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.config.ConfigurationException;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.security.api.SecurityApi;
import com.ning.billing.util.config.SecurityConfig;
import com.ning.billing.util.security.api.DefaultSecurityApi;
import com.ning.billing.util.security.api.NoOpSecurityApi;

public class SecurityApiProvider implements Provider<SecurityApi> {

    private final Logger log = LoggerFactory.getLogger(SecurityApiProvider.class);

    private final SecurityConfig securityConfig;

    private boolean shiroConfigured;

    public SecurityApiProvider(final SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    @Override
    public SecurityApi get() {
        installShiro();

        if (shiroConfigured) {
            return new DefaultSecurityApi();
        } else {
            return new NoOpSecurityApi();
        }
    }

    private void installShiro() {
        try {
            // Hook, mainly for testing
            SecurityUtils.getSecurityManager();
            shiroConfigured = true;
        } catch (UnavailableSecurityManagerException ignored) {
            try {
                final Factory<SecurityManager> factory = new IniSecurityManagerFactory(securityConfig.getShiroResourcePath());
                final SecurityManager securityManager = factory.getInstance();
                SecurityUtils.setSecurityManager(securityManager);
                shiroConfigured = true;
            } catch (ConfigurationException e) {
                log.warn(String.format("Unable to configure Shiro [%s] - RBAC WILL BE DISABLED!", e.getLocalizedMessage()));
                shiroConfigured = false;
            }
        }
    }
}
