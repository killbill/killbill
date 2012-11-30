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

/**
 * Opcodes are 1-byte entities.  Any "opcode" whose value is 240 or less is treated
 * as a time delta to be added to the previous time value.
 */
public enum TimelineOpcode {
    FULL_TIME(0xFF),                 // Followed by 4 bytes of int value
    REPEATED_DELTA_TIME_BYTE(0xFE),  // Followed by a byte repeat count byte, 1-255, and then by a 1-byte delta whose value is 1-240
    REPEATED_DELTA_TIME_SHORT(0xFD); // Followed by a repeat count short, 1-65535, and then by a 1-byte delta whose value is 1-240

    public static final int MAX_DELTA_TIME = 0xF0;      // 240: Leaves room for 16 other opcodes, of which 3 are used

    private final int opcodeIndex;

    private TimelineOpcode(final int opcodeIndex) {
        this.opcodeIndex = opcodeIndex;
    }

    public int getOpcodeIndex() {
        return opcodeIndex;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TimelineOpcode");
        sb.append("{opcodeIndex=").append(opcodeIndex);
        sb.append('}');
        return sb.toString();
    }
}
