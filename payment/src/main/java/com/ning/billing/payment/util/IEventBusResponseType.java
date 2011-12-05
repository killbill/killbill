package com.ning.billing.payment.util;

import com.ning.billing.util.eventbus.IEventBusType;

public interface IEventBusResponseType<T> extends IEventBusType {
    T getRequestId();
}
