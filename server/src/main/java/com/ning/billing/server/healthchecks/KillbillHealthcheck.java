/*
 * Copyright 2010-2011 Ning, Inc.
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
package com.ning.billing.server.healthchecks;

import com.yammer.metrics.core.HealthCheck;
import org.weakref.jmx.Managed;

public class KillbillHealthcheck extends HealthCheck
{
    public KillbillHealthcheck()
    {
        super(KillbillHealthcheck.class.getSimpleName());
    }

    @Override
    public Result check()
    {
        try {
            // STEPH obviously needs more than that
            return Result.healthy();
        }
        catch (Exception e) {
            return Result.unhealthy(e);
        }
    }

    @Managed(description = "Basic killbill healthcheck")
    public boolean isHealthy()
    {
        return check().isHealthy();
    }
}
