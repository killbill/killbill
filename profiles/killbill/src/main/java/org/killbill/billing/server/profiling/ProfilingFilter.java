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

package org.killbill.billing.server.profiling;

import java.io.IOException;

import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.ProfilingData.ProfilingDataOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ProfilingFilter implements Filter {

    private final static Logger logger = LoggerFactory.getLogger(ProfilingFilter.class);

    private final static String PROFILING_HEADER_REQ = "X-Killbill-Profiling-Req";
    private final static String PROFILING_HEADER_RESP = "X-Killbill-Profiling-Resp";

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain) throws IOException, ServletException {

        final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        final String profilingHeaderRequest = httpServletRequest.getHeader(PROFILING_HEADER_REQ);
        if (profilingHeaderRequest != null) {
            try {
                final ProfilingDataOutput profilingOutput = ProfilingDataOutput.valueOf(profilingHeaderRequest);
                Profiling.setPerThreadProfilingData(profilingOutput);
            } catch (IllegalArgumentException e) {
                logger.info("Profiling data output " + profilingHeaderRequest + " is not supported, profiling NOT enabled");
            }
        }
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            Profiling.resetPerThreadProfilingData();
        }
    }

    @Override
    public void destroy() {

    }
}
