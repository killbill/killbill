package com.ning.billing.payment;

import com.ning.billing.util.eventbus.IEventBusType;

public interface IEventBusResponseType<T> extends IEventBusType {
    T getRequestId();
}
