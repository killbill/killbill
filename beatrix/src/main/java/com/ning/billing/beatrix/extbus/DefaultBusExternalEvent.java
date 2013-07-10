package com.ning.billing.beatrix.extbus;

import java.util.UUID;

import com.ning.billing.ObjectType;
import com.ning.billing.bus.api.BusEvent;
import com.ning.billing.notification.plugin.api.ExtBusEvent;
import com.ning.billing.notification.plugin.api.ExtBusEventType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


public class DefaultBusExternalEvent implements ExtBusEvent, BusEvent {

    private final UUID objectId;
    private final UUID accountId;
    private final UUID tenantId;
    private final ObjectType objectType;
    private final ExtBusEventType eventType;
    private final Long searchKey1;
    private final Long searchKey2;
    private final UUID userToken;

    @JsonCreator
    public DefaultBusExternalEvent(@JsonProperty("objectId") final UUID objectId,
                                   @JsonProperty("objectType") final ObjectType objectType,
                                   @JsonProperty("eventType") final ExtBusEventType eventType,
                                   @JsonProperty("accountId") final UUID accountId,
                                   @JsonProperty("tenantId") final UUID tenantId,
                                   @JsonProperty("searchKey1") final Long searchKey1,
                                   @JsonProperty("searchKey2") final Long searchKey2,
                                   @JsonProperty("userToken") final UUID userToken) {
        this.eventType = eventType;
        this.objectType = objectType;
        this.objectId = objectId;
        this.accountId = accountId;
        this.tenantId = tenantId;
        this.searchKey1 = searchKey1;
        this.searchKey2 = searchKey2;
        this.userToken = userToken;
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
    public ExtBusEventType getEventType() {
        return eventType;
    }

    @Override
    public ObjectType getObjectType() {
        return objectType;
    }

    @JsonIgnore
    @Override
    public Long getSearchKey1() {
        return searchKey1;
    }

    @JsonIgnore
    @Override
    public Long getSearchKey2() {
        return searchKey2;
    }

    @JsonIgnore
    @Override
    public UUID getUserToken() {
        return userToken;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultBusExternalEvent)) {
            return false;
        }

        final DefaultBusExternalEvent that = (DefaultBusExternalEvent) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (eventType != that.eventType) {
            return false;
        }
        if (objectId != null ? !objectId.equals(that.objectId) : that.objectId != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }
        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = objectId != null ? objectId.hashCode() : 0;
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        result = 31 * result + objectType.hashCode();
        result = 31 * result + eventType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultBusExternalEvent{");
        sb.append("objectId=").append(objectId);
        sb.append(", accountId=").append(accountId);
        sb.append(", tenantId=").append(tenantId);
        sb.append(", objectType=").append(objectType);
        sb.append(", eventType=").append(eventType);
        sb.append('}');
        return sb.toString();
    }
}
