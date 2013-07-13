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

package com.ning.billing.subscription.api.user;

import java.util.LinkedList;
import java.util.List;

import com.ning.billing.subscription.events.SubscriptionEvent;


public class SubscriptionEvents {
    public static final long INITIAL_VERSION = 1;

    private final List<SubscriptionEvent> events;

    private long activeVersion;

    public SubscriptionEvents() {
        this.events = new LinkedList<SubscriptionEvent>();
        this.activeVersion = INITIAL_VERSION;
    }

    public void addEvent(final SubscriptionEvent ev) {
        events.add(ev);
    }

    public List<SubscriptionEvent> getCurrentView() {
        return getViewForVersion(activeVersion);
    }

    public List<SubscriptionEvent> getViewForVersion(final long version) {
        final LinkedList<SubscriptionEvent> result = new LinkedList<SubscriptionEvent>();
        for (final SubscriptionEvent cur : events) {
            if (cur.getActiveVersion() == version) {
                result.add(cur);
            }
        }

        return result;
    }

    public long getActiveVersion() {
        return activeVersion;
    }

    public void setActiveVersion(final long activeVersion) {
        this.activeVersion = activeVersion;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SubscriptionEvents");
        sb.append("{activeVersion=").append(activeVersion);
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

        if (activeVersion != that.activeVersion) {
            return false;
        }
        if (events != null ? !events.equals(that.events) : that.events != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = events != null ? events.hashCode() : 0;
        result = 31 * result + (int) (activeVersion ^ (activeVersion >>> 32));
        return result;
    }
}
