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

package com.ning.billing.server.modules;

import javax.servlet.ServletContext;

import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.guice.web.ShiroWebModule;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.web.session.mgt.DefaultWebSessionManager;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.billing.util.config.RbacConfig;
import com.ning.billing.util.glue.EhCacheManagerProvider;
import com.ning.billing.util.glue.IniRealmProvider;
import com.ning.billing.util.glue.JDBCSessionDaoProvider;
import com.ning.billing.util.glue.KillBillShiroModule;
import com.ning.billing.util.security.shiro.dao.JDBCSessionDao;
import com.ning.billing.util.security.shiro.realm.KillBillJndiLdapRealm;

import com.google.inject.binder.AnnotatedBindingBuilder;

// For Kill Bill server only.
// See com.ning.billing.util.glue.KillBillShiroModule for Kill Bill library.
public class KillBillShiroWebModule extends ShiroWebModule {

    private final ConfigSource configSource;

    public KillBillShiroWebModule(final ServletContext servletContext, final ConfigSource configSource) {
        super(servletContext);
        this.configSource = configSource;
    }

    @Override
    protected void configureShiroWeb() {
        final RbacConfig config = new ConfigurationObjectFactory(configSource).build(RbacConfig.class);
        bind(RbacConfig.class).toInstance(config);

        bindRealm().toProvider(IniRealmProvider.class).asEagerSingleton();

        if (KillBillShiroModule.isLDAPEnabled()) {
            bindRealm().to(KillBillJndiLdapRealm.class).asEagerSingleton();
        }

        // Magic provider to configure the cache manager
        bind(CacheManager.class).toProvider(EhCacheManagerProvider.class).asEagerSingleton();

        if (KillBillShiroModule.isRBACEnabled()) {
            addFilterChain(JaxrsResource.PREFIX + "/**", AUTHC_BASIC);
        }
    }

    @Override
    protected void bindSessionManager(final AnnotatedBindingBuilder<SessionManager> bind) {
        // Bypass the servlet container completely for session management and delegate it to Shiro.
        // The default session timeout is 30 minutes.
        bind.to(DefaultWebSessionManager.class).asEagerSingleton();

        // Magic provider to configure the session DAO
        bind(JDBCSessionDao.class).toProvider(JDBCSessionDaoProvider.class).asEagerSingleton();
    }
}
