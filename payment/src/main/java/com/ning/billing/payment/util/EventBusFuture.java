package com.ning.billing.payment.util;

import javax.annotation.Nullable;

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractFuture;
import com.ning.billing.util.eventbus.IEventBus;
import com.ning.billing.util.eventbus.IEventBus.EventBusException;

public class EventBusFuture<T, V extends IEventBusResponseType<T>> extends AbstractFuture<V> {
    public static <V, W extends IEventBusRequestType<V>, X extends IEventBusResponseType<V>> EventBusFuture<V, X> post(final IEventBus eventBus, final W event) throws EventBusException {
        final EventBusFuture<V, X> responseFuture = new EventBusFuture<V, X>(eventBus, event.getId());

        eventBus.register(responseFuture);
        eventBus.post(event);
        return responseFuture;
    }

    private final IEventBus eventBus;
    private final T requestId;

    private EventBusFuture(IEventBus eventBus, T requestId) {
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
