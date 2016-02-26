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

import java.util.LinkedList;
import java.util.List;

import org.killbill.billing.subscription.events.SubscriptionBaseEvent;


public class SubscriptionEvents {


    private final List<SubscriptionBaseEvent> events;

    public SubscriptionEvents() {
        this.events = new LinkedList<SubscriptionBaseEvent>();
    }

    public void addEvent(final SubscriptionBaseEvent ev) {
        events.add(ev);
    }

    public List<SubscriptionBaseEvent> getViewForVersion(final long version) {
        final LinkedList<SubscriptionBaseEvent> result = new LinkedList<SubscriptionBaseEvent>();
        for (final SubscriptionBaseEvent cur : events) {
            result.add(cur);
        }

        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SubscriptionEvents");
        sb.append(", events=").append(events);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SubscriptionEvents that = (SubscriptionEvents) o;

        if (events != null ? !events.equals(that.events) : that.events != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = events != null ? events.hashCode() : 0;
        return result;
    }
}
