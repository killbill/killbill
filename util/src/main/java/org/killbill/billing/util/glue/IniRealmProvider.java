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

package org.killbill.billing.util.glue;

import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Provider;

import org.apache.shiro.config.ConfigurationException;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.util.Factory;
import org.killbill.billing.util.config.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Really Provider<IniRealm>, but avoid an extra cast below
public class IniRealmProvider implements Provider<Realm> {

    private static final Logger log = LoggerFactory.getLogger(IniRealmProvider.class);

    private final SecurityConfig securityConfig;

    @Inject
    public IniRealmProvider(final SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    @Override
    public Realm get() {
        try {
            final Factory<SecurityManager> factory = new IniSecurityManagerFactory(securityConfig.getShiroResourcePath());
            // TODO Pierre hack - lame cast here, but we need to have Shiro go through its reflection magic
            // to parse the [main] section of the ini file. Without duplicating code, this seems to be possible only
            // by going through IniSecurityManagerFactory.
            final DefaultSecurityManager securityManager = (DefaultSecurityManager) factory.getInstance();
            final Collection<Realm> realms = securityManager.getRealms();
            // Null check mainly for testing
            return realms == null ? new IniRealm(securityConfig.getShiroResourcePath()) : realms.iterator().next();
        } catch (final ConfigurationException e) {
            log.warn("Unable to configure RBAC", e);
            return new IniRealm();
        }
    }
}
