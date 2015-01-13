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

package org.killbill.billing.server.security;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.pam.ModularRealmAuthenticator;
import org.apache.shiro.realm.Realm;
import org.killbill.billing.jaxrs.resources.JaxrsResource;
import org.killbill.billing.server.listeners.KillbillGuiceListener;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.commons.jdbi.guice.DaoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

@Singleton
public class TenantFilter implements Filter {

    // See org.killbill.billing.jaxrs.util.Context
    public static final String TENANT = "killbill_tenant";

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    @Inject
    protected TenantUserApi tenantUserApi;

    @Inject
    protected DaoConfig daoConfig;

    private ModularRealmAuthenticator modularRealmAuthenticator;

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        final Realm killbillJdbcRealm = new KillbillJdbcRealm(daoConfig);
        // We use Shiro to verify the api credentials - but the Shiro Subject is only used for RBAC
        modularRealmAuthenticator = new ModularRealmAuthenticator();
        modularRealmAuthenticator.setRealms(ImmutableList.<Realm>of(killbillJdbcRealm));
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        if (shouldSkipFilter(request)) {
            chain.doFilter(request, response);
            return;
        }

        // Lookup tenant information in the headers
        String apiKey = null;
        String apiSecret = null;
        if (request instanceof HttpServletRequest) {
            final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
            apiKey = httpServletRequest.getHeader(JaxrsResource.HDR_API_KEY);
            apiSecret = httpServletRequest.getHeader(JaxrsResource.HDR_API_SECRET);
        }

        // Multi-tenancy is enabled if this filter is installed, we can't continue without credentials
        if (apiKey == null || apiSecret == null) {
            final String errorMessage = String.format("Make sure to set the %s and %s headers", JaxrsResource.HDR_API_KEY, JaxrsResource.HDR_API_SECRET);
            sendAuthError(response, errorMessage);
            return;
        }

        // Verify the apiKey/apiSecret combo
        final AuthenticationToken token = new UsernamePasswordToken(apiKey, apiSecret);
        try {
            modularRealmAuthenticator.authenticate(token);
        } catch (final AuthenticationException e) {
            final String errorMessage = e.getLocalizedMessage();
            sendAuthError(response, errorMessage);
            return;
        }

        try {
            // Load the tenant in the request object (apiKey is unique across tenants)
            final Tenant tenant = tenantUserApi.getTenantByApiKey(apiKey);
            request.setAttribute(TENANT, tenant);

            chain.doFilter(request, response);
        } catch (final TenantApiException e) {
            // Should never happen since Shiro validated the credentials?
            log.warn("Couldn't find the tenant?", e);
        }
    }

    @Override
    public void destroy() {
    }

    private boolean shouldSkipFilter(final ServletRequest request) {
        boolean shouldSkip = false;

        if (request instanceof HttpServletRequest) {
            final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
            // TODO Wrong - See https://github.com/killbill/killbill/issues/221
            final String path = httpServletRequest.getRequestURI();
            final String httpMethod = httpServletRequest.getMethod();
            if (    // Chicken - egg problem
                    isTenantCreationRequest(path, httpMethod) ||
                    // Retrieve user permissions should not require tenant info since this is cross tenants
                    isPermissionRequest(path, httpMethod) ||
                    // Metrics servlets
                    isMetricsRequest(path, httpMethod) ||
                    // See KillBillShiroWebModule#CorsBasicHttpAuthenticationFilter
                    isOptionsRequest(httpMethod) ||
                    // Static resources
                    isStaticResourceRequest(path, httpMethod)
                    ) {
                shouldSkip = true;
            }
        }

        return shouldSkip;
    }

    private boolean isPermissionRequest(final String path, final String httpMethod) {
        return JaxrsResource.SECURITY_PATH.startsWith(path) && "GET".equals(httpMethod);
    }

    private boolean isTenantCreationRequest(final String path, final String httpMethod) {
        return JaxrsResource.TENANTS_PATH.equals(path) && "POST".equals(httpMethod);
    }

    private boolean isMetricsRequest(final String path, final String httpMethod) {
        return KillbillGuiceListener.METRICS_SERVLETS_PATHS.contains(path) && "GET".equals(httpMethod);
    }

    private boolean isOptionsRequest(final String httpMethod) {
        return "OPTIONS".equals(httpMethod);
    }

    private boolean isStaticResourceRequest(final String path, final String httpMethod) {
        if (isPluginRequest(path)) {
            // For plugins requests, we want to validate the Tenant except for HTML, JS, etc. files
            return isStaticFileRequest(path) && "GET".equals(httpMethod);
        } else {
            // Welcome screen, Swagger, etc.
            return !isKbApiRequest(path) && "GET".equals(httpMethod);
        }
    }

    private boolean isKbApiRequest(final String path) {
        return path.startsWith(JaxrsResource.PREFIX);
    }

    private boolean isPluginRequest(final String path) {
        return path.startsWith(JaxrsResource.PLUGINS_PATH);
    }

    private boolean isStaticFileRequest(final String path) {
        return path.endsWith(".htm") ||
               path.endsWith(".html") ||
               path.endsWith(".js") ||
               path.endsWith(".css") ||
               path.endsWith(".gz") ||
               path.endsWith(".xml") ||
               path.endsWith(".txt") ||
               path.endsWith(".map")||
               path.endsWith(".woff")||
               path.endsWith(".ttf");
    }

    private void sendAuthError(final ServletResponse response, final String errorMessage) throws IOException {
        if (response instanceof HttpServletResponse) {
            final HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            httpServletResponse.sendError(401, errorMessage);
        }
    }
}
