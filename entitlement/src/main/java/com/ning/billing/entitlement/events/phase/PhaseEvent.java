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

package com.ning.billing.entitlement.events.phase;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.entitlement.events.EventBase;
import com.ning.billing.entitlement.events.IEventLyfecycle.IEventLyfecycleState;


public class PhaseEvent extends EventBase implements IPhaseEvent {

    private final String phaseName;

    public PhaseEvent(UUID subscriptionId, IPlanPhase phase, DateTime requestedDate,
            DateTime effectiveDate, DateTime processedDate, long activeVersion) {
        super(subscriptionId, requestedDate, effectiveDate, processedDate, activeVersion, true);
        this.phaseName = phase.getName();
    }


    public PhaseEvent(UUID id, UUID subscriptionId, String phaseName, DateTime requestedDate,
            DateTime effectiveDate, DateTime processedDate,
            long activeVersion, boolean isActiveVersion,
            UUID processingOwner, DateTime nextAvailableProcessingTime,
            IEventLyfecycleState processingState) {
        super(id, subscriptionId, requestedDate, effectiveDate, processedDate, activeVersion, isActiveVersion, processingOwner, nextAvailableProcessingTime, processingState);
        this.phaseName = phaseName;
    }


    @Override
    public EventType getType() {
        return EventType.PHASE;
    }

    @Override
    public String getPhase() {
        return phaseName;
    }

    @Override
    public String toString() {
        return "PhaseEvent [getId()= " + getId()
        		+ ", phaseName=" + phaseName
        		+ ", getType()=" + getType()
                + ", getPhase()=" + getPhase()
                + ", getRequestedDate()=" + getRequestedDate()
                + ", getEffectiveDate()=" + getEffectiveDate()
                + ", getActiveVersion()=" + getActiveVersion()
                + ", getProcessedDate()=" + getProcessedDate()
                + ", getSubscriptionId()=" + getSubscriptionId()
                + ", isActive()=" + isActive() + "]\n";
    }

}
