/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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
import org.killbill.billing.jaxrs.resources.KillBillApiDefinition;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.server.listeners.KillbillGuiceListener;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

@Singleton
public class TenantFilter implements Filter {

    // See org.killbill.billing.jaxrs.util.Context
    public static final String TENANT = "killbill_tenant";

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    @Inject
    protected Context context;
    @Inject
    protected TenantUserApi tenantUserApi;
    @Inject
    protected KillbillJdbcTenantRealm killbillJdbcTenantRealm;

    private ModularRealmAuthenticator modularRealmAuthenticator;

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        // We use Shiro to verify the api credentials - but the Shiro Subject is only used for RBAC
        modularRealmAuthenticator = new ModularRealmAuthenticator();
        modularRealmAuthenticator.setRealms(ImmutableList.<Realm>of(killbillJdbcTenantRealm));
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
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
            handleAuthenticationError(errorMessage, chain, request, response);
            return;
        }

        // Verify the apiKey/apiSecret combo
        final AuthenticationToken token = new UsernamePasswordToken(apiKey, apiSecret);
        try {
            modularRealmAuthenticator.authenticate(token);
        } catch (final AuthenticationException e) {
            final String errorMessage = e.getLocalizedMessage();
            handleAuthenticationError(errorMessage, chain, request, response);
            return;
        }

        try {
            // Load the tenant in the request object (apiKey is unique across tenants)
            final Tenant tenant = tenantUserApi.getTenantByApiKey(apiKey);
            request.setAttribute(TENANT, tenant);

            // Create a dummy context, to set the MDC very early for LoggingFilter
            context.createTenantContextNoAccountId(request);

            chain.doFilter(request, response);
        } catch (final TenantApiException e) {
            // Should never happen since Shiro validated the credentials?
            log.error("Couldn't find the tenant? - should never happen!", e);
        }
    }

    private void handleAuthenticationError(final String errorMessage, final FilterChain chain, final ServletRequest request, final ServletResponse response) throws IOException, ServletException {
        if (shouldContinueIfTenantInformationIsWrongOrMissing(request)) {
            chain.doFilter(request, response);
        } else {
            sendAuthError(response, errorMessage);
        }
    }

    @Override
    public void destroy() {
    }

    private boolean shouldContinueIfTenantInformationIsWrongOrMissing(final ServletRequest request) {
        boolean shouldContinue = false;
        if (request instanceof HttpServletRequest) {
            final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
            final String path = httpServletRequest.getPathInfo();
            final String httpMethod = httpServletRequest.getMethod();
            shouldContinue = shouldContinueIfTenantInformationIsWrongOrMissing(path, httpMethod);
        }
        return shouldContinue;
    }

    private static boolean isMetricsRequest(final String path, final String httpMethod) {
        return KillbillGuiceListener.METRICS_SERVLETS_PATHS.contains(path) && "GET".equalsIgnoreCase(httpMethod);
    }


    public static boolean shouldContinueIfTenantInformationIsWrongOrMissing(final String path, final String httpMethod) {
        final boolean requiresTenantInfo = KillBillApiDefinition.requiresTenantInformation(path, httpMethod);
        return isMetricsRequest(path, httpMethod) || !requiresTenantInfo;
    }

    private void sendAuthError(final ServletResponse response, final String errorMessage) throws IOException {
        if (response instanceof HttpServletResponse) {
            final HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            httpServletResponse.sendError(401, errorMessage);
        }
    }
}
