package org.killbill.billing.catalog;

import org.killbill.billing.catalog.api.FixedType;

public class MockFixed extends DefaultFixed {

    public MockFixed(final DefaultInternationalPrice price) {
        setType(FixedType.ONE_TIME);
        setFixedPrice(price);
    }
}
