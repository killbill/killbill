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

import java.util.LinkedList;
import java.util.UUID;

import com.ning.billing.entitlement.events.EntitlementEvent;

public class SubscriptionEvents {

    public static final long INITIAL_VERSION = 1;

    private final UUID subscriptionId;
    private final LinkedList<EntitlementEvent> events;

    private long activeVersion;

    public SubscriptionEvents(UUID subscriptionId) {
        super();
        this.subscriptionId = subscriptionId;
        this.events = new LinkedList<EntitlementEvent>();
        this.activeVersion = INITIAL_VERSION;
    }

    public void addEvent(EntitlementEvent ev) {
        events.add(ev);
    }

    public LinkedList<EntitlementEvent> getCurrentView() {
        return getViewForVersion(activeVersion);
    }

    public LinkedList<EntitlementEvent> getViewForVersion(final long version) {

        LinkedList<EntitlementEvent> result = new LinkedList<EntitlementEvent>();
        for (EntitlementEvent cur : events) {
            if (cur.getActiveVersion() == version) {
                result.add(cur);
            }
        }
        return result;
    }


    public long getActiveVersion() {
        return activeVersion;
    }

    public void setActiveVersion(long activeVersion) {
        this.activeVersion = activeVersion;
    }
}
