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

package org.killbill.billing.subscription.events.phase;


import org.killbill.billing.subscription.events.EventBaseBuilder;

public class PhaseEventBuilder extends EventBaseBuilder<PhaseEventBuilder> {

    private String phaseName;

    public PhaseEventBuilder() {
        super();
    }

    public PhaseEventBuilder(final PhaseEvent phaseEvent) {
        super(phaseEvent);
        this.phaseName = phaseEvent.getPhase();
    }

    public PhaseEventBuilder(final EventBaseBuilder<?> base) {
        super(base);
    }

    public PhaseEventBuilder setPhaseName(final String phaseName) {
        this.phaseName = phaseName;
        return this;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public PhaseEvent build() {
        return new PhaseEventData(this);
    }
}
