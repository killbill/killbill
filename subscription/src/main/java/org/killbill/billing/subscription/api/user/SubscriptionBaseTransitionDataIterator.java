/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.subscription.api.user;

import java.util.Iterator;
import java.util.LinkedList;

import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.clock.Clock;

public class SubscriptionBaseTransitionDataIterator implements Iterator<SubscriptionBaseTransition> {

    private final Clock clock;
    private final Iterator<SubscriptionBaseTransition> it;
    private final TimeLimit timeLimit;
    private final Visibility visibility;

    private SubscriptionBaseTransition next;

    public enum Order {
        ASC_FROM_PAST,
        DESC_FROM_FUTURE
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

    public SubscriptionBaseTransitionDataIterator(final Clock clock, final LinkedList<SubscriptionBaseTransition> transitions,
                                                  final Order order, final Visibility visibility, final TimeLimit timeLimit) {
        this.it = (order == Order.DESC_FROM_FUTURE) ? transitions.descendingIterator() : transitions.iterator();
        this.clock = clock;
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

    private boolean shouldSkip(final SubscriptionBaseTransition input) {
        if (visibility == Visibility.FROM_DISK_ONLY && ! ((SubscriptionBaseTransitionData) input).isFromDisk()) {
            return true;
        }
        if ((timeLimit == TimeLimit.FUTURE_ONLY && !input.getEffectiveTransitionTime().isAfter(clock.getUTCNow())) ||
                ((timeLimit == TimeLimit.PAST_OR_PRESENT_ONLY && input.getEffectiveTransitionTime().isAfter(clock.getUTCNow())))) {
            return true;
        }
        return false;
    }

    @Override
    public SubscriptionBaseTransition next() {
        return next;
    }

    @Override
    public void remove() {
        throw new SubscriptionBaseError("Operation SubscriptionBaseTransitionDataIterator.remove not implemented");
    }
}
