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

package org.killbill.billing.subscription.alignment;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.PlanPhase;

public final class TimedPhase {
    private final PlanPhase phase;
    private final DateTime startPhase;

    public TimedPhase(final PlanPhase phase, final DateTime startPhase) {
        this.phase = phase;
        this.startPhase = startPhase;
    }

    public PlanPhase getPhase() {
        return phase;
    }

    public DateTime getStartPhase() {
        return startPhase;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TimedPhase");
        sb.append("{phase=").append(phase);
        sb.append(", startPhase=").append(startPhase);
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

        final TimedPhase phase1 = (TimedPhase) o;

        if (phase != null ? !phase.equals(phase1.phase) : phase1.phase != null) {
            return false;
        }
        if (startPhase != null ? !startPhase.equals(phase1.startPhase) : phase1.startPhase != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = phase != null ? phase.hashCode() : 0;
        result = 31 * result + (startPhase != null ? startPhase.hashCode() : 0);
        return result;
    }
}

