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

package org.killbill.billing.server.modules;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.shiro.authc.pam.ModularRealmAuthenticator;
import org.apache.shiro.authc.pam.ModularRealmAuthenticatorWith540;
import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.guice.web.ShiroWebModuleWith435;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.session.mgt.eis.CachingSessionDAO;
import org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.apache.shiro.web.session.mgt.DefaultWebSessionManager;
import org.apache.shiro.web.util.WebUtils;
import org.killbill.billing.jaxrs.resources.JaxrsResource;
import org.killbill.billing.server.security.FirstSuccessfulStrategyWith540;
import org.killbill.billing.server.security.KillbillJdbcTenantRealm;
import org.killbill.billing.util.config.definition.RbacConfig;
import org.killbill.billing.util.glue.EhCacheManagerProvider;
import org.killbill.billing.util.glue.IniRealmProvider;
import org.killbill.billing.util.glue.JDBCSessionDaoProvider;
import org.killbill.billing.util.glue.KillBillShiroModule;
import org.killbill.billing.util.glue.ShiroEhCacheInstrumentor;
import org.killbill.billing.util.security.shiro.dao.JDBCSessionDao;
import org.killbill.billing.util.security.shiro.realm.KillBillJdbcRealm;
import org.killbill.billing.util.security.shiro.realm.KillBillJndiLdapRealm;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

// For Kill Bill server only.
// See org.killbill.billing.util.glue.KillBillShiroModule for Kill Bill library.
public class KillBillShiroWebModule extends ShiroWebModuleWith435 {

    private final ConfigSource configSource;

    public KillBillShiroWebModule(final ServletContext servletContext, final ConfigSource configSource) {
        super(servletContext);
        this.configSource = configSource;
    }

    @Override
    public void configure() {
        super.configure();

        bind(ShiroEhCacheInstrumentor.class).asEagerSingleton();
    }

    @Override
    protected void configureShiroWeb() {
        // Magic provider to configure the cache manager
        bind(CacheManager.class).toProvider(EhCacheManagerProvider.class).asEagerSingleton();

        configureShiroForRBAC();

        configureShiroForTenants();
    }

    private void configureShiroForRBAC() {
        final RbacConfig config = new ConfigurationObjectFactory(configSource).build(RbacConfig.class);
        bind(RbacConfig.class).toInstance(config);

        // Note: order matters (the first successful match will win, see below)
        bindRealm().toProvider(IniRealmProvider.class).asEagerSingleton();
        bindRealm().to(KillBillJdbcRealm.class).asEagerSingleton();
        if (KillBillShiroModule.isLDAPEnabled()) {
            bindRealm().to(KillBillJndiLdapRealm.class).asEagerSingleton();
        }

        bindListener(new AbstractMatcher<TypeLiteral<?>>() {
                         @Override
                         public boolean matches(final TypeLiteral<?> o) {
                             return Matchers.subclassesOf(WebSecurityManager.class).matches(o.getRawType());
                         }
                     },
                     new DefaultWebSecurityManagerTypeListener(getProvider(ShiroEhCacheInstrumentor.class)));

        if (KillBillShiroModule.isRBACEnabled()) {
            addFilterChain(JaxrsResource.PREFIX + "/**", Key.get(CorsBasicHttpAuthenticationFilter.class));
        }
    }

    private void configureShiroForTenants() {
        // Realm binding for the tenants (see TenantFilter)
        bind(KillbillJdbcTenantRealm.class).toProvider(KillbillJdbcTenantRealmProvider.class).asEagerSingleton();
        expose(KillbillJdbcTenantRealm.class);
    }

    @Override
    protected void bindSessionManager(final AnnotatedBindingBuilder<SessionManager> bind) {
        // Bypass the servlet container completely for session management and delegate it to Shiro.
        // The default session timeout is 30 minutes.
        bind.to(DefaultWebSessionManager.class).asEagerSingleton();

        // Magic provider to configure the session DAO
        bind(JDBCSessionDao.class).toProvider(JDBCSessionDaoProvider.class).asEagerSingleton();
    }

    public static final class CorsBasicHttpAuthenticationFilter extends BasicHttpAuthenticationFilter {

        @Override
        protected boolean isAccessAllowed(final ServletRequest request, final ServletResponse response, final Object mappedValue) {
            final HttpServletRequest httpRequest = WebUtils.toHttp(request);
            final String httpMethod = httpRequest.getMethod();
            // Don't require any authorization or authentication header for OPTIONS requests
            // See https://bugzilla.mozilla.org/show_bug.cgi?id=778548 and http://www.kinvey.com/blog/60/kinvey-adds-cross-origin-resource-sharing-cors
            return "OPTIONS".equalsIgnoreCase(httpMethod) || super.isAccessAllowed(request, response, mappedValue);
        }
    }

    private static final class DefaultWebSecurityManagerTypeListener implements TypeListener {

        private final Provider<ShiroEhCacheInstrumentor> instrumentorProvider;

        @Inject
        public DefaultWebSecurityManagerTypeListener(final Provider<ShiroEhCacheInstrumentor> instrumentorProvider) {
            this.instrumentorProvider = instrumentorProvider;
        }

        @Override
        public <I> void hear(final TypeLiteral<I> typeLiteral, final TypeEncounter<I> typeEncounter) {
            typeEncounter.register(new InjectionListener<I>() {
                @Override
                public void afterInjection(final Object o) {
                    final ShiroEhCacheInstrumentor ehCacheInstrumentor = instrumentorProvider.get();
                    ehCacheInstrumentor.instrument(CachingSessionDAO.ACTIVE_SESSION_CACHE_NAME);

                    final DefaultWebSecurityManager webSecurityManager = (DefaultWebSecurityManager) o;
                    if (webSecurityManager.getAuthenticator() instanceof ModularRealmAuthenticator) {
                        final ModularRealmAuthenticator authenticator = (ModularRealmAuthenticator) webSecurityManager.getAuthenticator();
                        authenticator.setAuthenticationStrategy(new FirstSuccessfulStrategyWith540());
                        webSecurityManager.setAuthenticator(new ModularRealmAuthenticatorWith540(authenticator));

                        for (final Realm realm : webSecurityManager.getRealms()) {
                            ehCacheInstrumentor.instrument(realm);
                        }
                    }
                }
            });
        }
    }
}
