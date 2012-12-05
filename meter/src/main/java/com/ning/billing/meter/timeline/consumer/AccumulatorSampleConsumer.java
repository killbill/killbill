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

import java.util.LinkedHashMap;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.meter.api.TimeAggregationMode;
import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.meter.timeline.samples.ScalarSample;

public class AccumulatorSampleConsumer extends TimeRangeSampleProcessor {

    private final StringBuilder builder = new StringBuilder();
    // Linked HashMap to keep ordering of opcodes as they came
    private final Map<SampleOpcode, Double> accumulators = new LinkedHashMap<SampleOpcode, Double>();

    private final TimeAggregationMode timeAggregationMode;
    private final TimeRangeSampleProcessor sampleProcessor;

    private DateTime lastRoundedTime = null;
    private int aggregatedSampleNumber = 0;

    public AccumulatorSampleConsumer(final TimeAggregationMode timeAggregationMode, final TimeRangeSampleProcessor sampleProcessor) {
        super(null, null);
        this.timeAggregationMode = timeAggregationMode;
        this.sampleProcessor = sampleProcessor;
    }

    @Override
    public void processOneSample(final DateTime time, final SampleOpcode opcode, final Object value) {
        // Round the sample timestamp according to the aggregation mode
        final long millis = time.toDateTime(DateTimeZone.UTC).getMillis();
        final DateTime roundedTime;
        switch (timeAggregationMode) {
            case SECONDS:
                roundedTime = new DateTime((millis / 1000) * 1000L, DateTimeZone.UTC);
                break;
            case MINUTES:
                roundedTime = new DateTime((millis / (60 * 1000)) * 60 * 1000L, DateTimeZone.UTC);
                break;
            case HOURS:
                roundedTime = new DateTime((millis / (60 * 60 * 1000)) * 60 * 60 * 1000L, DateTimeZone.UTC);
                break;
            case DAYS:
                roundedTime = new DateTime((millis / (24 * 60 * 60 * 1000)) * 24 * 60 * 60 * 1000L, DateTimeZone.UTC);
                break;
            case MONTHS:
                roundedTime = new DateTime(time.getYear(), time.getMonthOfYear(), 1, 0, 0, 0, 0, DateTimeZone.UTC);
                break;
            case YEARS:
                roundedTime = new DateTime(time.getYear(), 1, 1, 0, 0, 0, 0, DateTimeZone.UTC);
                break;
            default:
                roundedTime = time;
                break;
        }

        // Get the sample value to aggregate
        // TODO Should we ignore conversion errors (e.g. Strings)?
        final double doubleValue = ScalarSample.getDoubleValue(opcode, value);

        // Output if it's not the first value and the current rounded time differ from the previous one
        if (lastRoundedTime != null && !lastRoundedTime.equals(roundedTime)) {
            outputAndResetAccumulators();
        }

        // Perform (or restart) the aggregation
        if (accumulators.get(opcode) == null) {
            accumulators.put(opcode, Double.valueOf("0"));
        }
        accumulators.put(opcode, accumulators.get(opcode) + doubleValue);

        lastRoundedTime = roundedTime;
    }

    private void outputAndResetAccumulators() {
        if (aggregatedSampleNumber != 0) {
            // TODO Assume CSV
            builder.append(",");
        }
        // Output one opcode at a time
        for (final SampleOpcode opcode : accumulators.keySet()) {
            aggregatedSampleNumber++;
            sampleProcessor.processOneSample(lastRoundedTime, opcode, accumulators.get(opcode));
        }
        // This will flush (clear) the sample consumer
        builder.append(sampleProcessor.toString());

        accumulators.clear();
    }

    @Override
    public synchronized String toString() {
        // Often empty
        final String value = builder.toString();
        // Allow for re-use
        builder.setLength(0);
        return value;
    }

    public String flush() {
        outputAndResetAccumulators();
        return toString();
    }
}
