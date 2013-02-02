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

package com.ning.billing.server.security;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.tenant.api.Tenant;
import com.ning.billing.tenant.api.TenantApiException;
import com.ning.billing.tenant.api.TenantUserApi;

@Singleton
public class TenantFilter implements Filter {

    public static final String SUBJECT = "killbill_subject";
    public static final String TENANT = "killbill_tenant";

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    @Inject
    private TenantUserApi tenantUserApi;

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        final Subject subject = SecurityUtils.getSubject();
        request.setAttribute(SUBJECT, subject);

        final String apiKey = (String) subject.getPrincipal();
        if (apiKey == null) {
            // Resource not protected by Shiro?
            chain.doFilter(request, response);
            return;
        }

        try {
            final Tenant tenant = tenantUserApi.getTenantByApiKey(apiKey);
            request.setAttribute(TENANT, tenant);

            chain.doFilter(request, response);
        } catch (TenantApiException e) {
            // Should never happen since Shiro validated the credentials?
            log.warn("Couldn't find the tenant?", e);
        }
    }

    @Override
    public void destroy() {
    }
}
