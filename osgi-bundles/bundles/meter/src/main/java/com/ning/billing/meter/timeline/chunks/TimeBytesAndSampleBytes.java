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

package com.ning.billing.meter.timeline.chunks;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

/**
 * POJO containing a series of bytes and associated time points
 */
public class TimeBytesAndSampleBytes {

    @JsonProperty
    @JsonView(TimelineChunksViews.Compact.class)
    private final byte[] timeBytes;
    @JsonProperty
    @JsonView(TimelineChunksViews.Compact.class)
    private final byte[] sampleBytes;

    public TimeBytesAndSampleBytes(final byte[] timeBytes, final byte[] sampleBytes) {
        this.timeBytes = timeBytes;
        this.sampleBytes = sampleBytes;
    }

    public byte[] getTimeBytes() {
        return timeBytes;
    }

    public byte[] getSampleBytes() {
        return sampleBytes;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TimeBytesAndSampleBytes");
        sb.append("{timeBytes=").append(timeBytes == null ? "null" : "");
        for (int i = 0; timeBytes != null && i < timeBytes.length; ++i) {
            sb.append(i == 0 ? "" : ", ").append(timeBytes[i]);
        }
        sb.append(", sampleBytes=").append(sampleBytes == null ? "null" : "");
        for (int i = 0; sampleBytes != null && i < sampleBytes.length; ++i) {
            sb.append(i == 0 ? "" : ", ").append(sampleBytes[i]);
        }
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

        final TimeBytesAndSampleBytes that = (TimeBytesAndSampleBytes) o;

        if (!Arrays.equals(sampleBytes, that.sampleBytes)) {
            return false;
        }
        if (!Arrays.equals(timeBytes, that.timeBytes)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = timeBytes != null ? Arrays.hashCode(timeBytes) : 0;
        result = 31 * result + (sampleBytes != null ? Arrays.hashCode(sampleBytes) : 0);
        return result;
    }
}
