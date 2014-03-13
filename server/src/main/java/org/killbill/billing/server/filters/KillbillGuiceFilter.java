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

package org.killbill.billing.server.filters;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

import org.killbill.billing.server.updatechecker.UpdateChecker;
import org.skife.config.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Injector;
import com.google.inject.servlet.GuiceFilter;

public class KillbillGuiceFilter extends GuiceFilter {

    private static final Logger log = LoggerFactory.getLogger(KillbillGuiceFilter.class);

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);

        // At this point, Kill Bill server is fully initialized
        log.info("Kill Bill server has started");

        // The magic happens in KillbillGuiceListener
        final Injector injector = (Injector) filterConfig.getServletContext().getAttribute(Injector.class.getName());
        final ConfigSource configSource = injector.getInstance(ConfigSource.class);
        final UpdateChecker checker = new UpdateChecker(configSource);
        checker.check(filterConfig.getServletContext());
    }
}
