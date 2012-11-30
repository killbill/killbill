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

package com.ning.billing.meter.timeline.shutdown;

public enum ShutdownSaveMode {
    SAVE_ALL_TIMELINES,    // Save all timelines in the db
    SAVE_START_TIMES;      // Save just the start times for each timeline, and use the replay facility to reconstruct the accumulators on startup

    public static ShutdownSaveMode fromString(final String mode) {
        for (final ShutdownSaveMode s : ShutdownSaveMode.values()) {
            if (s.name().equalsIgnoreCase(mode)) {
                return s;
            }
        }
        throw new IllegalArgumentException(String.format("The argument %s was supposed to be a ShutdownSaveMode, but was not", mode));
    }
}
