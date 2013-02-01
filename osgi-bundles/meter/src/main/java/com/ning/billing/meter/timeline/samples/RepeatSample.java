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

package com.ning.billing.meter.timeline.samples;

/**
 * A repeated value
 *
 * @param <T> A value consistent with the opcode
 */
public class RepeatSample<T> extends SampleBase {

    public static final int MAX_BYTE_REPEAT_COUNT = 0xFF; // The maximum byte value
    public static final int MAX_SHORT_REPEAT_COUNT = 0xFFFF; // The maximum short value

    private final ScalarSample<T> sampleRepeated;

    private int repeatCount;

    public RepeatSample(final int repeatCount, final ScalarSample<T> sampleRepeated) {
        super(SampleOpcode.REPEAT_BYTE);
        this.repeatCount = repeatCount;
        this.sampleRepeated = sampleRepeated;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    public void incrementRepeatCount() {
        repeatCount++;
    }

    public void incrementRepeatCount(final int addend) {
        repeatCount += addend;
    }

    public ScalarSample<T> getSampleRepeated() {
        return sampleRepeated;
    }

    @Override
    public SampleOpcode getOpcode() {
        return repeatCount > MAX_BYTE_REPEAT_COUNT ? SampleOpcode.REPEAT_SHORT : SampleOpcode.REPEAT_BYTE;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RepeatSample");
        sb.append("{sampleRepeated=").append(sampleRepeated);
        sb.append(", repeatCount=").append(repeatCount);
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

        final RepeatSample that = (RepeatSample) o;

        if (repeatCount != that.repeatCount) {
            return false;
        }
        if (sampleRepeated != null ? !sampleRepeated.equals(that.sampleRepeated) : that.sampleRepeated != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = sampleRepeated != null ? sampleRepeated.hashCode() : 0;
        result = 31 * result + repeatCount;
        return result;
    }
}
