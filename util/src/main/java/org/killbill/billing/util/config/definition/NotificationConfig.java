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

import java.util.List;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.Param;
import org.skife.config.TimeSpan;

public interface NotificationConfig extends KillbillConfig {

    @Config("org.killbill.billing.server.notifications.retries")
    @Default("15m,30m,2h,12h,1d")
    @Description("Delay before which unresolved push notifications should be retried")
    List<TimeSpan> getPushNotificationsRetries();

    @Config("org.killbill.billing.server.notifications.retries")
    @Default("15m,30m,2h,12h,1d")
    @Description("Delay before which unresolved push notifications should be retried")
    List<TimeSpan> getPushNotificationsRetries(@Param("dummy") final InternalTenantContext tenantContext);

}