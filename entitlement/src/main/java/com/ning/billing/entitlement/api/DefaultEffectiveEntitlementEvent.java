package com.ning.billing.entitlement.api;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.entitlement.EntitlementTransitionType;
import com.ning.billing.util.events.BusEventBase;
import com.ning.billing.util.events.EffectiveEntitlementInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultEffectiveEntitlementEvent extends BusEventBase implements EffectiveEntitlementInternalEvent {

    private final UUID id;
    private final UUID entitlementId;
    private final UUID bundleId;
    private final UUID accountId;
    private final EntitlementTransitionType transitionType;
    private final DateTime effectiveTransitionTime;
    private final DateTime requestedTransitionTime;


    @JsonCreator
    public DefaultEffectiveEntitlementEvent(@JsonProperty("eventId") final UUID id,
                                            @JsonProperty("entitlementId") final UUID entitlementId,
                                            @JsonProperty("bundleId") final UUID bundleId,
                                            @JsonProperty("accountId") final UUID accountId,
                                            @JsonProperty("transitionType") final EntitlementTransitionType transitionType,
                                            @JsonProperty("effectiveTransitionTime") final DateTime effectiveTransitionTime,
                                            @JsonProperty("requestedTransitionTime") final DateTime requestedTransitionTime,
                                            @JsonProperty("searchKey1") final Long searchKey1,
                                            @JsonProperty("searchKey2") final Long searchKey2,
                                            @JsonProperty("userToken") final UUID userToken) {

        super(searchKey1, searchKey2, userToken);
        this.id = id;
        this.entitlementId = entitlementId;
        this.bundleId = bundleId;
        this.accountId = accountId;
        this.transitionType = transitionType;
        this.effectiveTransitionTime = effectiveTransitionTime;
        this.requestedTransitionTime = requestedTransitionTime;
    }

    @JsonProperty("eventId")
    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getBundleId() {
        return bundleId;
    }

    @Override
    public UUID getEntitlementId() {
        return entitlementId;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public EntitlementTransitionType getTransitionType() {
        return transitionType;
    }

    @Override
    public DateTime getRequestedTransitionTime() {
        return requestedTransitionTime;
    }

    @Override
    public DateTime getEffectiveTransitionTime() {
        return effectiveTransitionTime;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.ENTITLEMENT_TRANSITION;
    }
}
