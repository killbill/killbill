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

package com.ning.billing.entitlement.alignment;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.IDuration;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.PlanAlignment;
import com.ning.billing.entitlement.events.phase.IPhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.Clock;

public class EntitlementAlignment {

    private final UUID subscriptionId;
    private final DateTime bundleStart;
    private final IPlan plan;
    private final DateTime effectiveDate;
    private final long activeVersion;
    private final DateTime now;
    private final List<TimedPhase> timedPhases;

    public final static class TimedPhase {

        private final IPlanPhase phase;
        private final DateTime startPhase;

        public TimedPhase(IPlanPhase phase, DateTime startPhase) {
            super();
            this.phase = phase;
            this.startPhase = startPhase;
        }

        public IPlanPhase getPhase() {
            return phase;
        }

        public DateTime getStartPhase() {
            return startPhase;
        }
    }

    public EntitlementAlignment(DateTime bundleStart, IPlan plan, DateTime effectiveDate) {
        this(null, null, bundleStart, plan, effectiveDate, 0);
    }

    public EntitlementAlignment(UUID subscriptionId, DateTime now, DateTime bundleStart, IPlan plan,
            DateTime effectiveDate, long activeVersion) {
        super();
        this.subscriptionId = subscriptionId;
        this.now = now;
        this.bundleStart = bundleStart;
        this.plan = plan;
        this.effectiveDate = effectiveDate;
        this.activeVersion = activeVersion;
        this.timedPhases = buildPhaseAlignment();
    }

    public List<TimedPhase> getTimedPhases() {
        return timedPhases;
    }

    public TimedPhase getCurrentTimedPhase() {
        return getCurOrNextTimedPhase(true);
    }

    public TimedPhase getNextTimedPhase() {
        return getCurOrNextTimedPhase(false);
    }

    public IPhaseEvent getNextPhaseEvent() {

        if (subscriptionId == null || now == null || activeVersion == 0) {
            throw new EntitlementError("Missing required arguments for creating next phase event");
        }
        TimedPhase nextPhase = getNextTimedPhase();
        IPhaseEvent nextPhaseEvent = (nextPhase == null) ?
                null :
                    new PhaseEvent(subscriptionId, nextPhase.getPhase(), now, nextPhase.getStartPhase(),
                            now,  activeVersion);
        return nextPhaseEvent;
    }

    private TimedPhase getCurOrNextTimedPhase(boolean returnCurrent) {
        TimedPhase cur = null;
        TimedPhase next = null;
        for (TimedPhase phase : timedPhases) {
            if (phase.getStartPhase().isAfter(effectiveDate)) {
                next = phase;
                break;
            }
            cur = phase;
        }
        return (returnCurrent) ? cur : next;
    }


    private List<TimedPhase> buildPhaseAlignment() {

        List<TimedPhase> result = new LinkedList<EntitlementAlignment.TimedPhase>();

        DateTime curPhaseStart = (plan.getPlanAlignment() == PlanAlignment.START_OF_SUBSCRIPTION) ?
                effectiveDate :  bundleStart;
        if (plan.getInitialPhases() == null) {
            result.add(new TimedPhase(plan.getFinalPhase(), curPhaseStart));
            return result;
        }

        DateTime nextPhaseStart = null;
        for (IPlanPhase cur : plan.getInitialPhases()) {

            result.add(new TimedPhase(cur, curPhaseStart));

            IDuration curPhaseDuration = cur.getDuration();
            nextPhaseStart = Clock.addDuration(curPhaseStart, curPhaseDuration);
            if (nextPhaseStart == null) {
                throw new EntitlementError(String.format("Unexpected non ending UNLIMITED phase for plan %s",
                        plan.getName()));
            }
            curPhaseStart = nextPhaseStart;
        }
        result.add(new TimedPhase(plan.getFinalPhase(), nextPhaseStart));
        return result;
    }


}
