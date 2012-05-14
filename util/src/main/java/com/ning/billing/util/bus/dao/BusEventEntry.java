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
package com.ning.billing.util.bus.dao;

import org.joda.time.DateTime;

import com.ning.billing.util.notificationq.NotificationLifecycle;

public class BusEventEntry implements NotificationLifecycle  {
    
    private final long id;
    private final String owner;
    private final String createdOwner;
    private final DateTime nextAvailable;
    private final NotificationLifecycleState processingState;
    private final String busEventClass;
    private final String busEventJson;

    public BusEventEntry(final long id, final String createdOwner, final String owner, final DateTime nextAvailable, NotificationLifecycleState processingState, final String busEventClass, final String busEventJson) {
        this.id = id;
        this.createdOwner = createdOwner;
        this.owner = owner;
        this.nextAvailable = nextAvailable;
        this.processingState = processingState;
        this.busEventClass = busEventClass;
        this.busEventJson = busEventJson;
    }

    public BusEventEntry(final String createdOwner, final String busEventClass, final String busEventJson) {
        this(0, createdOwner, null, null, null, busEventClass, busEventJson);
    }

    

    public long getId() {
        return id;
    }

    public String getBusEventClass() {
        return busEventClass;
    }

    public String getBusEventJson() {
        return busEventJson;
    }

    @Override
    public String getOwner() {
        return owner;
    }

    @Override
    public String getCreatedOwner() {
        return createdOwner;
    }

    @Override
    public DateTime getNextAvailableDate() {
        return nextAvailable;
    }

    @Override
    public NotificationLifecycleState getProcessingState() {
        return processingState;
    }

    @Override
    public boolean isAvailableForProcessing(DateTime now) {
        switch(processingState) {
        case AVAILABLE:
            break;
        case IN_PROCESSING:
            // Somebody already got the event, not available yet
            if (nextAvailable.isAfter(now)) {
                return false;
            }
            break;
        case PROCESSED:
            return false;
        default:
            throw new RuntimeException(String.format("Unkwnon IEvent processing state %s", processingState));
        }
        return true;
    }
}
