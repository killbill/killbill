/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.guice.ShiroModule;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.session.mgt.DefaultSessionManager;
import org.apache.shiro.session.mgt.SessionManager;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.RbacConfig;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.billing.util.security.shiro.dao.JDBCSessionDao;
import org.killbill.billing.util.security.shiro.realm.KillBillJdbcRealm;
import org.killbill.billing.util.security.shiro.realm.KillBillJndiLdapRealm;
import org.killbill.billing.util.security.shiro.realm.KillBillOktaRealm;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import com.google.inject.Provider;
import com.google.inject.binder.AnnotatedBindingBuilder;

// For Kill Bill library only.
// See org.killbill.billing.server.modules.KillBillShiroWebModule for Kill Bill server.
public class KillBillShiroModule extends ShiroModule {

    public static final String KILLBILL_LDAP_PROPERTY = "killbill.server.ldap";
    public static final String KILLBILL_OKTA_PROPERTY = "killbill.server.okta";
    public static final String KILLBILL_RBAC_PROPERTY = "killbill.server.rbac";

    public static boolean isLDAPEnabled() {
        return Boolean.parseBoolean(System.getProperty(KILLBILL_LDAP_PROPERTY, "false"));
    }

    public static boolean isOktaEnabled() {
        return Boolean.parseBoolean(System.getProperty(KILLBILL_OKTA_PROPERTY, "false"));
    }

    public static boolean isRBACEnabled() {
        return Boolean.parseBoolean(System.getProperty(KILLBILL_RBAC_PROPERTY, "true"));
    }

    private final KillbillConfigSource configSource;

    public KillBillShiroModule(final KillbillConfigSource configSource) {
        this.configSource = configSource;
    }

    protected void configureShiro() {
        final RbacConfig config = new ConfigurationObjectFactory(new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return configSource.getString(propertyName);
            }
        }).build(RbacConfig.class);
        bind(RbacConfig.class).toInstance(config);

        final ConfigSource skifeConfigSource = new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return configSource.getString(propertyName);
            }
        };

        bind(RbacConfig.class).toInstance(config);

        final Provider<IniRealm> iniRealmProvider = RealmsFromShiroIniProvider.getIniRealmProvider(skifeConfigSource);
        // Hack for Kill Bill library to work around weird Guice ClassCastException when using
        // bindRealm().toInstance(...) -- this means we don't support custom realms when embedding Kill Bill
        bindRealm().toProvider(iniRealmProvider).asEagerSingleton();

        configureJDBCRealm();

        configureLDAPRealm();

        configureOktaRealm();
    }

    protected void configureJDBCRealm() {
        bindRealm().to(KillBillJdbcRealm.class).asEagerSingleton();
    }

    protected void configureLDAPRealm() {
        if (isLDAPEnabled()) {
            bindRealm().to(KillBillJndiLdapRealm.class).asEagerSingleton();
        }
    }

    protected void configureOktaRealm() {
        if (isOktaEnabled()) {
            bindRealm().to(KillBillOktaRealm.class).asEagerSingleton();
        }
    }

    @Override
    protected void bindSecurityManager(final AnnotatedBindingBuilder<? super SecurityManager> bind) {
        super.bindSecurityManager(bind);

        // Magic provider to configure the cache manager
        bind(CacheManager.class).toProvider(EhcacheShiroManagerProvider.class).asEagerSingleton();
    }

    @Override
    protected void bindSessionManager(final AnnotatedBindingBuilder<SessionManager> bind) {
        bind.to(DefaultSessionManager.class).asEagerSingleton();

        // Magic provider to configure the session DAO
        bind(JDBCSessionDao.class).toProvider(JDBCSessionDaoProvider.class).asEagerSingleton();
    }
}
