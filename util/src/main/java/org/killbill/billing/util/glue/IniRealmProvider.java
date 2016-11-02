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
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Really Provider<IniRealm>, but avoid an extra cast below
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
            final Factory<SecurityManager> factory = new IniSecurityManagerFactory(securityConfig.getShiroResourcePath());
            // TODO Pierre hack - lame cast here, but we need to have Shiro go through its reflection magic
            // to parse the [main] section of the ini file. Without duplicating code, this seems to be possible only
            // by going through IniSecurityManagerFactory.
            final DefaultSecurityManager securityManager = (DefaultSecurityManager) factory.getInstance();
            final Collection<Realm> realms = securityManager.getRealms();

            IniRealm iniRealm = null;
            if (realms == null || realms.isEmpty()) {
                iniRealm = new IniRealm(securityConfig.getShiroResourcePath());
            } else {
                for (final Realm cur : realms) {
                    if (cur instanceof IniRealm) {
                        iniRealm = (IniRealm) cur;
                        break;
                    }
                }
            }
            if (iniRealm != null) {
                // See JavaDoc warning: https://shiro.apache.org/static/1.2.3/apidocs/org/apache/shiro/realm/AuthenticatingRealm.html
                iniRealm.setAuthenticationCachingEnabled(true);

                return iniRealm;
            } else {
                throw new ConfigurationException();
            }
        } catch (final ConfigurationException e) {
            log.warn("Unable to configure RBAC", e);
            return new IniRealm();
        }
    }
}
