/*
 * Copyright 2024 PayTheFly
 * Copyright 2024 The Billing Project, LLC
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

package org.killbill.billing.plugin.paythefly;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jooby.Result;
import org.jooby.Results;
import org.jooby.mvc.GET;
import org.jooby.mvc.Path;
import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.osgi.api.Healthcheck.HealthStatus;
import org.killbill.billing.tenant.api.Tenant;

/**
 * Healthcheck servlet exposed at {@code /plugins/killbill-paythefly/healthcheck}.
 */
@Singleton
@Path("/healthcheck")
public class PayTheFlyHealthcheckServlet {

    private final PayTheFlyHealthcheck healthcheck;

    @Inject
    public PayTheFlyHealthcheckServlet(final PayTheFlyHealthcheck healthcheck) {
        this.healthcheck = healthcheck;
    }

    @GET
    public Result check(@Named("killbill_tenant") final Tenant tenant) {
        final HealthStatus status = healthcheck.getHealthStatus(tenant, null);
        return status.isHealthy()
                ? Results.ok(status.getDetails())
                : Results.with(status.getDetails(), 503);
    }
}
