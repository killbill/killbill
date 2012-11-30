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

import org.joda.time.DateTime;

public interface TimelineCursor {

    /**
     * Skip to the given sample number within the timeline, where 0 is the first sample number
     *
     * @param finalSampleNumber the sample number to skip to
     */
    public void skipToSampleNumber(final int finalSampleNumber);

    /**
     * Return the DateTime for the next sample
     *
     * @return the DateTime for the next sample.  If we've run out of samples, return null
     */
    public DateTime getNextTime();
}
