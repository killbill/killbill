/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.config;

import java.util.List;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.TimeSpan;

public interface PaymentConfig extends KillbillConfig {

    @Config("org.killbill.payment.provider.default")
    // See ExternalPaymentProviderPlugin.PLUGIN_NAME
    @Default("__external_payment__")
    @Description("Default payment provider to use")
    public String getDefaultPaymentProvider();

    @Config("org.killbill.payment.retry.days")
    @Default("8,8,8")
    @Description("Interval in days between payment retries")
    public List<Integer> getPaymentRetryDays();

    @Config("orgkillbill.payment.failure.retry.start.sec")
    @Default("300")
    public int getPluginFailureRetryStart();

    @Config("org.killbill.payment.failure.retry.multiplier")
    @Default("2")
    public int getPluginFailureRetryMultiplier();

    @Config("org.killbill.payment.failure.retry.max.attempts")
    @Default("8")
    @Description("Maximum number of retries for failed payments")
    public int getPluginFailureRetryMaxAttempts();

    @Config("org.killbill.payment.plugin.timeout")
    @Default("90s")
    @Description("Timeout for each payment attempt")
    public TimeSpan getPaymentPluginTimeout();

    @Config("org.killbill.payment.plugin.threads.nb")
    @Default("10")
    @Description("Number of threads for plugin executor dispatcher")
    public int getPaymentPluginThreadNb();

    @Config("org.killbill.payment.off")
    @Default("false")
    @Description("Whether the payment subsystem is off")
    public boolean isPaymentOff();
}
