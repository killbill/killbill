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

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.PlanPhase;


public final class TimedPhase {

    private final PlanPhase phase;
    private final DateTime startPhase;

    public TimedPhase(final PlanPhase phase, final DateTime startPhase) {
        super();
        this.phase = phase;
        this.startPhase = startPhase;
    }

    public PlanPhase getPhase() {
        return phase;
    }

    public DateTime getStartPhase() {
        return startPhase;
    }
}

