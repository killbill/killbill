/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.entitlement.api.user;

import java.util.Iterator;
import java.util.LinkedList;

import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.Clock;

public class SubscriptionTransitionDataIterator implements Iterator<SubscriptionTransitionData> {

    private final Clock clock;
    private final Iterator<SubscriptionTransitionData> it;
    private final Kind kind;
    private final TimeLimit timeLimit;
    private final Visibility visibility;

    private SubscriptionTransitionData next;

    public enum Order {
        ASC_FROM_PAST,
        DESC_FROM_FUTURE
    }

    public enum Kind {
        ENTITLEMENT,
        BILLING,
        ALL
    }

    public enum TimeLimit {
        FUTURE_ONLY,
        PAST_OR_PRESENT_ONLY,
        ALL
    }

    public enum Visibility {
        FROM_DISK_ONLY,
        ALL
    }

    public SubscriptionTransitionDataIterator(final Clock clock, final LinkedList<SubscriptionTransitionData> transitions,
                                              final Order order, final Kind kind, final Visibility visibility, final TimeLimit timeLimit) {
        this.it = (order == Order.DESC_FROM_FUTURE) ? transitions.descendingIterator() : transitions.iterator();
        this.clock = clock;
        this.kind = kind;
        this.timeLimit = timeLimit;
        this.visibility = visibility;
        this.next = null;
    }

    @Override
    public boolean hasNext() {
        do {
            final boolean hasNext = it.hasNext();
            if (!hasNext) {
                return false;
            }
            next = it.next();
        } while (shouldSkip(next));
        return true;
    }

    private boolean shouldSkip(final SubscriptionTransitionData input) {
        if (visibility == Visibility.FROM_DISK_ONLY && !input.isFromDisk()) {
            return true;
        }
        if ((kind == Kind.ENTITLEMENT && shouldSkipForEntitlementEvents(input)) ||
            (kind == Kind.BILLING && shouldSkipForBillingEvents(input))) {
            return true;
        }
        if ((timeLimit == TimeLimit.FUTURE_ONLY && !input.getEffectiveTransitionTime().isAfter(clock.getUTCNow())) ||
                ((timeLimit == TimeLimit.PAST_OR_PRESENT_ONLY && input.getEffectiveTransitionTime().isAfter(clock.getUTCNow())))) {
            return true;
        }
        return false;
    }

    private boolean shouldSkipForEntitlementEvents(final SubscriptionTransitionData input) {
        // Entitlement system knows about all events except for MIGRATE_BILLING
        return (input.getTransitionType() == SubscriptionTransitionType.MIGRATE_BILLING);
    }

    private boolean shouldSkipForBillingEvents(final SubscriptionTransitionData input) {
        // Junction system knows about all events except for MIGRATE_ENTITLEMENT and UNCANCEL-- which is a NO event as it undo
        // something that should have happened in the future.
        return (input.getTransitionType() == SubscriptionTransitionType.MIGRATE_ENTITLEMENT ||
                input.getTransitionType() == SubscriptionTransitionType.UNCANCEL);
    }


    @Override
    public SubscriptionTransitionData next() {
        return next;
    }

    @Override
    public void remove() {
        throw new EntitlementError("Operation SubscriptionTransitionDataIterator.remove not implemented");
    }
}
