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

import com.ning.billing.entitlement.events.IEvent;

public class SubscriptionEvents {

    public static final long INITIAL_VERSION = 1;

    private final UUID subscriptionId;
    private final LinkedList<IEvent> events;

    private long activeVersion;

    public SubscriptionEvents(UUID subscriptionId) {
        super();
        this.subscriptionId = subscriptionId;
        this.events = new LinkedList<IEvent>();
        this.activeVersion = INITIAL_VERSION;
    }

    public void addEvent(IEvent ev) {
        events.add(ev);
    }

    public LinkedList<IEvent> getCurrentView() {
        return getViewForVersion(activeVersion);
    }

    public LinkedList<IEvent> getViewForVersion(final long version) {

        LinkedList<IEvent> result = new LinkedList<IEvent>();
        for (IEvent cur : events) {
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
