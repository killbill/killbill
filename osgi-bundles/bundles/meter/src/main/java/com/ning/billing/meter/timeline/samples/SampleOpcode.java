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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opcode for samples encoding
 */
public enum SampleOpcode {
    BYTE(1, 1),
    SHORT(2, 2),
    INT(3, 4),
    LONG(4, 8),
    FLOAT(5, 4),
    DOUBLE(6, 8),
    STRING(7, 0),
    NULL(8, 0, true),
    FLOAT_FOR_DOUBLE(10, 4, DOUBLE),
    HALF_FLOAT_FOR_DOUBLE(11, 2, DOUBLE),
    BYTE_FOR_DOUBLE(12, 1, DOUBLE),
    SHORT_FOR_DOUBLE(13, 2, DOUBLE),
    BIGINT(14, 0),
    DOUBLE_ZERO(15, 0, true),
    INT_ZERO(16, 0, true),
    REPEAT_BYTE(0xff, 1, true),   // A repeat operation in which the repeat count fits in an unsigned byte
    REPEAT_SHORT(0xfe, 2, true);  // A repeat operation in which the repeat count fits in an unsigned short

    private static final Logger log = LoggerFactory.getLogger(SampleOpcode.class);

    private int opcodeIndex;
    private final int byteSize;
    private final boolean repeater;
    private final boolean noArgs;
    private final SampleOpcode replacement;

    private SampleOpcode(int opcodeIndex, int byteSize) {
        this(opcodeIndex, byteSize, false);
    }

    private SampleOpcode(int opcodeIndex, int byteSize, boolean noArgs) {
        this(opcodeIndex, byteSize, noArgs, false);
    }

    private SampleOpcode(int opcodeIndex, int byteSize, boolean noArgs, boolean repeater) {
        this.opcodeIndex = opcodeIndex;
        this.byteSize = byteSize;
        this.noArgs = noArgs;
        this.repeater = repeater;
        this.replacement = this;
    }

    private SampleOpcode(int opcodeIndex, int byteSize, SampleOpcode replacement) {
        this(opcodeIndex, byteSize, false, false, replacement);
    }

    private SampleOpcode(final int opcodeIndex, final int byteSize, final boolean noArgs, final boolean repeater, final SampleOpcode replacement) {
        this.opcodeIndex = opcodeIndex;
        this.byteSize = byteSize;
        this.noArgs = noArgs;
        this.repeater = repeater;
        this.replacement = replacement;
    }

    public static SampleOpcode getOpcodeFromIndex(final int index) {
        for (SampleOpcode opcode : values()) {
            if (opcode.getOpcodeIndex() == index) {
                return opcode;
            }
        }

        final String s = String.format("In SampleOpcode.getOpcodefromIndex(), could not find opcode for index %d", index);
        log.error(s);
        throw new IllegalArgumentException(s);
    }

    public int getOpcodeIndex() {
        return opcodeIndex;
    }

    public int getByteSize() {
        return byteSize;
    }

    public boolean getNoArgs() {
        return noArgs;
    }

    public boolean getRepeater() {
        return repeater;
    }

    public SampleOpcode getReplacement() {
        return replacement;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("SampleOpcode");
        sb.append("{opcodeIndex=").append(opcodeIndex);
        sb.append(", byteSize=").append(byteSize);
        sb.append(", repeater=").append(repeater);
        sb.append(", noArgs=").append(noArgs);
        sb.append(", replacement=").append(replacement.name());
        sb.append('}');
        return sb.toString();
    }
}
