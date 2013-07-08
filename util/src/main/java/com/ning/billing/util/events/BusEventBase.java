package com.ning.billing.util.events;

import java.util.UUID;

import com.ning.billing.bus.api.BusEvent;

public class BusEventBase implements BusEvent {

    private final Long searchKey1;
    private final Long searchKey2;
    private final UUID userToken;

    public BusEventBase(final Long searchKey1,
                        final Long searchKey2,
                        final UUID userToken) {
        this.searchKey1 = searchKey1;
        this.searchKey2 = searchKey2;
        this.userToken = userToken;
    }

    @Override
    public Long getSearchKey1() {
        return searchKey1;
    }

    @Override
    public Long getSearchKey2() {
        return searchKey2;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }
}
