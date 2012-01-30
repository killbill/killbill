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

package com.ning.billing.payment.util;

import javax.annotation.Nullable;

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractFuture;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;

public class EventBusFuture<T, V extends EventBusResponse<T>> extends AbstractFuture<V> {
    public static <V, W extends EventBusRequest<V>, X extends EventBusResponse<V>> EventBusFuture<V, X> post(final Bus eventBus, final W event) throws EventBusException {
        final EventBusFuture<V, X> responseFuture = new EventBusFuture<V, X>(eventBus, event.getId());

        eventBus.register(responseFuture);
        eventBus.post(event);
        return responseFuture;
    }

    private final Bus eventBus;
    private final T requestId;

    private EventBusFuture(Bus eventBus, T requestId) {
        this.eventBus = eventBus;
        this.requestId = requestId;
    }

    @Subscribe
    public void handleResponse(V response) {
        if (requestId.equals(response.getRequestId())) {
            set(response);
        }
    }

    @Override
    public boolean set(@Nullable V value) {
        boolean result = super.set(value);

        try {
            eventBus.unregister(this);
        }
        catch (EventBusException ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }

    @Override
    public boolean setException(Throwable throwable) {
        boolean result = super.setException(throwable);

        try {
            eventBus.unregister(this);
        }
        catch (EventBusException ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }
}
