/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.guice.ShiroModule;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.mgt.SubjectDAO;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.session.mgt.DefaultSessionManager;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.session.mgt.eis.SessionDAO;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.RbacConfig;
import org.killbill.billing.util.config.definition.RedisCacheConfig;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.billing.util.security.shiro.realm.KillBillAuth0Realm;
import org.killbill.billing.util.security.shiro.realm.KillBillJdbcRealm;
import org.killbill.billing.util.security.shiro.realm.KillBillJndiLdapRealm;
import org.killbill.billing.util.security.shiro.realm.KillBillOktaRealm;
import org.skife.config.ConfigSource;
import org.skife.config.AugmentedConfigurationObjectFactory;

import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;

// For Kill Bill library only.
// See org.killbill.billing.server.modules.KillBillShiroWebModule for Kill Bill server.
public class KillBillShiroModule extends ShiroModule {


    public static final String KILLBILL_LDAP_PROPERTY = "killbill.server.ldap";
    public static final String KILLBILL_OKTA_PROPERTY = "killbill.server.okta";
    public static final String KILLBILL_AUTH0_PROPERTY = "killbill.server.auth0";
    public static final String KILLBILL_RBAC_PROPERTY = "killbill.server.rbac";

    public static boolean isLDAPEnabled() {
        return Boolean.parseBoolean(System.getProperty(KILLBILL_LDAP_PROPERTY, "false"));
    }

    public static boolean isOktaEnabled() {
        return Boolean.parseBoolean(System.getProperty(KILLBILL_OKTA_PROPERTY, "false"));
    }

    public static boolean isAuth0Enabled() {
        return Boolean.parseBoolean(System.getProperty(KILLBILL_AUTH0_PROPERTY, "false"));
    }

    public static boolean isRBACEnabled() {
        return Boolean.parseBoolean(System.getProperty(KILLBILL_RBAC_PROPERTY, "true"));
    }

    private final KillbillConfigSource configSource;
    private final ConfigSource skifeConfigSource;
    private final DefaultSecurityManager defaultSecurityManager;

    public KillBillShiroModule(final KillbillConfigSource configSource) {
        this.configSource = configSource;
        this.skifeConfigSource = new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return configSource.getString(propertyName);
            }
        };
        this.defaultSecurityManager = RealmsFromShiroIniProvider.get(skifeConfigSource);
    }

    protected void configureShiro() {
        final RbacConfig config = new AugmentedConfigurationObjectFactory(new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return configSource.getString(propertyName);
            }
        }).build(RbacConfig.class);
        bind(RbacConfig.class).toInstance(config);

        bind(RbacConfig.class).toInstance(config);

        final SecurityConfig securityConfig = new AugmentedConfigurationObjectFactory(skifeConfigSource).build(SecurityConfig.class);
        final Collection<Realm> realms = defaultSecurityManager.getRealms() != null ? defaultSecurityManager.getRealms() :
                                         List.of(new IniRealm(securityConfig.getShiroResourcePath())); // Mainly for testing
        for (final Realm realm : realms) {
            bindRealm().toInstance(realm);
        }

        configureJDBCRealm();

        configureLDAPRealm();

        configureOktaRealm();

        configureAuth0Realm();

        expose(new TypeLiteral<Set<Realm>>() {});
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

    protected void configureAuth0Realm() {
        if (isAuth0Enabled()) {
            bindRealm().to(KillBillAuth0Realm.class).asEagerSingleton();
        }
    }

    @Override
    protected void bindSecurityManager(final AnnotatedBindingBuilder<? super SecurityManager> bind) {
        //super.bindSecurityManager(bind);
        bind.toInstance(defaultSecurityManager);

        final RedisCacheConfig redisCacheConfig = new AugmentedConfigurationObjectFactory(new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                return configSource.getString(propertyName);
            }
        }).build(RedisCacheConfig.class);

        // Magic provider to configure the cache manager
        if (redisCacheConfig.isRedisCachingEnabled()) {
            bind(CacheManager.class).toProvider(RedisShiroManagerProvider.class).asEagerSingleton();
        } else {
            bind(CacheManager.class).toProvider(EhcacheShiroManagerProvider.class).asEagerSingleton();
        }
    }

    @Override
    protected void bindSessionManager(final AnnotatedBindingBuilder<SessionManager> bind) {
        bind.to(DefaultSessionManager.class).asEagerSingleton();

        bind(SubjectDAO.class).toProvider(KillBillSubjectDAOProvider.class).asEagerSingleton();

        // Magic provider to configure the session DAO
        bind(SessionDAO.class).toProvider(SessionDAOProvider.class).asEagerSingleton();
    }
}
