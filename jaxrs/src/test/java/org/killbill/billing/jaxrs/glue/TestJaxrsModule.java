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

package org.killbill.billing.jaxrs.glue;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.glue.KillBillModule;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.killbill.commons.metrics.api.MetricRegistry;
import org.killbill.commons.metrics.impl.NoOpMetricRegistry;

public class TestJaxrsModule extends KillBillModule {

    public TestJaxrsModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    private void installObjectMapper() {
        bind(com.fasterxml.jackson.databind.ObjectMapper.class).toInstance(new ObjectMapper());
    }

    @Override
    protected void configure() {
        installObjectMapper();

        bind(MetricRegistry.class).to(NoOpMetricRegistry.class).asEagerSingleton();
    }
}
