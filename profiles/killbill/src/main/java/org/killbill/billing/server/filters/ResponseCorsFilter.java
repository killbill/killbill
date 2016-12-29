/*
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

package org.killbill.billing.server.filters;

import java.io.IOException;

import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.killbill.billing.jaxrs.resources.JaxrsResource;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;

@Singleton
public class ResponseCorsFilter implements Filter {

    private final String allowedHeaders;

    public ResponseCorsFilter() {
        allowedHeaders = Joiner.on(",").join(ImmutableList.<String>of(HttpHeaders.AUTHORIZATION,
                                                                      HttpHeaders.CONTENT_TYPE,
                                                                      HttpHeaders.LOCATION,
                                                                      JaxrsResource.HDR_API_KEY,
                                                                      JaxrsResource.HDR_API_SECRET,
                                                                      JaxrsResource.HDR_COMMENT,
                                                                      JaxrsResource.HDR_CREATED_BY,
                                                                      JaxrsResource.HDR_PAGINATION_CURRENT_OFFSET,
                                                                      JaxrsResource.HDR_PAGINATION_MAX_NB_RECORDS,
                                                                      JaxrsResource.HDR_PAGINATION_NEXT_OFFSET,
                                                                      JaxrsResource.HDR_PAGINATION_NEXT_PAGE_URI,
                                                                      JaxrsResource.HDR_PAGINATION_TOTAL_NB_RECORDS,
                                                                      JaxrsResource.HDR_REASON));
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        final HttpServletResponse res = (HttpServletResponse) response;
        final HttpServletRequest req = (HttpServletRequest) request;

        final String origin = MoreObjects.firstNonNull(req.getHeader(HttpHeaders.ORIGIN), "*");
        res.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        res.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, DELETE, PUT, OPTIONS");
        res.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, allowedHeaders);
        res.addHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, allowedHeaders);
        res.addHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
