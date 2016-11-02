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

package org.killbill.billing.subscription.events.bcd;

import org.killbill.billing.subscription.events.EventBaseBuilder;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;

public class BCDEventBuilder extends EventBaseBuilder<BCDEventBuilder> {

    private Integer billCycleDayLocal;

    public BCDEventBuilder() {
        super();
    }


    public BCDEventBuilder(final BCDEvent event) {
        super(event);
        this.billCycleDayLocal = event.getBillCycleDayLocal();
    }

    public BCDEventBuilder(final EventBaseBuilder<?> base) {
        super(base);
    }


    @Override
    public SubscriptionBaseEvent build() {
        return new BCDEventData(this);
    }

    public Integer getBillCycleDayLocal() {
        return billCycleDayLocal;
    }

    public BCDEventBuilder setBillCycleDayLocal(final Integer billCycleDayLocal) {
        this.billCycleDayLocal = billCycleDayLocal;
        return this;
    }
}
