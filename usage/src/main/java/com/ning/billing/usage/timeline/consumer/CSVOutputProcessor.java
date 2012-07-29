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

package com.ning.billing.usage.timeline.consumer;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.usage.timeline.codec.TimeRangeSampleProcessor;
import com.ning.billing.usage.timeline.samples.SampleOpcode;

public class CSVOutputProcessor extends TimeRangeSampleProcessor {

    private final SampleConsumer delegate = new CSVSampleConsumer();
    private int sampleNumber = 0;

    public CSVOutputProcessor(@Nullable final DateTime startTime, @Nullable final DateTime endTime) {
        super(startTime, endTime);
    }

    @Override
    public void processOneSample(final DateTime sampleTimestamp, final SampleOpcode opcode, final Object value) {
        delegate.consumeSample(sampleNumber, opcode, value, sampleTimestamp);
        sampleNumber++;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
