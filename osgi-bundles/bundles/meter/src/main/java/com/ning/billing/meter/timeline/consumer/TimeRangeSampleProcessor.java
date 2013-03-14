/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.meter.timeline.consumer;

import org.joda.time.DateTime;

import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.meter.timeline.times.TimelineCursor;

public abstract class TimeRangeSampleProcessor implements SampleProcessor {

    private final DateTime startTime;  // Inclusive
    private final DateTime endTime;    // Inclusive

    protected TimeRangeSampleProcessor() {
        this(null, null);
    }

    public TimeRangeSampleProcessor(final DateTime startTime, final DateTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Process sampleCount sequential samples with identical values.  sampleCount will usually be 1,
     * but may be larger than 1.  Implementors may just loop processing identical values, but some
     * implementations may optimize adding a bunch of repeated values
     *
     * @param timeCursor  a TimeCursor instance, which supplies successive int UNIX times
     * @param sampleCount the count of sequential, identical values
     * @param opcode      the opcode of the sample value, which may not be a REPEAT opcode
     * @param value       the value of this kind of sample over the sampleCount samples
     */
    @Override
    public void processSamples(final TimelineCursor timeCursor, final int sampleCount, final SampleOpcode opcode, final Object value) {
        for (int i = 0; i < sampleCount; i++) {
            // Check if the sample is in the right time range
            final DateTime sampleTime = timeCursor.getNextTime();
            if ((startTime == null || !sampleTime.isBefore(startTime)) && ((endTime == null || !sampleTime.isAfter(endTime)))) {
                processOneSample(sampleTime, opcode, value);
            }
        }
    }

    public abstract void processOneSample(final DateTime time, final SampleOpcode opcode, final Object value);

    public DateTime getStartTime() {
        return startTime;
    }

    public DateTime getEndTime() {
        return endTime;
    }
}
