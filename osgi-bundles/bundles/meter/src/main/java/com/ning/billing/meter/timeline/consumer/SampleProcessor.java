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

import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.meter.timeline.times.TimelineCursor;

public interface SampleProcessor {

    /**
     * Process sampleCount sequential samples with identical values.  sampleCount will usually be 1,
     * but may be larger than 1.  Implementors may just loop processing identical values, but some
     * implementations may optimize adding a bunch of repeated values
     *
     * @param timeCursor  a TimeCursor object from which times can be found.
     * @param sampleCount the count of sequential, identical values
     * @param opcode      the opcode of the sample value, which may not be a REPEAT opcode
     * @param value       the value of this kind of sample over the count of samples
     */
    public void processSamples(final TimelineCursor timeCursor,
                               final int sampleCount,
                               final SampleOpcode opcode,
                               final Object value);
}
