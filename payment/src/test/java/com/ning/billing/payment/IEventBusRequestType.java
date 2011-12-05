package com.ning.billing.payment;

import com.ning.billing.util.eventbus.IEventBusType;

public interface IEventBusRequestType<T> extends IEventBusType {
    T getId();
}
