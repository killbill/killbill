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

package com.ning.billing.meter.timeline.codec;

import java.util.Arrays;

public class EncodedBytesAndSampleCount {

    private final byte[] encodedBytes;
    private final int sampleCount;

    public EncodedBytesAndSampleCount(final byte[] encodedBytes, final int sampleCount) {
        this.encodedBytes = encodedBytes;
        this.sampleCount = sampleCount;
    }

    public byte[] getEncodedBytes() {
        return encodedBytes;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("EncodedBytesAndSampleCount");
        sb.append("{encodedBytes=").append(encodedBytes == null ? "null" : "");
        for (int i = 0; encodedBytes != null && i < encodedBytes.length; ++i) {
            sb.append(i == 0 ? "" : ", ").append(encodedBytes[i]);
        }
        sb.append(", sampleCount=").append(sampleCount);
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

        final EncodedBytesAndSampleCount that = (EncodedBytesAndSampleCount) o;

        if (sampleCount != that.sampleCount) {
            return false;
        }
        if (!Arrays.equals(encodedBytes, that.encodedBytes)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = encodedBytes != null ? Arrays.hashCode(encodedBytes) : 0;
        result = 31 * result + sampleCount;
        return result;
    }
}
