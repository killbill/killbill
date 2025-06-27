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

package org.killbill.billing.server.modules;

import java.util.Collection;
import java.util.Set;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.shiro.authc.pam.ModularRealmAuthenticator;
import org.apache.shiro.authc.pam.ModularRealmAuthenticatorWith540;
import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.guice.web.ShiroWebModuleWith435;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SubjectDAO;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.session.mgt.eis.SessionDAO;
import org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter;
import org.apache.shiro.web.filter.authc.BearerHttpAuthenticationFilter;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.mgt.WebSecurityManager;
import org.apache.shiro.web.util.WebUtils;
import org.killbill.billing.jaxrs.resources.JaxrsResource;
import org.killbill.billing.server.security.FirstSuccessfulStrategyWith540;
import org.killbill.billing.server.security.KillBillWebSessionManager;
import org.killbill.billing.server.security.KillbillJdbcTenantRealm;
import org.killbill.billing.util.config.definition.RbacConfig;
import org.killbill.billing.util.config.definition.RedisCacheConfig;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.billing.util.glue.EhcacheShiroManagerProvider;
import org.killbill.billing.util.glue.KillBillShiroModule;
import org.killbill.billing.util.glue.RealmsFromShiroIniProvider;
import org.killbill.billing.util.glue.RedisShiroManagerProvider;
import org.killbill.billing.util.glue.SessionDAOProvider;
import org.killbill.billing.util.security.shiro.realm.KillBillAuth0Realm;
import org.killbill.billing.util.security.shiro.realm.KillBillJdbcRealm;
import org.killbill.billing.util.security.shiro.realm.KillBillJndiLdapRealm;
import org.killbill.billing.util.security.shiro.realm.KillBillOktaRealm;
import org.skife.config.ConfigSource;
import org.skife.config.AugmentedConfigurationObjectFactory;

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;

// For Kill Bill server only.
// See org.killbill.billing.util.glue.KillBillShiroModule for Kill Bill library.
public class KillBillShiroWebModule extends ShiroWebModuleWith435 {

    private final ConfigSource configSource;
    private final DefaultSecurityManager defaultSecurityManager;

    public KillBillShiroWebModule(final ServletContext servletContext, final ConfigSource configSource) {
        super(servletContext);
        this.configSource = configSource;
        this.defaultSecurityManager = RealmsFromShiroIniProvider.get(configSource);
    }

    @Override
    protected void configureShiroWeb() {
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

        final SecurityConfig securityConfig = new AugmentedConfigurationObjectFactory(configSource).build(SecurityConfig.class);
        final Collection<Realm> realms = defaultSecurityManager.getRealms() != null ? defaultSecurityManager.getRealms() :
                                         Set.of(new IniRealm(securityConfig.getShiroResourcePath())); // Mainly for testing
        for (final Realm realm : realms) {
            bindRealm().toInstance(realm);
        }

        configureShiroForRBAC();

        configureShiroForTenants();

        expose(new TypeLiteral<Set<Realm>>() {});
    }

    private void configureShiroForRBAC() {
        final RbacConfig config = new AugmentedConfigurationObjectFactory(configSource).build(RbacConfig.class);
        bind(RbacConfig.class).toInstance(config);

        bindRealm().to(KillBillJdbcRealm.class).asEagerSingleton();
        if (KillBillShiroModule.isLDAPEnabled()) {
            bindRealm().to(KillBillJndiLdapRealm.class).asEagerSingleton();
        }
        if (KillBillShiroModule.isOktaEnabled()) {
            bindRealm().to(KillBillOktaRealm.class).asEagerSingleton();
        }
        if (KillBillShiroModule.isAuth0Enabled()) {
            bindRealm().to(KillBillAuth0Realm.class).asEagerSingleton();
        }

        if (KillBillShiroModule.isRBACEnabled()) {
            addFilterChain(JaxrsResource.PREFIX + "/**", Key.get(BearerHttpAuthenticationPermissiveFilter.class), Key.get(CorsBasicHttpAuthenticationFilter.class));
            addFilterChain(JaxrsResource.PLUGINS_PATH + "/**", Key.get(BearerHttpAuthenticationPermissiveFilter.class), Key.get(CorsBasicHttpAuthenticationOptionalFilter.class));
        }
    }

