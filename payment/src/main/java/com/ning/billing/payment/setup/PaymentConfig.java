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

package com.ning.billing.payment.setup;

import java.util.List;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.DefaultNull;
import org.skife.config.TimeSpan;

public interface PaymentConfig {
    @Config("killbill.payment.provider.default")
    @DefaultNull
    public String getDefaultPaymentProvider();

    @Config("killbill.payment.retry.days")
    @DefaultNull
    public List<String> getPaymentRetryDays();

    @Config("killbill.payment.retry.pause")
    // payment retry job is off by default
    @DefaultNull
    TimeSpan getPaymentRetrySchedulePause();

    @Config("killbill.payment.retry.claim.timeout")
    // if payment retry job is on, then retry abandoned payment attempts after some period of time
    @Default("1h")
    TimeSpan getPaymentRetryClaimTimeout();

}
