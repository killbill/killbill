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

package com.ning.billing.subscription.api.timeline;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.util.entity.Entity;

/**
 * The interface {@code} shows a view of all the events for a particular {@code SubscriptionBase}.
 * <p/>
 * It can be used to display information, or it can be used to modify the subscription stream of events
 * and 'repair' the stream by versioning the events.
 */
public interface SubscriptionBaseTimeline extends Entity {

    /**
     * @return the list of events that should be deleted when repairing the stream.
     */
    public List<DeletedEvent> getDeletedEvents();

    /**
     * @return the list of events that should be added when repairing the stream
     */
    public List<NewEvent> getNewEvents();

    /**
     * @return the current list of events for that {@code SubscriptionBase}
     */
    public List<ExistingEvent> getExistingEvents();

    /**
     * @return the active version for the event stream
     */
    public long getActiveVersion();


    public interface DeletedEvent {

        /**
         * @return the unique if for the event to delete
         */
        public UUID getEventId();
    }

    public interface NewEvent {

        /**
         * @return the description for the event to be added
         */
        public PlanPhaseSpecifier getPlanPhaseSpecifier();

        /**
         * @return the date at which this event should be inserted into the stream
         */
        public DateTime getRequestedDate();

        /**
         * @return the {@code SubscriptionBaseTransitionType} for the event
         */
        public SubscriptionBaseTransitionType getSubscriptionTransitionType();

    }

    public interface ExistingEvent extends DeletedEvent, NewEvent {

        /**
         * @return the date at which this event was effective
         */
        public DateTime getEffectiveDate();

        /**
         * @return the name of the phase
         */
        public String getPlanPhaseName();
    }
}