    private void configureShiroForTenants() {
        // Realm binding for the tenants (see TenantFilter)
        bind(KillbillJdbcTenantRealm.class).toProvider(KillbillJdbcTenantRealmProvider.class).asEagerSingleton();
        expose(KillbillJdbcTenantRealm.class);
    }

    @Override
    protected void bindWebSecurityManager(final AnnotatedBindingBuilder<? super WebSecurityManager> bind) {
        //super.bindWebSecurityManager(bind);
        // This following is to work around obscure Guice issues
        bind.toProvider(KillBillWebSecurityManagerProvider.class).asEagerSingleton();
    }

    public static final class KillBillWebSecurityManagerProvider implements Provider<DefaultWebSecurityManager> {

        private final Collection<Realm> realms;
        private final SessionManager sessionManager;

        @Inject
        public KillBillWebSecurityManagerProvider(final Collection<Realm> realms, final SessionManager sessionManager) {
            this.realms = realms;
            this.sessionManager = sessionManager;
        }

        @Override
        public DefaultWebSecurityManager get() {
            final DefaultWebSecurityManager defaultWebSecurityManager = new DefaultWebSecurityManager(realms);
            defaultWebSecurityManager.setSessionManager(sessionManager);

            final ModularRealmAuthenticator authenticator = (ModularRealmAuthenticator) defaultWebSecurityManager.getAuthenticator();
            authenticator.setAuthenticationStrategy(new FirstSuccessfulStrategyWith540());
            defaultWebSecurityManager.setAuthenticator(new ModularRealmAuthenticatorWith540(defaultWebSecurityManager.getRealms(), authenticator));

            return defaultWebSecurityManager;
        }
    }

    @Override
    protected void bindSessionManager(final AnnotatedBindingBuilder<SessionManager> bind) {
        // Bypass the servlet container completely for session management and delegate it to Shiro.
        // The default session timeout is 30 minutes.
        bind.to(KillBillWebSessionManager.class).asEagerSingleton();

        bind(SubjectDAO.class).toProvider(KillBillWebSubjectDAOProvider.class).asEagerSingleton();

        // Magic provider to configure the session DAO
        bind(SessionDAO.class).toProvider(SessionDAOProvider.class).asEagerSingleton();
    }

    public static final class BearerHttpAuthenticationPermissiveFilter extends BearerHttpAuthenticationFilter {

        @Override
        protected boolean onAccessDenied(final ServletRequest request, final ServletResponse response) throws Exception {
            super.onAccessDenied(request, response);
            // Fallback to CorsBasicHttpAuthenticationFilter
            return true;
        }

        @Override
        protected boolean sendChallenge(final ServletRequest request, final ServletResponse response) {
            // Don't send the challenge yet, need to go through the Authc filter first
            return true;
        }
    }

    public static class CorsBasicHttpAuthenticationFilter extends BasicHttpAuthenticationFilter {

        @Override
        protected boolean isAccessAllowed(final ServletRequest request, final ServletResponse response, final Object mappedValue) {
            final HttpServletRequest httpRequest = WebUtils.toHttp(request);
            final String httpMethod = httpRequest.getMethod();
            // Don't require any authorization or authentication header for OPTIONS requests
            // See https://bugzilla.mozilla.org/show_bug.cgi?id=778548 and http://www.kinvey.com/blog/60/kinvey-adds-cross-origin-resource-sharing-cors
            return "OPTIONS".equalsIgnoreCase(httpMethod) || super.isAccessAllowed(request, response, mappedValue);
        }
    }

    public static final class CorsBasicHttpAuthenticationOptionalFilter extends CorsBasicHttpAuthenticationFilter {

        @Override
        protected boolean onAccessDenied(final ServletRequest request, final ServletResponse response) throws Exception {
            if (isLoginAttempt(request, response)) {
                // Attempt to log-in
                executeLogin(request, response);
            }

            // Unlike the original method, we don't send a challenge on failure but simply allow the request to continue
            return true;
        }
    }
}
