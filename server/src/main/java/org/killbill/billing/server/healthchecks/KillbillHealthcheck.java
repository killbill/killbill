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

package org.killbill.billing.server.healthchecks;

import org.weakref.jmx.Managed;

import com.codahale.metrics.health.HealthCheck;

public class KillbillHealthcheck extends HealthCheck {

    @Override
    public Result check() {
        try {
            // STEPH obviously needs more than that
            return Result.healthy();
        } catch (final Exception e) {
            return Result.unhealthy(e);
        }
    }

    @Managed(description = "Basic killbill healthcheck")
    public boolean isHealthy() {
        return check().isHealthy();
    }
}
