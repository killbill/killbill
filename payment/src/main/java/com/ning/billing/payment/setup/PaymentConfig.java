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

import org.skife.config.Config;
import org.skife.config.Default;

import com.ning.billing.util.notificationq.NotificationConfig;

public interface PaymentConfig extends NotificationConfig {
    @Config("killbill.payment.provider.default")
    @Default("noop")
    public String getDefaultPaymentProvider();

    @Config("killbill.payment.dao.claim.time")
    @Default("60000")
    public long getDaoClaimTimeMs();

    @Config("killbill.payment.dao.ready.max")
    @Default("10")
    public int getDaoMaxReadyEvents();

    @Config("killbill.payment.engine.notifications.sleep")
    @Default("500")
    public long getNotificationSleepTimeMs();

    @Config("killbill.payment.engine.events.off")
    @Default("false")
    public boolean isNotificationProcessingOff();
}
