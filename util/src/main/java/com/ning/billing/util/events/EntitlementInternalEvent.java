package com.ning.billing.util.events;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.entitlement.EntitlementTransitionType;

public interface EntitlementInternalEvent extends BusInternalEvent {

    UUID getId();

    UUID getBundleId();

    UUID getEntitlementId();

    UUID getAccountId();

    EntitlementTransitionType getTransitionType();

    DateTime getRequestedTransitionTime();

    DateTime getEffectiveTransitionTime();
}
