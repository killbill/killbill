package com.ning.billing.mock.api;

import java.util.UUID;

import com.ning.billing.ObjectType;
import com.ning.billing.beatrix.bus.api.ExtBusEvent;
import com.ning.billing.beatrix.bus.api.ExtBusEventType;

/**
 * Used for Jruby plugin that import util test package for default implementation of interfaces in api.
 * So despite the appearences, this class is used.
 */
public class MockExtBusEvent implements ExtBusEvent {

    private final ExtBusEventType eventType;
    private final ObjectType objectType;
    private final UUID objectId;
    private final UUID accountId;
    private final UUID tenantId;


    public MockExtBusEvent(final ExtBusEventType eventType,
                           final ObjectType objectType,
                           final UUID objectId,
                           final UUID accountId,
                           final UUID tenantId) {
        this.eventType = eventType;
        this.objectId = objectId;
        this.objectType = objectType;
        this.accountId = accountId;
        this.tenantId = tenantId;
    }

    @Override
    public ExtBusEventType getEventType() {
        return eventType;
    }

    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    @Override
    public UUID getObjectId() {
        return objectId;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getTenantId() {
        return tenantId;
    }

    @Override
    public String toString() {
        return "MockExtBusEvent [eventType=" + eventType + ", objectType="
               + objectType + ", objectId=" + objectId + ", accountId="
               + accountId + ", tenantId=" + tenantId + "]";
    }

}
