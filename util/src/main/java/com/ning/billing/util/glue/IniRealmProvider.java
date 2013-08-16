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

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.shiro.config.ConfigurationException;
import org.apache.shiro.realm.text.IniRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.config.SecurityConfig;

public class IniRealmProvider implements Provider<IniRealm> {

    private static final Logger log = LoggerFactory.getLogger(IniRealmProvider.class);

    private final SecurityConfig securityConfig;

    @Inject
    public IniRealmProvider(final SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    @Override
    public IniRealm get() {
        try {
            return new IniRealm(securityConfig.getShiroResourcePath());
        } catch (ConfigurationException e) {
            log.warn(e.getLocalizedMessage());
            return new IniRealm();
        }
    }
}
