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

package com.ning.billing.meter.timeline.times;

import java.util.List;

import org.joda.time.DateTime;

public interface TimelineCoder {

    /**
     * Compress the list of DateTimes, producing the bytes of a timeline
     *
     * @param dateTimes a list of DateTimes to compress
     * @return the bytes of the resulting timeline
     */
    public byte[] compressDateTimes(final List<DateTime> dateTimes);

    /**
     * Decompress the timeline bytes argument, returning a list of DateTimes
     * Currently only used by tests.
     *
     * @param compressedTimes the timeline bytes
     * @return a list of DateTimes representing the timeline times
     */
    public List<DateTime> decompressDateTimes(final byte[] compressedTimes);

    /**
     * Recode and combine the list of timeline byte objects, returning a single timeline.
     * If the sampleCount is non-null and is not equal to the sum of the sample counts
     * of the list of timelines, throw an error
     *
     * @param timesList   a list of timeline byte arrays
     * @param sampleCount the expected count of samples for all timeline byte arrays
     * @return the combined timeline
     */
    public byte[] combineTimelines(final List<byte[]> timesList, final Integer sampleCount);

    /**
     * Return a count of the time samples in the timeline provided
     *
     * @param timeBytes the bytes of a timeline
     * @return the count of samples represented in the timeline
     */
    public int countTimeBytesSamples(final byte[] timeBytes);
}
