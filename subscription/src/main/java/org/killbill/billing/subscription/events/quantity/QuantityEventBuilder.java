/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.subscription.events.quantity;

import org.killbill.billing.subscription.events.EventBaseBuilder;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;

public class QuantityEventBuilder extends EventBaseBuilder<QuantityEventBuilder> {

    private Integer quantity;

    public QuantityEventBuilder() {
        super();
    }


    public QuantityEventBuilder(final QuantityEvent event) {
        super(event);
        this.quantity = event.getQuantity();
    }

    public QuantityEventBuilder(final EventBaseBuilder<?> base) {
        super(base);
    }


    @Override
    public SubscriptionBaseEvent build() {
        return new QuantityEventData(this);
    }

    public Integer getQuantity() {
        return quantity;
    }

    public QuantityEventBuilder setQuantity(final Integer quantity) {
        this.quantity = quantity;
        return this;
    }
}
