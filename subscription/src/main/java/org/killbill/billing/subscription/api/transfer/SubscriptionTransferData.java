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

package org.killbill.billing.subscription.api.transfer;

import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;

public class SubscriptionTransferData {

    private final DefaultSubscriptionBase data;
    private final List<SubscriptionBaseEvent> initialEvents;

    public SubscriptionTransferData(final DefaultSubscriptionBase data,
                                    final List<SubscriptionBaseEvent> initialEvents,
                                    final DateTime ctd) {
        super();
        // Set CTD to subscription object
        final SubscriptionBuilder builder = new SubscriptionBuilder(data);
        if (ctd != null) {
            builder.setChargedThroughDate(ctd);
        }
        this.data = new DefaultSubscriptionBase(builder);
        this.initialEvents = initialEvents;
    }

    public DefaultSubscriptionBase getData() {
        return data;
    }

    public List<SubscriptionBaseEvent> getInitialEvents() {
        return initialEvents;
    }
}
