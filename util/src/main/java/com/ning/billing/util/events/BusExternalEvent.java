package com.ning.billing.util.events;

import com.ning.billing.bus.BusPersistentEvent;
import com.ning.billing.notification.plugin.api.ExtBusEventType;

public interface BusExternalEvent extends BusPersistentEvent {

    public ExtBusEventType getBusEventType();
}
