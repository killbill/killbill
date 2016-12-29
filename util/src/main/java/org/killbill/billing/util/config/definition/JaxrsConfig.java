/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.util.config.definition;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.DefaultNull;
import org.skife.config.Description;
import org.skife.config.TimeSpan;

public interface JaxrsConfig extends KillbillConfig {

    @Config("org.killbill.jaxrs.threads.pool.nb")
    @Default("30")
    @Description("Number of threads for jaxrs executor")
    int getJaxrsThreadNb();

    @Config("org.killbill.jaxrs.timeout")
    @Default("30s")
    @Description("Total timeout for all callables associated to a given api call (parallel mode)")
    TimeSpan getJaxrsTimeout();

    @Config("org.killbill.jaxrs.location.full.url")
    @Default("true")
    @Description("Type of return for the jaxrs response location URL")
    boolean isJaxrsLocationFullUrl();

    @Config("org.killbill.jaxrs.location.useForwardHeaders")
    @Default("true")
    @Description("Whether to respect X-Forwarded headers for redirect URLs")
    boolean isJaxrsLocationUseForwardHeaders();

    @Config("org.killbill.jaxrs.location.host")
    @DefaultNull
    @Description("Base host address to use for redirect URLs")
    String getJaxrsLocationHost();
}
