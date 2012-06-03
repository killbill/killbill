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

package com.ning.billing.config;

import java.util.List;

import org.skife.config.Config;
import org.skife.config.Default;


public interface PaymentConfig extends NotificationConfig, KillbillConfig  {
	

    @Config("killbill.payment.provider.default")
    @Default("noop")
    public String getDefaultPaymentProvider();


    @Config("killbill.payment.retry.days")
    @Default("8,8,8")
    public List<Integer> getPaymentRetryDays();

    @Config("killbill.payment.failure.retry.start.sec")
    @Default("300")
    public int getPaymentFailureRetryStart();

    @Config("killbill.payment.failure.retry.multiplier")
    @Default("2")
    public int getPaymentFailureRetryMultiplier();

    @Config("killbill.payment.failure.retry.max.attempts")
    @Default("8")
    public int getPaymentFailureRetryMaxAttempts();

	@Override
    @Config("killbill.payment.engine.notifications.sleep")
    @Default("500")
    public long getSleepTimeMs();

	@Override
    @Config("killbill.payment.engine.events.off")
    @Default("false")
    public boolean isNotificationProcessingOff();

}
