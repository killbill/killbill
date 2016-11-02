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

import org.killbill.billing.callcontext.InternalTenantContext;
import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.Param;
import org.skife.config.TimeSpan;

public interface InvoiceConfig extends KillbillConfig {

    @Config("org.killbill.invoice.maxNumberOfMonthsInFuture")
    @Default("36")
    @Description("Maximum target date to consider when generating an invoice")
    int getNumberOfMonthsInFuture();

    @Config("org.killbill.invoice.maxNumberOfMonthsInFuture")
    @Default("36")
    @Description("Maximum target date to consider when generating an invoice")
    int getNumberOfMonthsInFuture(@Param("dummy") final InternalTenantContext tenantContext);

    @Config("org.killbill.invoice.dryRunNotificationSchedule")
    @Default("0s")
    @Description("DryRun invoice notification time before targetDate (ignored if set to 0s)")
    TimeSpan getDryRunNotificationSchedule();

    @Config("org.killbill.invoice.dryRunNotificationSchedule")
    @Default("0s")
    @Description("DryRun invoice notification time before targetDate (ignored if set to 0s)")
    TimeSpan getDryRunNotificationSchedule(@Param("dummy") final InternalTenantContext tenantContext);

    @Config("org.killbill.invoice.readMaxRawUsagePreviousPeriod")
    @Default("2")
    @Description("Maximum number of past billing periods we use to fetch raw usage data (usage optimization)")
    int getMaxRawUsagePreviousPeriod();

    @Config("org.killbill.invoice.readMaxRawUsagePreviousPeriod")
    @Default("2")
    @Description("Maximum number of past billing periods we use to fetch raw usage data (usage optimization)")
    int getMaxRawUsagePreviousPeriod(@Param("dummy") final InternalTenantContext tenantContext);

    @Config("org.killbill.invoice.globalLock.retries")
    @Default("50")
    @Description("Maximum number of times the system will retry to grab global lock (with a 100ms wait each time)")
    int getMaxGlobalLockRetries();

    @Config("org.killbill.invoice.emailNotificationsEnabled")
    @Default("false")
    @Description("Whether to send email notifications on invoice creation (for configured accounts)")
    boolean isEmailNotificationsEnabled();
}
