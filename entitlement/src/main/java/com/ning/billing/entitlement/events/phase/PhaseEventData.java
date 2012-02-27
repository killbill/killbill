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


import com.ning.billing.entitlement.alignment.TimedPhase;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.events.EventBase;
import org.joda.time.DateTime;


public class PhaseEventData extends EventBase implements PhaseEvent {

    private final String phaseName;

    public PhaseEventData(PhaseEventBuilder builder) {
        super(builder);
        this.phaseName = builder.getPhaseName();
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

    public static final PhaseEvent createNextPhaseEvent(String phaseName, SubscriptionData subscription, DateTime now, DateTime effectiveDate) {
        return (phaseName == null) ?
                null :
                    new PhaseEventData(new PhaseEventBuilder()
                        .setSubscriptionId(subscription.getId())
                        .setRequestedDate(now)
                        .setEffectiveDate(effectiveDate)
                        .setProcessedDate(now)
                        .setActiveVersion(subscription.getActiveVersion())
                        .setPhaseName(phaseName));
    }
}
